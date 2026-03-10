package se.telavox.mediaserver_loadbalancer.server.config;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the static pool configuration loaded from a JSON file.
 * <p>
 * Each pool has a name and a list of mediaserver entries identified by
 * host and RPC port.
 *
 * <p>Example configuration file ({@code pools.json}):
 * <pre>
 * {
 *   "pollingIntervalSeconds": 10,
 *   "pools": {
 *     "default": {
 *       "servers": [
 *         {"host": "ms1.example.com", "rpcPort": 9092},
 *         {"host": "ms2.example.com", "rpcPort": 9092}
 *       ]
 *     },
 *     "conference": {
 *       "servers": [
 *         {"host": "ms3.example.com", "rpcPort": 9092}
 *       ]
 *     }
 *   }
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PoolConfig {

    private static final Logger log = LoggerFactory.getLogger(PoolConfig.class);

    private int pollingIntervalSeconds = 10;
    private Map<String, Pool> pools = Collections.emptyMap();

    public int getPollingIntervalSeconds() {
        return pollingIntervalSeconds;
    }

    public void setPollingIntervalSeconds(int pollingIntervalSeconds) {
        this.pollingIntervalSeconds = pollingIntervalSeconds;
    }

    public Map<String, Pool> getPools() {
        return pools;
    }

    public void setPools(Map<String, Pool> pools) {
        this.pools = pools;
    }

    /**
     * Loads the pool configuration from a JSON file.
     *
     * @param file the configuration file
     * @return the parsed configuration
     * @throws IOException if the file cannot be read or parsed
     */
    public static PoolConfig load(File file) throws IOException {
        log.info("Loading pool configuration from {}", file.getAbsolutePath());
        ObjectMapper mapper = new ObjectMapper();
        PoolConfig config = mapper.readValue(file, PoolConfig.class);
        log.info("Loaded {} pool(s): {}", config.pools.size(), config.pools.keySet());
        return config;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pool {
        private List<ServerEntry> servers = Collections.emptyList();

        public List<ServerEntry> getServers() {
            return servers;
        }

        public void setServers(List<ServerEntry> servers) {
            this.servers = servers;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServerEntry {
        private String host;
        private int rpcPort = 9092;

        public ServerEntry() {
        }

        public ServerEntry(String host, int rpcPort) {
            this.host = host;
            this.rpcPort = rpcPort;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getRpcPort() {
            return rpcPort;
        }

        public void setRpcPort(int rpcPort) {
            this.rpcPort = rpcPort;
        }

        /**
         * Returns a unique identifier for this server entry,
         * used as a key in state maps.
         */
        public String getId() {
            return host + ":" + rpcPort;
        }

        @Override
        public String toString() {
            return getId();
        }
    }
}
