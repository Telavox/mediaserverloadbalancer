package se.telavox.mediaserverloadbalancer.client;

/**
 * Thrown when the MediaServer Load Balancer client cannot complete a request.
 * <p>
 * This covers network errors, non-2xx HTTP responses, and JSON parse failures.
 */
public class MediaServerLoadBalancerException extends Exception {

    public MediaServerLoadBalancerException(String message) {
        super(message);
    }

    public MediaServerLoadBalancerException(String message, Throwable cause) {
        super(message, cause);
    }
}
