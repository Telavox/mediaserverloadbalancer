package se.telavox.mediaserver_loadbalancer.server;

import java.io.File;

import net.sf.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.telavox.base.Base;
import se.telavox.base.BaseConfig;
import se.telavox.mediaserver_loadbalancer.server.api.LoadbalancerJaxRS;
import se.telavox.mediaserver_loadbalancer.server.balancer.BalancerStrategy;
import se.telavox.mediaserver_loadbalancer.server.balancer.WeightedScoreStrategy;
import se.telavox.mediaserver_loadbalancer.server.config.PoolConfig;
import se.telavox.mediaserver_loadbalancer.server.polling.MediaServerPoller;

/**
 * Main entry point for the Mediaserver Loadbalancer service.
 * <p>
 * Extends {@link Base} to leverage the standard Telavox service infrastructure
 * (HTTP server, heartbeat, grapher, configuration). On startup it:
 * <ol>
 *   <li>Loads pool configuration from a JSON file</li>
 *   <li>Starts the mediaserver poller to periodically collect load reports</li>
 *   <li>Registers the HTTP API for SIP-server queries</li>
 * </ol>
 */
public class MediaServerLoadbalancer extends Base {

    private static final Logger log = LoggerFactory.getLogger(MediaServerLoadbalancer.class);

    private static final String APP_NAME = "mediaserver_loadbalancer";
    private static final int HTTP_PORT = 8102;

    private MediaServerPoller poller;
    private LoadbalancerJaxRS jaxrs;

    public static void main(String[] args) throws Exception {
        new MediaServerLoadbalancer(args);
    }

    public MediaServerLoadbalancer(String[] args) throws Exception {
        BaseConfig config = new BaseConfig();
        config.setSourceRepositoryName(APP_NAME);

        if (config.isDevelopment()) {
            config.setLogToSyslog(false);
            config.setBasemanAddress(null);
        } else {
            config.setLogToSyslog(true);
            config.setBasemanAddress("baseman.service.telavox.se:8087");
        }

        super.init(APP_NAME, HTTP_PORT, args, config);
        log.info("Initializing {}", APP_NAME);

        // Load pool configuration
        File poolConfigFile = resolvePoolConfigFile();
        PoolConfig poolConfig = PoolConfig.load(poolConfigFile);

        // Create the balancer strategy
        BalancerStrategy strategy = new WeightedScoreStrategy();

        // Start the poller
        poller = new MediaServerPoller(poolConfig);
        poller.start();

        // Register the HTTP API
        jaxrs = new LoadbalancerJaxRS(this, poller, strategy);
        jaxrs.load();

        log.info("{} started on port {}", APP_NAME, HTTP_PORT);
    }

    /**
     * Resolves the pool configuration file.
     * <p>
     * Searches for {@code pools.json} in the following locations (in order):
     * <ol>
     *   <li>Path specified by system property {@code pools.config}</li>
     *   <li>{@code pools.json} in the current working directory</li>
     *   <li>{@code /etc/mediaserver_loadbalancer/pools.json}</li>
     * </ol>
     *
     * @return the configuration file
     * @throws IllegalStateException if no configuration file is found
     */
    private File resolvePoolConfigFile() {
        String configPath = System.getProperty("pools.config");
        if (configPath != null) {
            File f = new File(configPath);
            if (f.isFile()) {
                log.info("Using pool config from system property: {}", f.getAbsolutePath());
                return f;
            }
            log.warn("System property pools.config points to non-existent file: {}", configPath);
        }

        File local = new File("pools.json");
        if (local.isFile()) {
            log.info("Using pool config from working directory: {}", local.getAbsolutePath());
            return local;
        }

        File etc = new File("/etc/mediaserver_loadbalancer/pools.json");
        if (etc.isFile()) {
            log.info("Using pool config from /etc: {}", etc.getAbsolutePath());
            return etc;
        }

        throw new IllegalStateException(
                "No pool configuration file found. Provide pools.json in the working directory, "
                + "at /etc/mediaserver_loadbalancer/pools.json, or set -Dpools.config=<path>");
    }

    @Override
    public boolean isAppOK() {
        return poller != null;
    }

    @Override
    public JSONObject getAppSpecificJSONHeartBeat() {
        JSONObject o = new JSONObject();
        if (poller != null) {
            o.put("trackedServers", poller.getAllServerStates().size());
            o.put("pools", poller.getPoolMapping().size());
        }
        return o;
    }

    @Override
    public void askedToStop() {
        log.info("Stopping {}", APP_NAME);
        if (poller != null) {
            poller.stop();
        }
    }
}
