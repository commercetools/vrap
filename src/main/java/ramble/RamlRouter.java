package ramble;

import com.google.common.base.Joiner;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.raml.v2.api.model.v10.api.Api;
import org.raml.v2.api.model.v10.datamodel.ExampleSpec;
import org.raml.v2.api.model.v10.datamodel.StringTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;
import org.raml.v2.api.model.v10.methods.Method;
import org.raml.v2.api.model.v10.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.func.Action;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.handling.Handlers;
import ratpack.handling.RequestLogger;
import ratpack.http.Headers;
import ratpack.http.Request;
import ratpack.http.TypedData;
import ratpack.http.client.HttpClient;
import ratpack.http.client.ReceivedResponse;
import ratpack.http.client.RequestSpec;
import ratpack.registry.Registry;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static ratpack.jackson.Jackson.json;

/**
 * This class routes request for raml resource to a raml route.
 */
class RamlRouter {

    private final static Logger LOG = LoggerFactory.getLogger(RamlRouter.class);
    private final Handler routes;

    public RamlRouter(final Api api) {
        routes = Handlers.chain(createRoutes(api));
    }

    public Handler getRoutes() {
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
                    final Route route = new Route(resource, method);
                    methodHandlers.put(method, route);
                } else {
                    final Map<String, Handler> contentTypeHandlers = new HashMap<>();

                    for (final TypeDeclaration bodyDeclaration : method.body()) {
                        final Route route = new Route(resource, method);
                        contentTypeHandlers.put(bodyDeclaration.name(), route);
                    }
                    methodHandlers.put(method, Handlers.chain(ctx2 ->
                            ctx2.byContent(byContentSpec -> contentTypeHandlers.entrySet()
                                    .forEach(e -> byContentSpec.type(e.getKey(), () -> e.getValue().handle(ctx2))))));
                }
            }

            final List<Handler> children = new ArrayList<>();

            children.add(Handlers.path(
                    "",
                    ctx3 -> ctx3.byMethod(byMethodSpec ->
                            methodHandlers.entrySet().
                                    forEach(e -> byMethodSpec.named(e.getKey().method(), () -> e.getValue().handle(ctx3))))
            ));
            children.addAll(createRoutes(api, resource.resources()));

            routes.add(
                    Handlers.prefix(
                            ratpackPath,
                            Handlers.chain(children)
                    )
            );
        }
        return routes;
    }

    private static String mapToRatpackPath(final Resource resource) {
        String ramlPath = resource.relativeUri().value().substring(1); // remove leading "/"
        final String ratpackPath;
        final String directoryPattern = "[-a-zA-Z0-9@:%_\\+.~#?&=]+";

        Boolean simplePattern = true;

        if (!ramlPath.contains("{") && !ramlPath.contains("}")) {
            return ramlPath;
        }

        for (final TypeDeclaration uriParamDeclaration : resource.uriParameters()) {
            String pattern = directoryPattern;
            if (uriParamDeclaration instanceof StringTypeDeclaration) {
                String uriPattern = ((StringTypeDeclaration) uriParamDeclaration).pattern();
                if (uriPattern != null) {
                    simplePattern = false;
                    pattern = uriPattern;
                }
            }
            ramlPath = ramlPath.replace("{" + uriParamDeclaration.name() + "}", pattern).replace("^", "").replace("$", "");
        }


        if (simplePattern && ramlPath.contains("{") && ramlPath.contains("}")) {
            LOG.warn("Resource path contains unspecified uri parameter: {}", ramlPath);
            ratpackPath = Pattern.compile("\\{([^}]*)\\}").matcher(ramlPath).replaceAll(directoryPattern);
        } else {
            ratpackPath = ramlPath;
        }

        return "::" + ratpackPath;
    }


    static class Route implements Handler {
        private final static Logger LOG = LoggerFactory.getLogger(Route.class);
        private static final String MODE_HEADER = "X-Ramble-Mode";

        private final RequestValidationHandler requestValidationHandler = new RequestValidationHandler();
        private final RequestProxyHandler requestProxyHandler = new RequestProxyHandler();
        private final RequestExampleHandler requestExampleHandler = new RequestExampleHandler();
        private final ReceivedResponseValidationHandler receivedResponseValidationHandler = new ReceivedResponseValidationHandler();
        private final ReceivedResponseForwardHandler receivedResponseForwardHandler = new ReceivedResponseForwardHandler();

        private final Handler delegate;

        public Route(final Resource resource, final Method method) {
            final Registry registry = Registry.builder().add(resource).add(method).build();
            final Handler chain = Handlers.chain(
                    RequestLogger.ncsa(LOG),
                    requestValidationHandler,
                    Handlers.when(this::isProxyMode,
                            Handlers.chain(
                                    requestProxyHandler,
                                    receivedResponseValidationHandler,
                                    receivedResponseForwardHandler)),
                    Handlers.when(this::isExampleMode, requestExampleHandler));
            this.delegate = Handlers.register(registry, chain);
        }

        @Override
        public void handle(final Context ctx) throws Exception {
            final String path = ctx.getRequest().getPath();
            LOG.debug("Request path: {}", path);

            delegate.handle(ctx);
        }

        private boolean isProxyMode(final Context ctx) {
            return mode(ctx).equals(RambleMode.proxy);
        }

        private boolean isExampleMode(final Context ctx) {
            return mode(ctx).equals(RambleMode.example);
        }

        private RambleMode mode(final Context ctx) {
            final Headers headers = ctx.getRequest().getHeaders();
            final RambleApp.RambleOptions options = ctx.get(RambleApp.RambleOptions.class);
            return RambleMode.parse(headers.get(MODE_HEADER)).orElse(options.getMode());
        }
    }

    /**
     * This handler validates the request {@link Context#getRequest()} against
     * the {@link Method} from its context.
     *
     * When the validation fails, the validation errors will be sent.
     * Otherwise the execution is passed to the next handler.
     */
    private static class RequestValidationHandler implements Handler {

        @Override
        public void handle(Context ctx) throws Exception {
            ctx.getRequest().getBody().then(body -> validateRequest(ctx, body));
        }

        private void validateRequest(final Context ctx, final TypedData body) {
            final Validator validator = ctx.get(Validator.class);
            final Method method = ctx.get(Method.class);
            final Optional<Validator.ValidationErrors> validationErrors = validator.validateRequest(ctx, body, method);

            if (validationErrors.isPresent()) {
                ctx.getResponse().status(RambleStatus.BAD_REQUEST);
                ctx.render(json(validationErrors.get()));
            } else {
                ctx.next(Registry.single(body));
            }
        }
    }

    /**
     * This handler renders an example for the method {@link Method} registered on its context
     * for the content type the client requested {@link HttpHeaderNames#ACCEPT}.
     */
    private static class RequestExampleHandler implements Handler {

        @Override
        public void handle(final Context ctx) throws Exception {
            final Method method = ctx.get(Method.class);

            final List<TypeDeclaration> bodTypeDeclarations = method.responses().stream()
                    .flatMap(r -> r.body().stream()).collect(Collectors.toList());

            final Map<String, ExampleSpec> contentTypeToExample = bodTypeDeclarations.stream()
                    .collect(Collectors.toMap(TypeDeclaration::name, TypeDeclaration::example));

            ctx.byContent(byContentSpec ->
                    contentTypeToExample.entrySet().stream()
                            .forEach(e -> byContentSpec.type(e.getKey(), () -> ctx.render(e.getValue().value()))));
        }
    }


    /**
     * This handler proxies the request {@link Context#getRequest()} to the base uri and passes
     * the response and the proxied uri to the next handler.
     */
    private static class RequestProxyHandler implements Handler {

        @Override
        public void handle(final Context ctx) throws Exception {
            final Request request = ctx.getRequest();
            final TypedData body = ctx.get(TypedData.class);
            final HttpClient httpClient = ctx.get(HttpClient.class);
            final URI proxiedUri = proxiedUri(ctx);
            LOG.info("Forward to: {}", proxiedUri);

            httpClient.request(proxiedUri, proxyRequest(body, request))
                    .then(receivedResponse ->
                            ctx.next(Registry.builder().add(receivedResponse).add(proxiedUri).build()));
        }

        private URI proxiedUri(final Context ctx) {
            final Api api = ctx.get(RamlModelRepository.class).getApi();

            final RambleApp.RambleOptions options = ctx.get(RambleApp.RambleOptions.class);

            final Request request = ctx.getRequest();
            final String query = request.getQuery();
            final String boundPath = request.getPath().replaceAll("^api/", "") + (!query.isEmpty() ? "?" + query : "");

            final String baseUri = options.getApiUrl().orElse(api.baseUri().value());

            final String uriStr = baseUri.endsWith("/") ?
                    baseUri + boundPath :
                    Joiner.on("/").join(baseUri, boundPath);

            return URI.create(uriStr);
        }

        private Action<RequestSpec> proxyRequest(final TypedData body, final Request request) {
            return spec -> {
                spec.getBody().buffer(body.getBuffer());
                spec.getHeaders().copy(request.getHeaders());
                spec.method(request.getMethod());
            };
        }
    }

    /**
     * This handler forwards the received response from its context to the contexts response.
     */
    private static class ReceivedResponseForwardHandler implements Handler {

        @Override
        public void handle(Context ctx) throws Exception {
            ctx.get(ReceivedResponse.class).forwardTo(ctx.getResponse());
        }
    }

    /**
     * This handler retrieves the {@link ReceivedResponse} from its context and validates it aginst
     * the {@link Method} from its context.
     *
     * When the validation fails, the validation errors will be sent.
     * Otherwise the execution is passed to the next handler.
     */
    private static class ReceivedResponseValidationHandler implements Handler {

        @Override
        public void handle(Context ctx) throws Exception {
            final ReceivedResponse receivedResponse = ctx.get(ReceivedResponse.class);
            final Validator validator = ctx.get(Validator.class);
            final Method method = ctx.get(Method.class);
            final Optional<Validator.ValidationErrors> receivedResponseErrors = validator.validateReceivedResponse(receivedResponse, method);

            if (receivedResponseErrors.isPresent()) {
                ctx.getResponse().status(RambleStatus.BAD_GATEWAY);
                ctx.render(json(receivedResponseErrors.get()));
            } else {
                ctx.next();
            }
        }
    }
}
