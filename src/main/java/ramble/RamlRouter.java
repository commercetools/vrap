package ramble;

import com.google.common.base.Joiner;
import org.raml.v2.api.model.v10.api.Api;
import org.raml.v2.api.model.v10.bodies.Response;
import org.raml.v2.api.model.v10.datamodel.ExampleSpec;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;
import org.raml.v2.api.model.v10.methods.Method;
import org.raml.v2.api.model.v10.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.handling.Handlers;
import ratpack.http.Headers;
import ratpack.http.Request;
import ratpack.http.client.HttpClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * This class routes request for raml resource to a raml route.
 */
class RamlRouter implements Handler {
    private final Logger LOG = LoggerFactory.getLogger(RamlRouter.class);

    @Override
    public void handle(final Context ctx) throws Exception {
        final RamlModelRepository ramlModelRepository = ctx.get(RamlModelRepository.class);
        final Api api = ramlModelRepository.getApi();
        final List<Handler> routes = createRoutes(api);
        ctx.insert(routes.toArray(new Handler[routes.size()]));
    }

    private List<Handler> createRoutes(final Api api) {
        return createRoutes(api, api.resources());
    }

    private List<Handler> createRoutes(final Api api, final List<Resource> resources) {
        final List<Handler> routes = new ArrayList<>();

        for (final Resource resource : resources) {
            String ramlPath = resource.resourcePath().substring(1); // remove leading "/"

            for (TypeDeclaration uriP : resource.uriParameters())  {
                ramlPath = ramlPath.replaceAll("\\{" + uriP.name() + "\\}", ":" + uriP.name());
            }
            final String ratpackPath ;
            if (ramlPath.contains("{") && ramlPath.contains("}")) {
                LOG.warn("Resource path contains unspecified uri parameter: {}", ramlPath);
                ratpackPath = Pattern.compile("\\{([^}]*)\\}").matcher(ramlPath).replaceAll(":$1");
            } else {
                ratpackPath = ramlPath;
            }

            routes.add(Handlers.path(ratpackPath, new Route(api, resource)));

            routes.addAll(createRoutes(api, resource.resources()));
        }
        return routes;
    }


    static class Route implements Handler {
        private final static Logger LOG = LoggerFactory.getLogger(Route.class);
        private static final String MODE_HEADER = "X-Ramble-Mode";

        private final Api api;
        private final Resource resource;

        public Route(final Api api, final Resource resource) {
            this.api = api;
            this.resource = resource;
        }

        @Override
        public void handle(final Context ctx) throws Exception {
            final Request request = ctx.getRequest();
            final String method = request.getMethod().getName().toLowerCase();
            final String path = request.getPath();
            LOG.debug("Request path: {}", path);

            final Optional<Method> ramlMethod = resource.methods().stream().filter(m -> m.method().equals(method)).findFirst();
            if (ramlMethod.isPresent()) {
                switch (mode(ctx)) {
                    case proxy:
                        proxyRequest(ctx, request);
                        break;
                    case example:
                        sendExample(ctx, ramlMethod);
                        break;
                }
            } else {
                ctx.next();
            }
        }

        private void proxyRequest(final Context ctx, final Request request) {
            final URI uri = proxiedUri(ctx);
            final HttpClient httpClient = ctx.get(HttpClient.class);
            request.getBody().flatMap(incoming -> httpClient.requestStream(uri, spec -> {
                spec.getBody().buffer(incoming.getBuffer());
                spec.getHeaders().copy(request.getHeaders());
                spec.method(request.getMethod());
            })).then(streamedResponse -> streamedResponse.forwardTo(ctx.getResponse()));
        }


        private void sendExample(final Context ctx, final Optional<Method> ramlMethod) {
            final Optional<Response> response = ramlMethod.get().responses().stream().findFirst();
            final Optional<ExampleSpec> example = response.flatMap(r -> r.body().stream()
                    .findFirst()).map(TypeDeclaration::example);

            if (example.isPresent()) {
                ctx.render(example.get().value());
            } else {
                ctx.getResponse().send("No example found.");
            }
        }

        private RambleMode mode(final Context ctx) {
            final Headers headers = ctx.getRequest().getHeaders();
            return Optional.ofNullable(headers.get(MODE_HEADER))
                    .map(RambleMode::valueOf)
                    .orElse(RambleMode.example);
        }

        private URI proxiedUri(final Context ctx) {
            final Request request = ctx.getRequest();
            final String query = request.getQuery();
            final String boundPath = ctx.getPathBinding().getBoundTo() + (!query.isEmpty() ? "?" + query : "");
            final String baseUri = api.baseUri().value();
            final String uriStr = baseUri.endsWith("/") ?
                    baseUri + boundPath :
                    Joiner.on("/").join(baseUri, boundPath);
            return URI.create(uriStr);
        }
    }
}
