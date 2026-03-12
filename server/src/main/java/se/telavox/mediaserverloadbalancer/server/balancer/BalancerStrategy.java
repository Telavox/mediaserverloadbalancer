package se.telavox.mediaserverloadbalancer.server.balancer;

import java.util.Collection;

import se.telavox.mediaserverloadbalancer.server.polling.MediaServerState;

/**
 * Strategy interface for selecting the best mediaserver from a set of
 * candidates within a pool.
 */
public interface BalancerStrategy {

    /**
     * Selects the optimal server from the given candidates.
     *
     * @param candidates the available server states in the pool
     * @return the selected server, or {@code null} if no suitable server is available
     */
    MediaServerState select(Collection<MediaServerState> candidates);
}
