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

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

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
        final Path baseRamlDir = filePath.getParent();

        final RamlModelResult ramlModelResult = new RamlModelBuilder().buildApi(filePath.toFile());
        if (ramlModelResult.hasErrors()) {
            for (ValidationResult validationResult : ramlModelResult.getValidationResults()) {
                LOG.error("{}", validationResult.toString());
            }
        } else {
            final Api api = ramlModelResult.getApiV10();

            RatpackServer.start(server -> server
                    .serverConfig(c -> c.findBaseDir())
                    .registry(Guice.registry(b -> b.module(HandlebarsModule.class)))
                    .handlers(chain -> chain.get(ctx -> ctx.render(handlebarsTemplate(ImmutableMap.of("fileName", fileName), "index.html")))
                                            .prefix("api-console", chain1 -> chain1.all(new ApiConsoleHandler(fileName)))
                                            .prefix("api", new RamlRouter(api))
                                            .prefix("raml", chain1 -> chain1.all(new RamlFilesHandler(baseRamlDir)))));
        }

    }
}
