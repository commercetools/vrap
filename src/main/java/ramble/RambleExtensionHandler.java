package ramble;

import com.google.common.collect.ImmutableMap;
import org.raml.v2.api.model.v10.api.Api;
import org.raml.v2.api.model.v10.methods.Method;
import org.raml.v2.api.model.v10.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.handling.Context;
import ratpack.handling.Handler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static ratpack.handlebars.Template.handlebarsTemplate;

/**
 * Serves the ramble extension which extends the raml api specification with ramble mode headers.
 */
class RambleExtensionHandler implements Handler {
    private final static Logger LOG = LoggerFactory.getLogger(RambleExtensionHandler.class);

    private final Api api;
    private final Path ramlFile;

    public RambleExtensionHandler(final Api api, final Path ramlFile) {
        this.api = api;
        this.ramlFile = ramlFile;
    }

    @Override
    public void handle(final Context ctx) throws Exception {
        final String path = ctx.getPathBinding().getPastBinding();

        if (path.equals("Ramble-Extension.raml")) {
            List<ResourceExtension> resourceExtensions = resourceExtensions(api.resources(), "");
            final ImmutableMap<String, Object> model =
                    ImmutableMap.of("fileName", ramlFile.getFileName(),
                            "queryParams", ctx.getRequest().getQuery(),
                            "resourceExtensions", resourceExtensions);

            ctx.render(handlebarsTemplate(model, "api-raml/Ramble-Extension.raml"));
        } else {
            ctx.next();
        }
    }

    private List<ResourceExtension> resourceExtensions(final List<Resource> resources, final String currentIndent) {
        final List<ResourceExtension> result = new ArrayList<>();
        for (final Resource resource : resources) {
            final List<String> methods = resource.methods().stream().map(Method::method).collect(Collectors.toList());

            result.add(new ResourceExtension(resource.resourcePath(), methods, currentIndent));
            result.addAll(resourceExtensions(resource.resources(), currentIndent + "    "));
        }
        return result;
    }

    private static class ResourceExtension {
        private final String resourcePath;
        private final List<ResourceExtensionMethod> methods;
        private final String indent;

        public ResourceExtension(final String resourcePath, final List<String> methods, final String indent) {
            this.resourcePath = resourcePath;
            this.methods = methods.stream().map(ResourceExtensionMethod::new).collect(Collectors.toList());
            this.indent = indent;
        }

        public String getResourcePath() {
            return resourcePath;
        }

        public List<ResourceExtensionMethod> getMethods() {
            return methods;
        }

        public String getIndent() {
            return indent;
        }
    }

    private static class ResourceExtensionMethod {
        private final String method;

        public ResourceExtensionMethod(final String method) {
            this.method = method;
        }

        public String getMethod() {
            return method;
        }
    }
}
