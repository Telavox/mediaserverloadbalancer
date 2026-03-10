package se.telavox.mediaserver_loadbalancer.shared;

/**
 * JSON-RPC service interface for retrieving load reports from a mediaserver.
 * <p>
 * This interface defines the contract that the mediaserver must implement
 * as a JSON-RPC service. The loadbalancer polls each mediaserver by calling
 * {@link #getLoadReport()} via the mediaserver's {@code /rpc} endpoint.
 */
public interface LoadReportService {

    /**
     * Returns the current load report for this mediaserver instance.
     *
     * @return a {@link LoadReport} containing OS resource utilization,
     *         active RTP stream count, and the server's pause state
     */
    LoadReport getLoadReport();
}
