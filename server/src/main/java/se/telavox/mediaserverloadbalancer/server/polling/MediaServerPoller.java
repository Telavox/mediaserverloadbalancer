package se.telavox.mediaserverloadbalancer.server.polling;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.googlecode.jsonrpc4j.JsonRpcHttpClient;
import com.googlecode.jsonrpc4j.ProxyUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.telavox.mediaserverloadbalancer.server.config.PoolConfig;
import se.telavox.mediaserverloadbalancer.shared.LoadReport;
import se.telavox.mediaserverloadbalancer.shared.LoadReportService;

/**
 * Periodically polls all configured mediaservers for their load reports
 * via JSON-RPC.
 * <p>
 * Maintains a {@link MediaServerState} for each server entry. The poller
 * runs on a fixed-rate schedule; each cycle polls every configured server
 * and updates its state with the result.
 */
public class MediaServerPoller {

    private static final Logger log = LoggerFactory.getLogger(MediaServerPoller.class);

    /** RPC service name under which the load report service is registered on the mediaserver. */
    private static final String RPC_SERVICE_NAME = "loadreport";

    private final PoolConfig poolConfig;
    private final ScheduledExecutorService scheduler;

    /**
     * Server state keyed by {@link PoolConfig.ServerEntry#getId()}.
     */
    private final Map<String, MediaServerState> serverStates = new ConcurrentHashMap<>();

    /**
     * Pool name to list of server IDs, for fast lookup.
     */
    private final Map<String, List<String>> poolToServerIds = new ConcurrentHashMap<>();

    public MediaServerPoller(PoolConfig poolConfig) {
        this.poolConfig = poolConfig;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "ms-poller");
            t.setDaemon(true);
            return t;
        });
        initializeStates();
    }

    private void initializeStates() {
        for (Map.Entry<String, PoolConfig.Pool> entry : poolConfig.getPools().entrySet()) {
            String poolName = entry.getKey();
            PoolConfig.Pool pool = entry.getValue();
            List<String> serverIds = new ArrayList<>();

            for (PoolConfig.ServerEntry server : pool.getServers()) {
                String id = server.getId();
                serverIds.add(id);
                // A server may appear in multiple pools; only create state once
                serverStates.computeIfAbsent(id, k -> new MediaServerState(server));
            }

            poolToServerIds.put(poolName, Collections.unmodifiableList(serverIds));
        }

        log.info("Initialized state tracking for {} unique server(s) across {} pool(s)",
                serverStates.size(), poolToServerIds.size());
    }

    /**
     * Starts the periodic polling.
     */
    public void start() {
        int intervalSeconds = poolConfig.getPollingIntervalSeconds();
        log.info("Starting mediaserver poller with {}s interval", intervalSeconds);
        scheduler.scheduleAtFixedRate(this::pollAll, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * Stops the poller and releases resources.
     */
    public void stop() {
        log.info("Stopping mediaserver poller");
        scheduler.shutdownNow();
        try {
            scheduler.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for poller shutdown", e);
            Thread.currentThread().interrupt();
        }
    }

    private void pollAll() {
        for (MediaServerState state : serverStates.values()) {
            try {
                pollServer(state);
            } catch (Throwable t) {
                log.error("Unexpected error polling {}: {}", state.getServerEntry(), t.getMessage(), t);
                state.updateFailure();
            }
        }
    }

    private void pollServer(MediaServerState state) {
        PoolConfig.ServerEntry server = state.getServerEntry();
        try {
            String rpcUrl = "http://" + server.getHost() + ":" + server.getRpcPort()
                    + "/rpc/" + RPC_SERVICE_NAME;
            JsonRpcHttpClient client = new JsonRpcHttpClient(new URL(rpcUrl));
            LoadReportService service = ProxyUtil.createClientProxy(
                    getClass().getClassLoader(), LoadReportService.class, client);

            LoadReport report = service.getLoadReport();
            state.updateSuccess(report);

            if (log.isDebugEnabled()) {
                log.debug("Poll {} -> {}", server, report);
            }
        } catch (Throwable t) {
            state.updateFailure();
            if (state.getConsecutiveFailures() <= 3 || state.getConsecutiveFailures() % 10 == 0) {
                log.warn("Failed to poll {} (failure #{}): {}",
                        server, state.getConsecutiveFailures(), t.getMessage());
            }
        }
    }

    /**
     * Replaces the current pool configuration with new settings.
     * <p>
     * Adds states for any new servers, removes states for servers that no
     * longer appear in any pool, and updates the pool-to-server mappings.
     *
     * @param newConfig the new pool configuration
     */
    public void updateConfig(PoolConfig newConfig) {
        Map<String, List<String>> newPoolToServerIds = new ConcurrentHashMap<>();
        Map<String, PoolConfig.ServerEntry> newServerEntries = new ConcurrentHashMap<>();

        for (Map.Entry<String, PoolConfig.Pool> entry : newConfig.getPools().entrySet()) {
            String poolName = entry.getKey();
            PoolConfig.Pool pool = entry.getValue();
            List<String> serverIds = new ArrayList<>();

            for (PoolConfig.ServerEntry server : pool.getServers()) {
                String id = server.getId();
                serverIds.add(id);
                newServerEntries.put(id, server);
                serverStates.computeIfAbsent(id, k -> new MediaServerState(server));
            }

            newPoolToServerIds.put(poolName, Collections.unmodifiableList(serverIds));
        }

        // Remove servers that are no longer in any pool
        serverStates.keySet().removeIf(id -> !newServerEntries.containsKey(id));

        // Replace pool mappings atomically
        poolToServerIds.clear();
        poolToServerIds.putAll(newPoolToServerIds);

        log.info("Updated pool configuration: {} unique server(s) across {} pool(s)",
                serverStates.size(), poolToServerIds.size());
    }

    /**
     * Returns the current states for all servers in the given pool.
     *
     * @param poolName the pool name
     * @return an unmodifiable collection of server states, or empty if the pool is unknown
     */
    public Collection<MediaServerState> getServerStates(String poolName) {
        List<String> ids = poolToServerIds.get(poolName);
        if (ids == null) {
            return Collections.emptyList();
        }
        List<MediaServerState> states = new ArrayList<>(ids.size());
        for (String id : ids) {
            MediaServerState state = serverStates.get(id);
            if (state != null) {
                states.add(state);
            }
        }
        return Collections.unmodifiableList(states);
    }

    /**
     * Returns all known server states across all pools.
     */
    public Map<String, MediaServerState> getAllServerStates() {
        return Collections.unmodifiableMap(serverStates);
    }

    /**
     * Returns the pool name to server ID mapping.
     */
    public Map<String, List<String>> getPoolMapping() {
        return Collections.unmodifiableMap(poolToServerIds);
    }
}
