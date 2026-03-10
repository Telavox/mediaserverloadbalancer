package se.telavox.mediaserver_loadbalancer.server.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.telavox.mediaserver_loadbalancer.server.balancer.BalancerStrategy;
import se.telavox.mediaserver_loadbalancer.server.polling.MediaServerPoller;
import se.telavox.mediaserver_loadbalancer.server.polling.MediaServerState;
import se.telavox.mediaserver_loadbalancer.shared.LoadReport;

/**
 * JAX-RS resource exposing the loadbalancer HTTP API.
 * <p>
 * Provides endpoints for SIP-servers to query for the best available
 * mediaserver in a given pool, and a status endpoint for monitoring.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class LoadbalancerApi {

    private static final Logger log = LoggerFactory.getLogger(LoadbalancerApi.class);

    private final MediaServerPoller poller;
    private final BalancerStrategy strategy;

    public LoadbalancerApi(MediaServerPoller poller, BalancerStrategy strategy) {
        this.poller = poller;
        this.strategy = strategy;
    }

    /**
     * Selects the best available mediaserver from the given pool.
     * <p>
     * Returns a JSON object with the selected server's host and port.
     * Returns 400 if pool parameter is missing, 404 if the pool is unknown,
     * and 503 if no healthy servers are available in the pool.
     *
     * @param pool the pool name to select from
     * @return JSON response with the selected server
     */
    @GET
    @Path("select")
    public Response selectServer(@QueryParam("pool") String pool) {
        if (pool == null || pool.isEmpty()) {
            log.warn("Select request with missing pool parameter");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(errorJson("Missing required query parameter: pool"))
                    .build();
        }

        Collection<MediaServerState> candidates = poller.getServerStates(pool);
        if (candidates.isEmpty()) {
            log.warn("Select request for unknown pool '{}'", pool);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(errorJson("Unknown pool: " + pool))
                    .build();
        }

        MediaServerState selected = strategy.select(candidates);
        if (selected == null) {
            log.warn("No healthy servers available in pool '{}'", pool);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(errorJson("No healthy servers available in pool: " + pool))
                    .build();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("host", selected.getServerEntry().getHost());
        result.put("port", selected.getServerEntry().getRpcPort());
        result.put("pool", pool);

        return Response.ok(result).build();
    }

    /**
     * Returns a status overview of all pools and their servers.
     * <p>
     * Useful for monitoring and debugging the loadbalancer state.
     */
    @GET
    @Path("status")
    public Response getStatus() {
        Map<String, Object> status = new HashMap<>();
        Map<String, List<String>> poolMapping = poller.getPoolMapping();

        Map<String, Object> poolsInfo = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : poolMapping.entrySet()) {
            String poolName = entry.getKey();
            Collection<MediaServerState> states = poller.getServerStates(poolName);
            List<Map<String, Object>> serverInfos = new ArrayList<>();

            for (MediaServerState state : states) {
                Map<String, Object> serverInfo = new HashMap<>();
                serverInfo.put("host", state.getServerEntry().getHost());
                serverInfo.put("port", state.getServerEntry().getRpcPort());
                serverInfo.put("reachable", state.isReachable());
                serverInfo.put("healthy", state.isHealthy());
                serverInfo.put("consecutiveFailures", state.getConsecutiveFailures());
                serverInfo.put("lastPollTimeMillis", state.getLastPollTimeMillis());

                LoadReport report = state.getLastReport();
                if (report != null) {
                    Map<String, Object> reportInfo = new HashMap<>();
                    reportInfo.put("cpuUsage", report.getCpuUsage());
                    reportInfo.put("memoryUsage", report.getMemoryUsage());
                    reportInfo.put("rtpStreamCount", report.getRtpStreamCount());
                    reportInfo.put("pauseState", report.getPauseState());
                    reportInfo.put("timestamp", report.getTimestamp());
                    serverInfo.put("lastReport", reportInfo);
                }

                serverInfos.add(serverInfo);
            }

            poolsInfo.put(poolName, serverInfos);
        }

        status.put("pools", poolsInfo);
        return Response.ok(status).build();
    }

    private static Map<String, String> errorJson(String message) {
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        return error;
    }
}
