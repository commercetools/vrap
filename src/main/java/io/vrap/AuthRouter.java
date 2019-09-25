package io.vrap;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import io.vrap.rmf.raml.model.modules.Api;
import io.vrap.rmf.raml.model.security.OAuth20Settings;
import io.vrap.rmf.raml.model.security.SecurityScheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.form.Form;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.handling.Handlers;
import ratpack.handling.RequestLogger;
import ratpack.http.Request;
import ratpack.http.client.HttpClient;
import ratpack.registry.Registry;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;


public class AuthRouter {
    private final static Logger LOG = LoggerFactory.getLogger(AuthRouter.class);

    private final Handler routes;

    public AuthRouter(final Api api) throws Exception {

        List<SecurityScheme> oauthSchemes = api != null ?
                api.getSecuritySchemes().stream().filter(securityScheme -> securityScheme.getType().getName().equals("OAuth 2.0")).collect(Collectors.toList()) :
                Lists.newArrayList();

        routes = Handlers.chain(createRoutes(oauthSchemes));
    }

    public Handler getRoutes() {
        return routes;
    }

    private List<Handler> createRoutes(final List<SecurityScheme> schemes) throws Exception {
        final List<Handler> routes = new ArrayList<>();

        for (final SecurityScheme scheme : schemes) {
            routes.add(Handlers.prefix(
                    scheme.getType().getName(),
                    new Route(((OAuth20Settings) scheme.getSettings()).getAccessTokenUri())
            ));
        }

        return routes;
    }

    static class Route implements Handler {
        private final static Logger LOG = LoggerFactory.getLogger(AuthRouter.Route.class);

        private final AuthRouter.RequestProxyHandler requestProxyHandler;

        private final Handler delegate;

        public Route(final String authUri) {
            requestProxyHandler = new AuthRouter.RequestProxyHandler(authUri);
            final Registry registry = Registry.builder().add(authUri).build();
            final Handler chain = Handlers.chain(
                    RequestLogger.ncsa(LOG),
                    requestProxyHandler
            );
            this.delegate = Handlers.register(registry, chain);
        }

        @Override
        public void handle(final Context ctx) throws Exception {
            final String path = ctx.getRequest().getPath();
            LOG.debug("Request path: {}", path);

            delegate.handle(ctx);
        }
    }

    /**
     * This handler proxies the request {@link Context#getRequest()} to the base uri and passes
     * the response and the proxied uri to the next handler.
     */
    private static class RequestProxyHandler implements Handler {
        private final String authUri;

        public RequestProxyHandler(final String authUri) {
            this.authUri = authUri;
        }

        @Override
        public void handle(final Context ctx) throws Exception {
            final Request request = ctx.getRequest();
            final HttpClient httpClient = ctx.get(HttpClient.class);
            final URI proxiedUri = URI.create(authUri);
            LOG.info("Forward to: {}", proxiedUri);

            ctx.parse(Form.class).then(form -> {
                httpClient.requestStream(proxiedUri, requestSpec -> {
                    final String s = form.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining("&"));
                    requestSpec.getBody().bytes(s.getBytes(Charsets.UTF_8));
                    requestSpec.getHeaders().copy(request.getHeaders());
                    requestSpec.method(request.getMethod());

                    if (form.containsKey("client_id") && form.containsKey("client_secret")) {
                        final String auth = Base64.getEncoder().encodeToString((form.get("client_id") + ":" + form.get("client_secret")).getBytes(Charsets.UTF_8));
                        requestSpec.getHeaders().add("Authorization", "Basic " + auth);
                    }
                }).then(receivedResponse ->
                        receivedResponse.forwardTo(ctx.getResponse(), mutableHeaders -> {
                            mutableHeaders.add("Via", "Vrap OAuth 2.0 proxy");
                        }));
            });
        }
    }
}
