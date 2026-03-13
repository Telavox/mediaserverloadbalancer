package se.telavox.mediaserverloadbalancer.server.balancer;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.telavox.configurator.client.ConfiguratorClient;
import se.telavox.configurator.client.DoubleProperty;
import se.telavox.mediaserverloadbalancer.server.polling.MediaServerState;
import se.telavox.mediaserverloadbalancer.shared.LoadReport;
import se.telavox.mediaserverloadbalancer.shared.PauseState;

/**
 * Selects the mediaserver with the fewest active RTP streams, after filtering
 * out servers that exceed CPU or memory thresholds or are not in the
 * {@link PauseState#ENABLED} state.
 */
public class ThresholdStrategy implements BalancerStrategy {

    private static final Logger log = LoggerFactory.getLogger(ThresholdStrategy.class);

    private final DoubleProperty maxCpuUsage;
    private final DoubleProperty maxMemoryUsage;

    /**
     * Creates a strategy with live-updating thresholds from the configurator.
     *
     * @param configClient the configurator client for reading threshold properties
     */
    public ThresholdStrategy(ConfiguratorClient configClient) {
        this.maxCpuUsage = configClient.doubleProperty(
                "mediaserverloadbalancer.strategy.thresholdstrategy.threshold.cpu", 0.7);
        this.maxMemoryUsage = configClient.doubleProperty(
                "mediaserverloadbalancer.strategy.thresholdstrategy.threshold.memory", 0.7);
    }

    @Override
    public MediaServerState select(Collection<MediaServerState> candidates) {
        double cpuThreshold = maxCpuUsage.get();
        double memThreshold = maxMemoryUsage.get();

        MediaServerState best = null;
        int lowestStreamCount = Integer.MAX_VALUE;

        for (MediaServerState state : candidates) {
            if (!state.isHealthy()) {
                continue;
            }

            LoadReport report = state.getLastReport();

            if (report.getPauseState() != PauseState.ENABLED) {
                if (log.isDebugEnabled()) {
                    log.debug("Excluding {} from selection: pauseState={}",
                            state.getServerEntry(), report.getPauseState());
                }
                continue;
            }

            if (report.getCpuUsage() > cpuThreshold) {
                if (log.isDebugEnabled()) {
                    log.debug("Excluding {} from selection: cpuUsage={} exceeds threshold {}",
                            state.getServerEntry(), report.getCpuUsage(), cpuThreshold);
                }
                continue;
            }

            if (report.getMemoryUsage() > memThreshold) {
                if (log.isDebugEnabled()) {
                    log.debug("Excluding {} from selection: memoryUsage={} exceeds threshold {}",
                            state.getServerEntry(), report.getMemoryUsage(), memThreshold);
                }
                continue;
            }

            int streamCount = report.getRtpStreamCount();

            if (log.isDebugEnabled()) {
                log.debug("Candidate {}: streams={} (cpu={}, mem={})",
                        state.getServerEntry(), streamCount,
                        report.getCpuUsage(), report.getMemoryUsage());
            }

            if (streamCount < lowestStreamCount) {
                lowestStreamCount = streamCount;
                best = state;
            }
        }

        if (best != null) {
            log.info("Selected {} with {} streams", best.getServerEntry(), lowestStreamCount);
        }

        return best;
    }
}