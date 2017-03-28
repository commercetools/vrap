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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.cli.*;

import static ratpack.handlebars.Template.handlebarsTemplate;

/**
 * The ramble app.
 */
public class RambleApp {
    private static Logger LOG = LoggerFactory.getLogger(RambleApp.class);

    public static void main(String[] args) throws Exception {

        final RambleOptions options = new RambleOptions(args);

        final Path filePath = options.getFilePath();
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

    private static class RambleOptions {
        private Path filePath;
        private RambleMode mode;
        private final Options options;

        public RambleOptions(String[] args)
        {
            final CommandLine cmd;
            final CommandLineParser parser = new DefaultParser();

            options = getOptions();

            try {
                cmd = parser.parse(options, args);
            } catch (ParseException e) {
                System.out.println(e.getMessage());
                printHelp();

                System.exit(1);
                return;
            }

            mode = parseModeOption(cmd.getOptionValue(getModeOption().getOpt(), RambleMode.proxy.name()));

            if (cmd.getArgs().length == 0) {
                LOG.error("Missing file input argument.");
                System.exit(1);
            }

            filePath = Paths.get(cmd.getArgs()[0]).toAbsolutePath();
        }

        private Options getOptions()
        {
            final Options options = new Options();
            options.addOption(getModeOption());

            return options;
        }

        private Option getModeOption()
        {
            return Option.builder("m")
                    .argName("mode")
                    .desc("ramble mode: " + Arrays.toString(RambleMode.values()))
                    .hasArg(true)
                    .required(false)
                    .build();
        }

        private void printHelp()
        {
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("ramble [-m <mode>] <file.raml>", options);
        }

        private RambleMode parseModeOption(String value)
        {
            final Optional<RambleMode> mode = Optional.ofNullable(value)
                    .map(o -> Stream.of(RambleMode.values())
                            .filter(m -> m.name().equals(o))
                            .findFirst()
                            .orElse(null)
                    );
            if (mode.isPresent()) {
                return mode.get();
            }

            System.out.println("Unknown ramble mode: " + value);
            printHelp();
            System.exit(1);
            return null;
        }

        public Path getFilePath() {
            return filePath;
        }

        public RambleMode getMode() {
            return mode;
        }
    }
}
