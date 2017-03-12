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
import ratpack.handling.Chain;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.http.Headers;
import ratpack.http.Request;
import ratpack.http.client.HttpClient;
import ratpack.http.client.ReceivedResponse;
import ratpack.http.client.RequestSpec;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * This class routes request for raml resource to a raml route.
 */
class RamlRouter implements Action<Chain> {
    private final Logger LOG = LoggerFactory.getLogger(RamlRouter.class);

    private final Api api;

    public RamlRouter(final Api api) {
        this.api = api;
    }

    @Override
    public void execute(final Chain chain) throws Exception {
        createRoutes(chain, api);
    }

    private Chain createRoutes(final Chain chain, final Api api) {
        return createRoutes(chain, api, api.resources());
    }

    private Chain createRoutes(final Chain chain, final Api api, final List<Resource> resources) {
        for (final Resource resource : resources) {
            final String ramlPath = resource.resourcePath().substring(1); // remove leading "/"
            final String ratpackPath = Pattern.compile("\\{(.*)\\}").matcher(ramlPath).replaceAll(":$1");

            chain.path(ratpackPath, new Route(api, resource));

            createRoutes(chain, api, resource.resources());
        }
        return chain;
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
                    case "proxy" :
                        proxyRequest(ctx, request);
                        break;
                    default:
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
            httpClient.request(uri, toRequestSpec(request)).then(sendResponse(ctx));
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

        private String mode(final Context ctx) {
            final Headers headers = ctx.getRequest().getHeaders();
            return Optional.ofNullable(headers.get(MODE_HEADER))
                    .map(String::toLowerCase)
                    .orElse("proxy");
        }

        private URI proxiedUri(final Context ctx) {
            final String boundPath = ctx.getPathBinding().getBoundTo() + ctx.getRequest().getQuery();
            final String baseUri = api.baseUri().value();
            final String uriStr = baseUri.endsWith("/") ?
                    baseUri + boundPath :
                    Joiner.on("/").join(baseUri, boundPath);
            return URI.create(uriStr);
        }

        private Action<ReceivedResponse> sendResponse(final Context ctx) {
            return response -> ctx.getResponse().send(response.getBody().getBuffer());
        }

        private Action<RequestSpec> toRequestSpec(final Request request) {
            return r -> {
                final Headers headers = request.getHeaders();
                r.getHeaders().copy(headers).remove(MODE_HEADER).add("Accept-Encoding", "gzip,deflate");
                r.method(request.getMethod());
            };
        }
    }
}
