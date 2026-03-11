package se.telavox.mediaserver_loadbalancer.shared;

/**
 * Represents the operational state of a mediaserver instance.
 * <p>
 * STARTING - The server is booting up and not yet ready to accept calls.
 * ENABLED  - The server is fully operational and accepts new calls.
 * PAUSED   - The server is draining; existing calls continue but no new calls are assigned.
 * STOPPED  - The server is fully excluded from load balancing.
 */
public enum PauseState {
    STARTING,
    ENABLED,
    PAUSED,
    STOPPED
}
