package se.telavox.mediaserverloadbalancer.server;

import java.io.File;

import net.sf.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.telavox.base.Base;
import se.telavox.base.BaseConfig;
import se.telavox.configurator.client.ConfiguratorClient;
import se.telavox.configurator.client.InnerConfigProperty;
import se.telavox.configurator.client.StringProperty;
import se.telavox.mediaserverloadbalancer.server.api.LoadbalancerJaxRS;
import se.telavox.mediaserverloadbalancer.server.balancer.BalancerStrategy;
import se.telavox.mediaserverloadbalancer.server.balancer.ThresholdStrategy;
import se.telavox.mediaserverloadbalancer.server.balancer.WeightedScoreStrategy;
import se.telavox.mediaserverloadbalancer.server.config.PoolConfig;
import se.telavox.mediaserverloadbalancer.server.polling.MediaServerPoller;

/**
 * Main entry point for the Mediaserver Loadbalancer service.
 * <p>
 * Extends {@link Base} to leverage the standard Telavox service infrastructure
 * (HTTP server, heartbeat, grapher, configuration). On startup it:
 * <ol>
 *   <li>Loads pool configuration from ConfiguratorClient, or from a local
 *       {@code pools.json} file if present (file takes precedence)</li>
 *   <li>Starts the mediaserver poller to periodically collect load reports</li>
 *   <li>Registers the HTTP API for SIP-server queries</li>
 * </ol>
 */
public class MediaServerLoadbalancer extends Base {

    private static final Logger log = LoggerFactory.getLogger(MediaServerLoadbalancer.class);

    private static final String APP_NAME = "mediaserverloadbalancer";
    private static final int HTTP_PORT = 8102;
    private static final String POOLS_PROPERTY = "mediaserverloadbalancer.pools";

    private MediaServerPoller poller;
    private LoadbalancerJaxRS jaxrs;

    private StringProperty strategyProperty;
    private boolean usingFileConfig;

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

        // Register configurator properties for live updates
        ConfiguratorClient configClient = getConfigClient();
        strategyProperty = configClient.stringProperty(
                "mediaserverloadbalancer.strategy", "ThresholdStrategy");

        // Load pool configuration: file override takes precedence over configurator
        PoolConfig poolConfig = loadPoolConfig(configClient);

        // Create the balancer strategy (reads properties dynamically on each select)
        BalancerStrategy strategy = createStrategy(configClient);

        // Start the poller
        poller = new MediaServerPoller(poolConfig);
        poller.start();

        // If using configurator (no file override), listen for live config updates
        if (!usingFileConfig) {
            registerConfiguratorListener(configClient);
        }

        // Register the HTTP API
        jaxrs = new LoadbalancerJaxRS(this, poller, strategy);
        jaxrs.load();

        log.info("{} started on port {}", APP_NAME, HTTP_PORT);
    }

    /**
     * Loads pool configuration, preferring a local file if present,
     * otherwise falling back to the ConfiguratorClient property
     * {@value #POOLS_PROPERTY}.
     */
    private PoolConfig loadPoolConfig(ConfiguratorClient configClient) throws Exception {
        File poolConfigFile = resolvePoolConfigFile();
        if (poolConfigFile != null) {
            usingFileConfig = true;
            log.info("Using pools.json file override — configurator property '{}' will be ignored", POOLS_PROPERTY);
            return PoolConfig.load(poolConfigFile);
        }

        usingFileConfig = false;
        log.info("No pools.json file found — reading pool configuration from configurator property '{}'", POOLS_PROPERTY);
        InnerConfigProperty poolsProperty = configClient.innerConfigProperty(POOLS_PROPERTY);
        return PoolConfig.fromConfig(poolsProperty.get());
    }

    /**
     * Registers a listener on the configurator property so that pool
     * configuration changes are picked up at runtime without a restart.
     */
    private void registerConfiguratorListener(ConfiguratorClient configClient) {
        InnerConfigProperty poolsProperty = configClient.innerConfigProperty(POOLS_PROPERTY);
        poolsProperty.addUpdateListener((oldConfig, newConfig) -> {
            log.info("Configurator property '{}' updated — reloading pool configuration", POOLS_PROPERTY);
            PoolConfig updated = PoolConfig.fromConfig(newConfig);
            poller.updateConfig(updated);
        });
    }

    /**
     * Creates a {@link BalancerStrategy} that delegates to the appropriate implementation
     * based on the live configurator property {@code mediaserverloadbalancer.strategy}.
     * <p>
     * The returned strategy reads property values on each {@code select()} call,
     * so configuration changes take effect without restarting.
     */
    private BalancerStrategy createStrategy(ConfiguratorClient configClient) {
        WeightedScoreStrategy weightedScore = new WeightedScoreStrategy();
        ThresholdStrategy threshold = new ThresholdStrategy(configClient);

        return candidates -> {
            String strategyName = strategyProperty.get();

            if ("WeightedScoreStrategy".equals(strategyName)) {
                return weightedScore.select(candidates);
            }

            if (!"ThresholdStrategy".equals(strategyName)) {
                log.warn("Unknown strategy '{}', falling back to ThresholdStrategy", strategyName);
            }

            return threshold.select(candidates);
        };
    }

    /**
     * Searches for a {@code pools.json} file in well-known locations.
     * <p>
     * Returns {@code null} if no file is found (caller should fall back
     * to ConfiguratorClient).
     * <p>
     * Search order:
     * <ol>
     *   <li>Path specified by system property {@code pools.config}</li>
     *   <li>{@code pools.json} in the current working directory</li>
     *   <li>{@code /etc/mediaserverloadbalancer/pools.json}</li>
     * </ol>
     *
     * @return the configuration file, or {@code null} if not found
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

        File etc = new File("/etc/mediaserverloadbalancer/pools.json");
        if (etc.isFile()) {
            log.info("Using pool config from /etc: {}", etc.getAbsolutePath());
            return etc;
        }

        return null;
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
