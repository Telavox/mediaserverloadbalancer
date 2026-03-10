package se.telavox.mediaserver_loadbalancer.server.polling;

import se.telavox.mediaserver_loadbalancer.shared.LoadReport;
import se.telavox.mediaserver_loadbalancer.server.config.PoolConfig;

/**
 * Holds the latest known state of a single mediaserver instance.
 * <p>
 * Updated by the {@link MediaServerPoller} on each polling cycle. If a
 * poll fails, the server is marked as unreachable while retaining the
 * last successful report for diagnostic purposes.
 */
public class MediaServerState {

    private final PoolConfig.ServerEntry serverEntry;
    private volatile LoadReport lastReport;
    private volatile boolean reachable;
    private volatile long lastPollTimeMillis;
    private volatile int consecutiveFailures;

    public MediaServerState(PoolConfig.ServerEntry serverEntry) {
        this.serverEntry = serverEntry;
        this.reachable = false;
        this.consecutiveFailures = 0;
    }

    /**
     * Records a successful poll result.
     */
    public void updateSuccess(LoadReport report) {
        this.lastReport = report;
        this.reachable = true;
        this.lastPollTimeMillis = System.currentTimeMillis();
        this.consecutiveFailures = 0;
    }

    /**
     * Records a failed poll attempt.
     */
    public void updateFailure() {
        this.reachable = false;
        this.lastPollTimeMillis = System.currentTimeMillis();
        this.consecutiveFailures++;
    }

    public PoolConfig.ServerEntry getServerEntry() {
        return serverEntry;
    }

    public LoadReport getLastReport() {
        return lastReport;
    }

    public boolean isReachable() {
        return reachable;
    }

    public long getLastPollTimeMillis() {
        return lastPollTimeMillis;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    /**
     * Returns {@code true} if this server is both reachable and has
     * provided at least one load report.
     */
    public boolean isHealthy() {
        return reachable && lastReport != null;
    }
}
