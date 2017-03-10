package ramble;

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
import ratpack.http.Request;
import ratpack.http.Status;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hnadler for raml resources.
 */
class ResourcesHandler implements Handler {
    private final Logger LOG = LoggerFactory.getLogger(ResourcesHandler.class);

    private final Map<String, Resource> resourceByPath;

    public ResourcesHandler(final Api api) {
        this.resourceByPath = api.resources().stream()
                .collect(Collectors.toMap(r -> "api" + r.relativeUri().value(), Function.identity()));
    }

    @Override
    public void handle(final Context ctx) throws Exception {
        final Request request = ctx.getRequest();
        final String method = request.getMethod().getName().toLowerCase();
        final String path = request.getPath();
        LOG.error("Request path: {}", path);

        final Resource resource = resourceByPath.get(path);
        final Set<String> methods = resource.methods().stream().map(Method::method).collect(Collectors.toSet());
        if (resource != null && methods.contains(method)) {
            final Method ramlMethod = resource.methods().stream().filter(m -> m.method().equals(method)).findFirst().get();
            Optional<Response> response = ramlMethod.responses().stream().findFirst();
            Optional<ExampleSpec> example = response.flatMap(r -> r.body().stream().findFirst()).map(TypeDeclaration::example);

            if (example.isPresent()) {
                ctx.render(example.get().value());
            } else {
                ctx.getResponse().send("No example found.");
            }
        } else {
            LOG.error("Resource for {} {} not found.", method, path);
            ctx.next();
        }

    }
}
