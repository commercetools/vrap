package ramble;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import org.raml.v2.api.RamlModelBuilder;
import org.raml.v2.api.RamlModelResult;
import org.raml.v2.api.model.common.ValidationResult;
import org.raml.v2.api.model.v10.api.Api;
import org.raml.v2.api.model.v10.bodies.Response;
import org.raml.v2.api.model.v10.datamodel.ExampleSpec;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;
import org.raml.v2.api.model.v10.methods.Method;
import org.raml.v2.api.model.v10.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.file.MimeTypes;
import ratpack.guice.Guice;
import ratpack.handlebars.HandlebarsModule;
import ratpack.handling.Chain;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.http.Request;
import ratpack.path.PathBinding;
import ratpack.server.RatpackServer;

import java.io.File;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ratpack.handlebars.Template.handlebarsTemplate;


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

class ApiConsoleHandler implements Handler {
    private final static String apiConsoleBase = "META-INF/resources/webjars/api-console/3.0.4/dist";

    private final Path fileName;

    public ApiConsoleHandler(final Path fileName) {
        this.fileName = fileName;
    }

    @Override
    public void handle(final Context ctx) throws Exception {
        final PathBinding pathBinding = ctx.getPathBinding();

        final String path = pathBinding.getPastBinding();

        if (path.isEmpty() || path.equals("index.html")) {
            ctx.render(handlebarsTemplate(ImmutableMap.of("fileName", fileName), "index.html"));
        }
        else  {
            final String apiConsoleResourcePath = Joiner.on("/").join(apiConsoleBase, path);

            final URL resource = Resources.getResource(apiConsoleResourcePath);
            if (resource == null) {
                ctx.next();
            } else {
                final ByteSource byteSource = Resources.asByteSource(resource);
                final String content = byteSource.asCharSource(Charsets.UTF_8).read();

                final String contentType = ctx.get(MimeTypes.class).getContentType(apiConsoleResourcePath);
                ctx.getResponse().send(contentType, content);
            }
        }
    }
}

class RamlFilesHandler implements Handler {
    private final Path baseDir;

    public RamlFilesHandler(final Path baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public void handle(final Context ctx) throws Exception {
        final String path = ctx.getPathBinding().getPastBinding();

        final Path resolvedFilePath = baseDir.resolve(path);
        final File file = resolvedFilePath.toFile();
        if (file.getName().endsWith(".raml") && file.exists()) {
            final String content = Files.asByteSource(file).asCharSource(Charsets.UTF_8).read();
            ctx.getResponse().send("text/plain", content);
        } else {
            ctx.next();
        }
    }
}

public class RambleApp {
    private static Logger LOG = LoggerFactory.getLogger(RambleApp.class);

    public static void main(String[] args) throws Exception {
        final FileSystem fileSystem = FileSystems.getDefault();
        final Path ramlFile = fileSystem.getPath("Example.raml").toAbsolutePath();
        final Path baseRamlDir = ramlFile.getParent();

        final RamlModelResult ramlModelResult = new RamlModelBuilder().buildApi(ramlFile.toFile());
        if (ramlModelResult.hasErrors()) {
            for (ValidationResult validationResult : ramlModelResult.getValidationResults()) {
                LOG.error(validationResult.getMessage());
            }
        } else {
            final Api api = ramlModelResult.getApiV10();

            RatpackServer.start(server -> server
                    .serverConfig(c -> c.findBaseDir())
                    .registry(Guice.registry(b -> b.module(HandlebarsModule.class)))
                    .handlers(chain -> chain.prefix("api-console", chain1 -> chain1.all(new ApiConsoleHandler(ramlFile.getFileName())))
                                            .prefix("api", chain1 -> chain1.all(new ResourcesHandler(api)))
                                            .prefix("raml", chain1 -> chain1.all(new RamlFilesHandler(baseRamlDir)))));
        }

    }
}
