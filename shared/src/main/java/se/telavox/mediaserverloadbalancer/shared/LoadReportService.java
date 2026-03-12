package se.telavox.mediaserverloadbalancer.shared;

/**
 * JSON-RPC service interface for load reporting and state control on a mediaserver.
 * <p>
 * This interface defines the contract that the mediaserver must implement
 * as a JSON-RPC service. The loadbalancer polls each mediaserver by calling
 * {@link #getLoadReport()} and controls the server's operational state via
 * {@link #setPauseState(PauseState)}.
 */
public interface LoadReportService {

    /**
     * Returns the current load report for this mediaserver instance.
     *
     * @return a {@link LoadReport} containing OS resource utilization,
     *         active RTP stream count, and the server's pause state
     */
    LoadReport getLoadReport();

    /**
     * Sets the operational pause state of this mediaserver instance.
     * <p>
     * Called by the loadbalancer to control whether the server should
     * accept new calls, drain existing calls, or be fully excluded.
     *
     * @param state the desired {@link PauseState}
     */
    void setPauseState(PauseState state);
}
