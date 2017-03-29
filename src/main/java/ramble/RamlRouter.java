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
import ratpack.func.Action;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.handling.Handlers;
import ratpack.http.Headers;
import ratpack.http.Request;
import ratpack.http.TypedData;
import ratpack.http.client.HttpClient;
import ratpack.http.client.ReceivedResponse;
import ratpack.http.client.RequestSpec;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

import static ratpack.jackson.Jackson.json;

/**
 * This class routes request for raml resource to a raml route.
 */
class RamlRouter implements Handler {
    private final static Logger LOG = LoggerFactory.getLogger(RamlRouter.class);

    @Override
    public void handle(final Context ctx) throws Exception {
        final RamlModelRepository ramlModelRepository = ctx.get(RamlModelRepository.class);
        ctx.insert(ramlModelRepository.getRoutes());
    }

    static class Routes {
        private final static Logger LOG = LoggerFactory.getLogger(RamlRouter.class);
        private final Handler[] routes;

        public Routes(final Api api) {
            final List<Handler> ramlRoutes = createRoutes(api);
            routes = ramlRoutes.toArray(new Handler[ramlRoutes.size()]);
        }

        public Handler[] getRoutes() {
            return routes;
        }


        private List<Handler> createRoutes(final Api api) {
            return createRoutes(api, api.resources());
        }

        private List<Handler> createRoutes(final Api api, final List<Resource> resources) {
            final List<Handler> routes = new ArrayList<>();

            for (final Resource resource : resources) {
                final String ratpackPath = mapToRatpackPath(resource);

                final Map<Method, Handler> methodHandlers = new HashMap<>();

                for (final Method method : resource.methods()) {
                    if (method.body().isEmpty()) {
                        final Route route = new Route(api, resource, method, Optional.empty());
                        methodHandlers.put(method, route);
                    }
                    else {
                        final Map<String, Handler> contentTypeHandlers = new HashMap<>();

                        for (final TypeDeclaration bodyDeclaration : method.body()) {
                            final Route route = new Route(api, resource, method, Optional.of(bodyDeclaration));
                            contentTypeHandlers.put(bodyDeclaration.name(), route);
                        }
                        methodHandlers.put(method, Handlers.chain(ctx ->
                                ctx.byContent(byContentSpec -> contentTypeHandlers.entrySet()
                                        .forEach(e -> byContentSpec.type(e.getKey(), () -> e.getValue().handle(ctx))))));
                    }
                }
                routes.add(Handlers.path(ratpackPath, ctx -> ctx.byMethod(byMethodSpec ->
                        methodHandlers.entrySet().
                                forEach(e -> byMethodSpec.named(e.getKey().method(), () -> e.getValue().handle(ctx))))));

                routes.addAll(createRoutes(api, resource.resources()));
            }
            return routes;
        }

        private static String mapToRatpackPath(final Resource resource) {
            String ramlPath = resource.resourcePath().substring(1); // remove leading "/"

            for (final TypeDeclaration uriParamDeclaration : resource.uriParameters())  {
                ramlPath = ramlPath.replaceAll("\\{" + uriParamDeclaration.name() + "\\}", ":" + uriParamDeclaration.name());
            }
            final String ratpackPath;

            if (ramlPath.contains("{") && ramlPath.contains("}")) {
                LOG.warn("Resource path contains unspecified uri parameter: {}", ramlPath);
                ratpackPath = Pattern.compile("\\{([^}]*)\\}").matcher(ramlPath).replaceAll(":$1");
            } else {
                ratpackPath = ramlPath;
            }

            return ratpackPath;
        }
    }

    static class Route implements Handler {
        private final static Logger LOG = LoggerFactory.getLogger(Route.class);
        private static final String MODE_HEADER = "X-Ramble-Mode";

        private final Api api;
        private final Resource resource;
        private final Method method;
        private final Optional<TypeDeclaration> bodyDeclaration;

        public Route(final Api api, final Resource resource, final Method method, final Optional<TypeDeclaration> bodyDeclaration) {
            this.api = api;
            this.resource = resource;
            this.method = method;
            this.bodyDeclaration = bodyDeclaration;
        }

        @Override
        public void handle(final Context ctx) throws Exception {
            final Request request = ctx.getRequest();
            final String path = request.getPath();
            LOG.debug("Request path: {}", path);
            final Validator validator = ctx.get(Validator.class);

            final Optional<Validator.ValidationErrors> validationErrors = validator.validateRequest(ctx, resource, method);
            if (validationErrors.isPresent()) {
                ctx.getResponse().status(400);
                ctx.render(json(validationErrors.get()));
            } else {
                switch (mode(ctx)) {
                    case proxy:
                        proxyRequest(ctx);
                        break;
                    case example:
                        sendExample(ctx, method);
                        break;
                }
            }
        }

        private void proxyRequest(final Context ctx) {
            ctx.getRequest().getBody().then(incoming -> streamValidatedBody(ctx, incoming));
        }

        private void streamValidatedBody(final Context ctx, final TypedData body) {
            final Validator validator = ctx.get(Validator.class);
            final Optional<Validator.ValidationErrors> validationErrors = validator.validateRequestBody(body, method);

            if (validationErrors.isPresent()) {
                ctx.getResponse().status(400);
                ctx.render(json(validationErrors.get()));
            } else {
                final Request request = ctx.getRequest();
                final HttpClient httpClient = ctx.get(HttpClient.class);
                final URI proxiedUri = proxiedUri(ctx);
                httpClient.request(proxiedUri, proxyRequest(body, request)).then(handleReceivedResponse(ctx, validator));
            }
        }

        private Action<ReceivedResponse> handleReceivedResponse(final Context ctx, final Validator validator) {
            return receivedResponse -> {
                final Optional<Validator.ValidationErrors> receivedResponseErrors = validator.validateReceivedResponse(receivedResponse, method);

                if (receivedResponseErrors.isPresent()) {
                    ctx.getResponse().status(502);
                    ctx.render(json(receivedResponseErrors.get()));
                } else {
                    receivedResponse.forwardTo(ctx.getResponse());
                }
            };
        }

        private Action<RequestSpec> proxyRequest(final TypedData body, final Request request) {
            return spec -> {
                spec.getBody().buffer(body.getBuffer());
                spec.getHeaders().copy(request.getHeaders());
                spec.method(request.getMethod());
            };
        }

        private void sendExample(final Context ctx, final Method ramlMethod) {
            final Optional<Response> response = ramlMethod.responses().stream().findFirst();
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
            final RambleApp.RambleOptions options = ctx.get(RambleApp.RambleOptions.class);
            return RambleMode.parse(headers.get(MODE_HEADER)).orElse(options.getMode());
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
