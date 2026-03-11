package se.telavox.mediaserverloadbalancer.shared;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A load report from a single mediaserver instance.
 * <p>
 * Returned by the mediaserver when polled via JSON-RPC. Contains OS-level
 * resource utilization, the current number of active RTP streams, and the
 * server's operational pause state.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoadReport {

    /** CPU utilization as a ratio from 0.0 (idle) to 1.0 (fully loaded). */
    private double cpuUsage;

    /** Memory utilization as a ratio from 0.0 (free) to 1.0 (fully used). */
    private double memoryUsage;

    /** The number of currently active RTP streams on this mediaserver. */
    private int rtpStreamCount;

    /** The operational state of this mediaserver (ENABLED, PAUSED, STOPPED). */
    private PauseState pauseState;

    /** Epoch millis when this report was generated on the mediaserver. */
    private long timestamp;

    public LoadReport() {
    }

    public LoadReport(double cpuUsage, double memoryUsage, int rtpStreamCount,
                      PauseState pauseState, long timestamp) {
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
        this.rtpStreamCount = rtpStreamCount;
        this.pauseState = pauseState;
        this.timestamp = timestamp;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public double getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(double memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public int getRtpStreamCount() {
        return rtpStreamCount;
    }

    public void setRtpStreamCount(int rtpStreamCount) {
        this.rtpStreamCount = rtpStreamCount;
    }

    public PauseState getPauseState() {
        return pauseState;
    }

    public void setPauseState(PauseState pauseState) {
        this.pauseState = pauseState;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "LoadReport{" +
                "cpuUsage=" + cpuUsage +
                ", memoryUsage=" + memoryUsage +
                ", rtpStreamCount=" + rtpStreamCount +
                ", pauseState=" + pauseState +
                ", timestamp=" + timestamp +
                '}';
    }
}
