package ramble;

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
import ratpack.http.Request;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

            chain.path(ratpackPath, new Route(resource));

            createRoutes(chain, api, resource.resources());
        }
        return chain;
    }

    static class Route implements Handler {
        private final static Logger LOG = LoggerFactory.getLogger(Route.class);

        private final Resource resource;

        public Route(final Resource resource) {
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
                Optional<Response> response = ramlMethod.get().responses().stream().findFirst();
                Optional<ExampleSpec> example = response.flatMap(r -> r.body().stream().findFirst()).map(TypeDeclaration::example);

                if (example.isPresent()) {
                    ctx.render(example.get().value());
                } else {
                    ctx.getResponse().send("No example found.");
                }
            } else {
                ctx.next();
            }
        }
    }
}
