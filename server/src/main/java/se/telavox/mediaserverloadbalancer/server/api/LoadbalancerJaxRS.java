package se.telavox.mediaserverloadbalancer.server.api;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import se.telavox.base.Base;
import se.telavox.mediaserverloadbalancer.server.balancer.BalancerStrategy;
import se.telavox.mediaserverloadbalancer.server.polling.MediaServerPoller;

/**
 * Jersey application configuration for the loadbalancer HTTP API.
 * <p>
 * Registers JAX-RS resources and loads the servlet into the Jetty
 * server provided by {@link Base}.
 */
public class LoadbalancerJaxRS extends ResourceConfig {

    private final Base base;

    public LoadbalancerJaxRS(Base base, MediaServerPoller poller, BalancerStrategy strategy) {
        this.base = base;
        register(new LoadbalancerApi(poller, strategy));
        register(JacksonFeature.class);
    }

    /**
     * Loads the Jersey servlet into the Base HTTP server at {@code /api/*}.
     */
    public void load() {
        ServletHolder holder = new ServletHolder(new ServletContainer(this));
        HandlerCollection handlers = (HandlerCollection) base.getHttpServer().getServer().getHandler();

        boolean loaded = false;
        for (Handler handler : handlers.getHandlers()) {
            if (handler instanceof WebAppContext) {
                if ("/".equals(((WebAppContext) handler).getContextPath())) {
                    ((WebAppContext) handler).addServlet(holder, "/api/*");
                    loaded = true;
                }
            }
        }

        if (!loaded) {
            throw new RuntimeException("Failed to load Jersey into Jetty WebAppContext");
        }
    }
}
