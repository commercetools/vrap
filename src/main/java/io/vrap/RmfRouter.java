package io.vrap;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.vrap.rmf.raml.model.modules.Api;
import io.vrap.rmf.raml.model.resources.Method;
import io.vrap.rmf.raml.model.resources.Resource;
import io.vrap.rmf.raml.model.responses.Body;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.func.Action;
import ratpack.func.Predicate;
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
import ratpack.registry.NotInRegistryException;
import ratpack.registry.Registry;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static ratpack.jackson.Jackson.json;

/**
 * This class routes request for raml resource to a raml route.
 */
class RmfRouter {

    private final static Logger LOG = LoggerFactory.getLogger(RmfRouter.class);

    private final Handler routes;

    public RmfRouter(final Api api) throws Exception {
        final String apiPath = api != null ?
                RamlRatpackPathMapper.map(new URI(api.getBaseUri().getValue().getTemplate().replace("{", "%7B").replace("}", "%7D")).getPath().replace("%7B", "{").replace("%7D", "}")) :
                "";
        if (apiPath != null && !apiPath.equals("")) {
            routes = Handlers.prefix(apiPath, Handlers.chain(createRoutes(api)));
        } else {
            routes = Handlers.chain(createRoutes(api));
        }
    }

    public Handler getRoutes() {
        return routes;
    }

    private List<Handler> createRoutes(final Api api) throws Exception {
        return api != null ? createRoutes(api, api.getResources()) : Lists.newArrayList();
    }

    private List<Handler> createRoutes(final Api api, final List<Resource> resources) throws Exception {
        final List<Handler> routes = new ArrayList<>();

        for (final Resource resource : resources) {
            final String expand = resource.getRelativeUri().getTemplate();
            final String ratpackPath = RmfRatpackPathMapper.map(expand, resource.getUriParameters());

            final Map<Method, Handler> methodHandlers = new HashMap<>();

            for (final Method method : resource.getMethods()) {
                if (method.getBodies().isEmpty()) {
                    final Route route = new Route(resource, method);
                    methodHandlers.put(method, route);
                } else {
                    final Map<String, Handler> contentTypeHandlers = new HashMap<>();

                    for (final Body bodyDeclaration : method.getBodies()) {
                        final Route route = new Route(resource, method);
                        contentTypeHandlers.put(bodyDeclaration.getContentType(), route);
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
                                    forEach(e -> byMethodSpec.named(e.getKey().getMethodName(), () -> e.getValue().handle(ctx3))))
            ));
            children.addAll(createRoutes(api, resource.getResources()));

            routes.add(
                    Handlers.prefix(
                            ratpackPath,
                            Handlers.chain(children)
                    )
            );
        }
        return routes;
    }

//    private static String mapToRatpackPath(final Resource resource) {
//        String ramlPath = resource.relativeUri().value().substring(1); // remove leading "/"
//        final String ratpackPath;
//        final String directoryPattern = "[-a-zA-Z0-9@:%_\\+.~#?&=]+";
//
//        Boolean simplePattern = true;
//
//        if (!ramlPath.contains("{") && !ramlPath.contains("}")) {
//            return ramlPath;
//        }
//
//        for (final TypeDeclaration uriParamDeclaration : resource.uriParameters()) {
//            String pattern = directoryPattern;
//            if (uriParamDeclaration instanceof StringTypeDeclaration) {
//                String uriPattern = ((StringTypeDeclaration) uriParamDeclaration).pattern();
//                if (uriPattern != null) {
//                    simplePattern = false;
//                    pattern = uriPattern;
//                }
//            }
//            ramlPath = ramlPath.replace("{" + uriParamDeclaration.name() + "}", pattern).replace("^", "").replace("$", "");
//        }
//
//
//        if (simplePattern && ramlPath.contains("{") && ramlPath.contains("}")) {
//            LOG.warn("Resource path contains unspecified uri parameter: {}", ramlPath);
//            ratpackPath = Pattern.compile("\\{([^}]*)\\}").matcher(ramlPath).replaceAll(directoryPattern);
//        } else {
//            ratpackPath = ramlPath;
//        }
//
//        return "::" + ratpackPath;
//    }


    static class Route implements Handler {
        private final static Logger LOG = LoggerFactory.getLogger(Route.class);
        private static final String MODE_HEADER = "X-Vrap-Mode";

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
                    Handlers.when(isMode(VrapMode.proxy),
                            Handlers.chain(
                                    requestProxyHandler,
                                    receivedResponseValidationHandler,
                                    receivedResponseForwardHandler))
//                    ,
//                    Handlers.when(isMode(VrapMode.example), requestExampleHandler)
            );
            this.delegate = Handlers.register(registry, chain);
        }

        @Override
        public void handle(final Context ctx) throws Exception {
            final String path = ctx.getRequest().getPath();
            LOG.debug("Request path: {}", path);

            delegate.handle(ctx);
        }

        private Predicate<Context> isMode(final VrapMode mode) {
            return ctx -> mode(ctx) == mode;
        }

        private VrapMode mode(final Context ctx) {
            final Headers headers = ctx.getRequest().getHeaders();
            final VrapApp.VrapOptions options = ctx.get(VrapApp.VrapOptions.class);

            return VrapMode.parse(headers.get(MODE_HEADER)).orElse(options.getMode());
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

        private void validateRequest(final Context ctx, final TypedData body) throws Exception {
            final RmfValidator validator = ctx.get(RmfValidator.class);
            final Method method = ctx.get(Method.class);
            final Optional<RmfValidator.ValidationErrors> validationErrors = validator.validateRequest(ctx, body, method);
            ctx.next(Registry.of(registrySpec -> {
                registrySpec.add(TypedData.class, body);
                validationErrors.ifPresent(validationErrors1 -> {
                    final RequestBody requestBody = new RequestBody(body.getText());
                    registrySpec.add(requestBody).add(RmfValidator.ValidationErrors.class, validationErrors1);
                });
            }));
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

            final List<Body> bodTypeDeclarations = method.getResponses().stream()
                    .flatMap(r -> r.getBodies().stream()).collect(Collectors.toList());

            final Map<String, String> contentTypeToExample = bodTypeDeclarations.stream()
                    .collect(Collectors.toMap(Body::getName, Body::getName));

            ctx.byContent(byContentSpec ->
                    contentTypeToExample.entrySet().stream()
                            .forEach(e -> byContentSpec.type(e.getKey(), () -> ctx.render(e.getValue()))));
        }
    }

    private static class RequestBody {
        private String text;

        public RequestBody(String body) {
            this.text = body;
        }

        public String getText() {
            return text;
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
            final Boolean insecureSSL = ctx.get(VrapApp.VrapOptions.class).getSslVerificationMode() == SSLVerificationMode.insecure;
            final URI proxiedUri = proxiedUri(ctx);
            LOG.info("Forward to: {}", proxiedUri);

            httpClient.request(proxiedUri, proxyRequest(body, request, insecureSSL))
                    .then(receivedResponse ->
                            ctx.next(Registry.builder().add(receivedResponse).add(proxiedUri).build()));
        }

        private URI proxiedUri(final Context ctx) {
            final Api api = ctx.get(RmfModelRepository.class).getApi();

            final VrapApp.VrapOptions options = ctx.get(VrapApp.VrapOptions.class);

            final Request request = ctx.getRequest();
            final String query = request.getQuery();
            final String boundPath = request.getPath().replaceAll("^rmf/", "") + (!query.isEmpty() ? "?" + query : "");

            final String ramlBaseUri = StringUtils.stripEnd(api.getBaseUri().getTemplate(), "/");
            final String ramlBaseUriPath = URI.create(ramlBaseUri.replace("{", "%7B").replace("}", "%7D")).getPath().replace("%7B", "{").replace("%7D", "}");
            final String ramlBaseUriHost = ramlBaseUri.replace(ramlBaseUriPath, "");

            final String baseUri = options.getApiUrl().orElse(ramlBaseUriHost);


            final String uriStr = baseUri.endsWith("/") ?
                    baseUri + boundPath :
                    Joiner.on("/").join(baseUri, boundPath);


            return URI.create(uriStr);
        }

        private Action<RequestSpec> proxyRequest(final TypedData body, final Request request, final Boolean insecureSSL) {
            return spec -> {
                if (insecureSSL) {
                    spec.sslContext(SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build());
                }
                spec.getBody().buffer(body.getBuffer());
                spec.getHeaders().copy(request.getHeaders());
                spec.getHeaders().remove("Host");
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
        @SuppressWarnings("unchecked")
        public void handle(Context ctx) throws Exception {
            final ReceivedResponse receivedResponse = ctx.get(ReceivedResponse.class);
            final RmfValidator validator = ctx.get(RmfValidator.class);
            final Boolean dryRun = ctx.get(VrapApp.VrapOptions.class).getDryRun();
            final Method method = ctx.get(Method.class);
            final Optional<RmfValidator.ValidationErrors> receivedResponseErrors = validator.validateReceivedResponse(ctx, receivedResponse, method);

            Optional<RmfValidator.ValidationErrors> requestValidationErrors;
            try {
                requestValidationErrors = Optional.of(ctx.get(RmfValidator.ValidationErrors.class));
            } catch (NotInRegistryException e) {
                requestValidationErrors = Optional.empty();
            }

            if (requestValidationErrors.isPresent() && !dryRun) {
                Integer statusCode = receivedResponse.getStatusCode();
                if (statusCode < 400 || statusCode > 499) {
                    ctx.getResponse().status(VrapStatus.INVALID_REQUEST);
                    RequestBody requestBody = ctx.get(RequestBody.class);
                    RmfValidator.ValidationErrors errors = new RmfValidator.ValidationErrors(
                            requestValidationErrors.get().getErrors(),
                            receivedResponse.getStatusCode(),
                            receivedResponse.getBody().getText(),
                            requestBody.getText());
                    ctx.render(json(errors));
                    return;
                }
            }
            if (receivedResponseErrors.isPresent() && !dryRun) {
                ctx.getResponse().status(VrapStatus.INVALID_RESPONSE);
                ctx.render(json(receivedResponseErrors.get()));
            } else {
                ctx.next();
            }
        }
    }
}
