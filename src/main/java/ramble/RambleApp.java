package ramble;

import org.raml.v2.api.RamlModelBuilder;
import org.raml.v2.api.RamlModelResult;
import org.raml.v2.api.model.common.ValidationResult;
import org.raml.v2.api.model.v10.api.Api;
import org.raml.v2.api.model.v10.bodies.Response;
import org.raml.v2.api.model.v10.datamodel.ExampleSpec;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;
import org.raml.v2.api.model.v10.methods.Method;
import org.raml.v2.api.model.v10.resources.Resource;
import org.raml.v2.internal.impl.commons.nodes.BodyNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.http.HttpMethod;
import ratpack.http.Request;
import ratpack.server.RatpackServer;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ratpack.jackson.Jackson.json;


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
            }
        } else {
            LOG.error("Resource for {} {} not found.", method, path);
            ctx.next();
        }

    }
}

public class RambleApp {
    private static Logger LOG = LoggerFactory.getLogger(RambleApp.class);

    public static void main(String[] args) throws Exception {
        final RamlModelResult ramlModelResult = new RamlModelBuilder().buildApi("./Example.raml");
        if (ramlModelResult.hasErrors()) {
            for (ValidationResult validationResult : ramlModelResult.getValidationResults()) {
                LOG.error(validationResult.getMessage());
            }
        } else {
            final Api api = ramlModelResult.getApiV10();

            RatpackServer.start(server -> server
                    .handlers(chain -> chain.all(new ResourcesHandler(api)))
            );
        }

    }
}
