package se.telavox.mediaserverloadbalancer.client;

/**
 * Thrown when the load balancer has no healthy servers available in the requested pool
 * (HTTP 503 response).
 */
public class NoHealthyServerException extends MediaServerLoadBalancerException {

    private final String pool;

    public NoHealthyServerException(String message, String pool) {
        super(message);
        this.pool = pool;
    }

    /**
     * Returns the pool name that had no healthy servers.
     */
    public String getPool() {
        return pool;
    }
}
