package ramble;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.func.Action;
import ratpack.guice.Guice;
import ratpack.handlebars.HandlebarsModule;
import ratpack.handling.Handlers;
import ratpack.server.RatpackServer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
        final Path filePath = Paths.get(args[0]).toAbsolutePath();
        final Path fileName = filePath.getFileName();

        final FileContentModifier contentModifier = new FileContentModifier(fileName.toString());
        final List<Path> watchFiles = new IncludeCollector(filePath).collect();
        watchFiles.add(filePath);

        RatpackServer.start(server -> server
                .serverConfig(c -> c.findBaseDir())
                .registry(Guice.registry(b -> b.module(HandlebarsModule.class)
                        .bindInstance(RamlModelRepository.of(filePath))
                        .bindInstance(FileWatcher.of(filePath.getParent(), watchFiles))
                        .bind(Validator.class)))
                .handlers(chain -> chain.get(ctx -> ctx.render(handlebarsTemplate("index.html")))
                        .prefix("api-console", chain1 ->
                                chain1.all(ctx -> ctx.insert(
                                        new ApiConsoleHandler(filePath),
                                        new WebJarHandler("api-console", "3.0.4"),
                                        new WebJarHandler("livereload-js", "2.2.2"),
                                        Handlers.files(ctx.getServerConfig(), Action.noop()))))
                        .prefix("api", chain1 -> chain1.all(new RamlRouter()))
                        .prefix("api-raml", chain1 ->
                                chain1.all(ctx -> ctx.insert(
                                        new RambleExtensionHandler(),
                                        new RamlFilesHandler(contentModifier))))));
                        // .get("livereload", ctx -> WebSockets.websocket(ctx, new LivereloadHandler(ctx, filePath)))));
    }
}
