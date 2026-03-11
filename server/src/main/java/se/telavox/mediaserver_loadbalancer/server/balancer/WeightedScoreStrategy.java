package se.telavox.mediaserver_loadbalancer.server.balancer;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.telavox.mediaserver_loadbalancer.server.polling.MediaServerState;
import se.telavox.mediaserver_loadbalancer.shared.LoadReport;
import se.telavox.mediaserver_loadbalancer.shared.PauseState;

/**
 * Selects the mediaserver with the lowest weighted composite score.
 * <p>
 * The score is computed as:
 * <pre>
 *   score = (cpuWeight * cpuUsage) + (memWeight * memoryUsage) + (streamWeight * normalizedStreamCount)
 * </pre>
 * where {@code normalizedStreamCount} is {@code rtpStreamCount / maxStreamsForNormalization}.
 * <p>
 * Servers that are not healthy, or have a pause state other than {@link PauseState#ENABLED},
 * are excluded from selection. {@link PauseState#STARTING} servers are still initializing,
 * {@link PauseState#PAUSED} servers are draining existing calls, and
 * {@link PauseState#STOPPED} servers are fully excluded.
 */
public class WeightedScoreStrategy implements BalancerStrategy {

    private static final Logger log = LoggerFactory.getLogger(WeightedScoreStrategy.class);

    private final double cpuWeight;
    private final double memoryWeight;
    private final double streamWeight;

    /**
     * The maximum stream count used for normalization so that stream count
     * is comparable to the 0.0-1.0 range of CPU and memory usage.
     */
    private final int maxStreamsForNormalization;

    /**
     * Creates a strategy with the given weights and normalization ceiling.
     *
     * @param cpuWeight                 weight for CPU usage (e.g. 0.4)
     * @param memoryWeight              weight for memory usage (e.g. 0.3)
     * @param streamWeight              weight for stream count (e.g. 0.3)
     * @param maxStreamsForNormalization ceiling for stream count normalization (e.g. 500)
     */
    public WeightedScoreStrategy(double cpuWeight, double memoryWeight,
                                  double streamWeight, int maxStreamsForNormalization) {
        this.cpuWeight = cpuWeight;
        this.memoryWeight = memoryWeight;
        this.streamWeight = streamWeight;
        this.maxStreamsForNormalization = maxStreamsForNormalization;
    }

    /**
     * Creates a strategy with default weights: cpu=0.4, memory=0.3, streams=0.3,
     * maxStreams=500.
     */
    public WeightedScoreStrategy() {
        this(0.4, 0.3, 0.3, 500);
    }

    @Override
    public MediaServerState select(Collection<MediaServerState> candidates) {
        MediaServerState best = null;
        double bestScore = Double.MAX_VALUE;

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

            double score = computeScore(report);

            if (log.isDebugEnabled()) {
                log.debug("Score for {}: {} (cpu={}, mem={}, streams={})",
                        state.getServerEntry(), String.format("%.4f", score),
                        report.getCpuUsage(), report.getMemoryUsage(),
                        report.getRtpStreamCount());
            }

            if (score < bestScore) {
                bestScore = score;
                best = state;
            }
        }

        if (best != null) {
            log.info("Selected {} with score {}", best.getServerEntry(),
                    String.format("%.4f", bestScore));
        }

        return best;
    }

    double computeScore(LoadReport report) {
        double normalizedStreams = Math.min(1.0,
                (double) report.getRtpStreamCount() / maxStreamsForNormalization);

        return (cpuWeight * report.getCpuUsage())
                + (memoryWeight * report.getMemoryUsage())
                + (streamWeight * normalizedStreams);
    }
}
