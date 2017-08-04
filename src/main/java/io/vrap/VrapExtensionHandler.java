package io.vrap;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import org.raml.v2.api.model.v10.api.Api;
import org.raml.v2.api.model.v10.methods.Method;
import org.raml.v2.api.model.v10.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.handling.Context;
import ratpack.handling.Handler;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static ratpack.handlebars.Template.handlebarsTemplate;

/**
 * Serves the vrap extension which extends the raml api specification with vrap mode headers.
 */
class VrapExtensionHandler implements Handler {
    private final static Logger LOG = LoggerFactory.getLogger(VrapExtensionHandler.class);

    private final String apiUri;

    public VrapExtensionHandler(final String apiUri) {
        this.apiUri = apiUri;
    }

    @Override
    public void handle(final Context ctx) throws Exception {
        final RamlModelRepository ramlModelRepository = ctx.get(RamlModelRepository.class);
        final Api api = ramlModelRepository.getApi();
        final String path = ctx.getPathBinding().getPastBinding();

        if (path.equals("Vrap-Extension.raml")) {
            final Path filePath = ramlModelRepository.getFilePath();
            List<ResourceExtension> resourceExtensions = resourceExtensions(api.resources(), "");
            final Integer port = ctx.getServerConfig().getPort();
            final String proxyUri = "http://localhost:" + port.toString() + "/" + apiUri + new URI(api.baseUri().value().replace("{", "%7B").replace("}", "%7D")).getPath().replace("%7B", "{").replace("%7D", "}");
            final ImmutableMap<String, Object> model =
                    ImmutableMap.<String, Object>builder()
                            .put("fileName", filePath.getFileName())
                            .put("queryParams", ctx.getRequest().getQuery())
                            .put("resourceExtensions", resourceExtensions)
                            .put("proxyUri", proxyUri)
                            .put("modes", Joiner.on(", ").join(VrapMode.values()))
                            .put("flags", Joiner.on(", ").join(ValidationFlag.values()))
                    .build();

            ctx.byContent(byContentSpec -> byContentSpec
                    .type("application/raml+yaml", () -> ctx.render(handlebarsTemplate(model, "api-raml/Vrap-Extension.raml")))
                    .html(() -> ctx.render(handlebarsTemplate(model, "api-raml/Vrap-Extension.html")))
                    .noMatch("application/raml+yaml"));
        } else {
            ctx.next();
        }
    }

    private List<ResourceExtension> resourceExtensions(final List<Resource> resources, final String currentIndent) {
        final List<ResourceExtension> result = new ArrayList<>();
        for (final Resource resource : resources) {
            final List<String> methods = resource.methods().stream().map(Method::method).collect(Collectors.toList());

            result.add(new ResourceExtension(resource.relativeUri().value(), methods, currentIndent));
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
