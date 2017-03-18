package ramble;

import com.google.common.collect.ImmutableMap;
import org.raml.v2.api.RamlModelBuilder;
import org.raml.v2.api.RamlModelResult;
import org.raml.v2.api.model.common.ValidationResult;
import org.raml.v2.api.model.v10.api.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.guice.Guice;
import ratpack.handlebars.HandlebarsModule;
import ratpack.server.RatpackServer;
import ratpack.websocket.WebSockets;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import static ratpack.handlebars.Template.handlebarsTemplate;

/**
 * The ramble app.
 */
public class RambleApp {
    private static Logger LOG = LoggerFactory.getLogger(RambleApp.class);

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            LOG.error("Missing file input argument.");
            System.exit(1);
        }
        final FileSystem fileSystem = FileSystems.getDefault();
        final Path filePath = fileSystem.getPath(args[0]).toAbsolutePath();
        final Path fileName = filePath.getFileName();

        final RamlModelResult ramlModelResult = new RamlModelBuilder().buildApi(filePath.toFile());
        if (ramlModelResult.hasErrors()) {
            for (ValidationResult validationResult : ramlModelResult.getValidationResults()) {
                LOG.error("{}", validationResult.toString());
            }
        } else {
            final Api api = ramlModelResult.getApiV10();
            final Function<String, String> baseUriModifier = content -> content.replaceAll("(baseUri:\\s*)\\S*", "$1http://localhost:5050/api");
            final FileContentModifier contentModifier = new FileContentModifier(fileName.toString(), baseUriModifier);
            final List<Path> watchFiles = new IncludeCollector(filePath).collect();
            watchFiles.add(filePath);

            RatpackServer.start(server -> server
                    .serverConfig(c -> c.findBaseDir())
                    .registry(Guice.registry(b -> b.module(HandlebarsModule.class)
                            .bindInstance(FileWatcher.of(filePath.getParent(), watchFiles))))
                    .handlers(chain -> chain.get(ctx -> ctx.render(handlebarsTemplate(ImmutableMap.of("apiTitle", api.title().value()), "index.html")))
                            .prefix("api-console", chain1 -> chain1.all(
                                    ctx -> ctx.insert(new ApiConsoleHandler(filePath),
                                                      new WebJarHandler("api-console", "3.0.4"),
                                                      new WebJarHandler("livereload-js", "2.2.2"))))
                            .prefix("assets", chain1 -> chain.files())
                            .prefix("api", new RamlRouter(api))
                            .prefix("api-raml", chain1 -> chain1.all(new RamlFilesHandler(api, filePath, contentModifier)))
                            .get("livereload", ctx -> WebSockets.websocket(ctx, new LivereloadHandler(ctx, filePath)))));
        }

    }
}
