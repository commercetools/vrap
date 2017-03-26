package ramble;

import com.google.common.base.Joiner;
import org.raml.v2.api.model.common.ValidationResult;
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
import ratpack.path.PathTokens;

import java.net.URI;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static ratpack.jackson.Jackson.json;

/**
 * This class routes request for raml resource to a raml route.
 */
class RamlRouter implements Handler {
    private final static Logger LOG = LoggerFactory.getLogger(RamlRouter.class);

    @Override
    public void handle(final Context ctx) throws Exception {
        final RamlModelRepository ramlModelRepository = ctx.get(RamlModelRepository.class);
        final Api api = ramlModelRepository.getApi();
        final List<Handler> routes = createRoutes(api);
        ctx.insert(routes.toArray(new Handler[routes.size()]));
    }

    private static List<Handler> createRoutes(final Api api) {
        return createRoutes(api, api.resources());
    }

    private static List<Handler> createRoutes(final Api api, final List<Resource> resources) {
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

    enum ValidationKind {
        uriParameter,
        queryParameter,
        body,
        response;
    }

    static class ValidationError {
        private final ValidationKind kind;
        private final String context;
        private final String message;

        public ValidationError(final ValidationKind kind, final String context, final String message) {
            this.kind = kind;
            this.context = context;
            this.message = message;
        }

        public ValidationKind getKind() {
            return kind;
        }

        public String getContext() {
            return context;
        }

        public String getMessage() {
            return message;
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

            final List<ValidationError> validationErrors = validateRequest(ctx);
            if (validationErrors.isEmpty()) {
                switch (mode(ctx)) {
                    case proxy:
                        proxyRequest(ctx, request);
                        break;
                    case example:
                        sendExample(ctx, method);
                        break;
                }
            } else {
                ctx.getResponse().status(400);
                ctx.render(json(validationErrors));
            }
        }

        private List<ValidationError> validateRequest(final Context context) {
            final List<ValidationError> errors = new ArrayList<>();

            errors.addAll(validateUriParameters(context.getAllPathTokens()));
            errors.addAll(validateQueryParameters(context.getRequest()));

            return errors;
        }

        private List<ValidationError> validateUriParameters(final PathTokens allPathTokens) {
            final List<ValidationError> validationResults = new ArrayList<>();

            final List<TypeDeclaration> requiredUriParameters = resource.uriParameters();

            for (final TypeDeclaration uriParameter : requiredUriParameters) {
                final String name = uriParameter.name();
                if (allPathTokens.containsKey(name)) {
                    final List<ValidationResult> validate = uriParameter.validate(allPathTokens.get(name));
                    List<ValidationError> results = validate.stream()
                            .map(r -> new ValidationError(ValidationKind.uriParameter, name, r.getMessage()))
                            .collect(Collectors.toList());
                    validationResults.addAll(results);
                } else if (uriParameter.required()) {
                    validationResults.add(new ValidationError(ValidationKind.uriParameter, uriParameter.name(), "Required uri parameter missing"));
                }
            }
            return validationResults;
        }

        private List<ValidationError> validateQueryParameters(final Request request) {
            final List<ValidationError> validationResults = new ArrayList<>();

            final Map<String, TypeDeclaration> queryParameters = method.queryParameters().stream()
                    .collect(Collectors.toMap(TypeDeclaration::name, Function.identity()));

            for (final String paramName : request.getQueryParams().keySet()) {
                if (queryParameters.containsKey(paramName)) {
                    final TypeDeclaration queryParamDeclaration = queryParameters.get(paramName);
                    for (final String paramValue : request.getQueryParams().getAll(paramName)) {
                        final List<ValidationResult> validate = queryParamDeclaration.validate(paramValue);
                        final List<ValidationError> results = validate.stream()
                                .map(r -> new ValidationError(ValidationKind.queryParameter, String.join("=", paramName, paramValue), r.getMessage()))
                                .collect(Collectors.toList());
                        validationResults.addAll(results);
                    }
                } else {
                    validationResults.add(new ValidationError(ValidationKind.queryParameter, paramName, "Unknown query parameter"));
                }
            }
            return validationResults;
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
