package se.telavox.mediaserverloadbalancer.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response from the {@code GET /api/select} endpoint.
 * <p>
 * Contains the host and RPC port of the selected mediaserver,
 * along with the pool name the selection was made from.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SelectResponse {

    private String host;
    private int port;
    private String pool;

    public SelectResponse() {
    }

    public SelectResponse(String host, int port, String pool) {
        this.host = host;
        this.port = port;
        this.pool = pool;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPool() {
        return pool;
    }

    public void setPool(String pool) {
        this.pool = pool;
    }

    /**
     * Returns the mediaserver URL in the format {@code http://{host}:{port}}.
     */
    public String toMediaServerUrl() {
        return "http://" + host + ":" + port;
    }

    @Override
    public String toString() {
        return "SelectResponse{host='" + host + "', port=" + port + ", pool='" + pool + "'}";
    }
}
