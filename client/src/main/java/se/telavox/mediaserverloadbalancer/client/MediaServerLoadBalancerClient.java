package se.telavox.mediaserverloadbalancer.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Lightweight HTTP client for the MediaServer Load Balancer service.
 * <p>
 * This client is transport-only: it calls the LB's {@code GET /api/select}
 * endpoint and returns the parsed {@link SelectResponse}. It has no dependency
 * on proxy-specific classes ({@code MediaServerInterface}, {@code ConfiguratorClient},
 * etc.) so it can be used as a standalone library.
 * <p>
 * Thread-safe. The underlying {@link HttpClient} is reused across calls.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * MediaServerLoadBalancerClient client =
 *     new MediaServerLoadBalancerClient("http://mslb.service.telavox.se:8102");
 * SelectResponse response = client.select("default");
 * // response.getHost(), response.getPort(), response.toMediaServerUrl()
 * }</pre>
 */
public class MediaServerLoadBalancerClient {

    private static final Logger log = LoggerFactory.getLogger(MediaServerLoadBalancerClient.class);

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    private volatile String baseUrl;

    /**
     * Creates a client with the given base URL and default timeouts
     * (2s connect, 3s request).
     *
     * @param baseUrl the load balancer base URL, e.g. {@code http://mslb.service.telavox.se:8102}.
     *                May be {@code null} if not yet configured; calls to {@link #select} will
     *                throw {@link MediaServerLoadBalancerException} until a URL is set.
     */
    public MediaServerLoadBalancerClient(String baseUrl) {
        this(baseUrl, DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Creates a client with explicit timeout configuration.
     *
     * @param baseUrl        the load balancer base URL (may be {@code null})
     * @param connectTimeout TCP connect timeout
     * @param requestTimeout per-request timeout (includes connect + response)
     */
    public MediaServerLoadBalancerClient(String baseUrl, Duration connectTimeout, Duration requestTimeout) {
        this.baseUrl = baseUrl;
        this.requestTimeout = requestTimeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
    }

    /**
     * Updates the base URL at runtime (e.g. from a configurator property listener).
     *
     * @param baseUrl the new base URL, or {@code null} to disable
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Returns the current base URL.
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Selects the best available mediaserver from the given pool.
     * <p>
     * Calls {@code GET /api/select?pool={poolName}} on the load balancer and
     * returns the parsed response containing the selected server's host and port.
     *
     * @param poolName the pool to select from
     * @return the selected server
     * @throws MediaServerLoadBalancerException if the LB is not configured, unreachable,
     *         returns a non-2xx status, or the response cannot be parsed
     */
    public SelectResponse select(String poolName) throws MediaServerLoadBalancerException {
        String url = this.baseUrl;
        if (url == null || url.isBlank()) {
            throw new MediaServerLoadBalancerException("No load balancer URL configured");
        }

        String selectUrl = url + "/api/select?pool=" + poolName;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(selectUrl))
                    .timeout(requestTimeout)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status == 503) {
                throw new NoHealthyServerException(
                        "No healthy servers available in pool '" + poolName + "'", poolName);
            }

            if (status < 200 || status >= 300) {
                throw new MediaServerLoadBalancerException(
                        "Load balancer returned HTTP " + status + ": " + response.body());
            }

            SelectResponse selectResponse = objectMapper.readValue(response.body(), SelectResponse.class);
            log.debug("Selected server {} from pool '{}'", selectResponse, poolName);
            return selectResponse;

        } catch (MediaServerLoadBalancerException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaServerLoadBalancerException("Interrupted while contacting load balancer", e);
        } catch (Exception e) {
            throw new MediaServerLoadBalancerException(
                    "Failed to contact load balancer at '" + selectUrl + "'", e);
        }
    }
}
