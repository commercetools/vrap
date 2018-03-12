package io.vrap;

import org.apache.commons.cli.*;
import org.apache.commons.lang.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.func.Action;
import ratpack.guice.Guice;
import ratpack.handlebars.HandlebarsModule;
import ratpack.handling.Handlers;
import ratpack.http.client.HttpClient;
import ratpack.server.RatpackServer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static ratpack.handlebars.Template.handlebarsTemplate;

/**
 * The vrap app.
 */
public class VrapApp {
    public static final String API_URI = "api";
    public static final String RMF_URI = "rmf";
    public static final String PARSER_RMF = "rmf";
    public static final String PARSER_RAML = "raml";
    public static final String API_RAML = "api-" + PARSER_RAML;
    public static final String API_RMF = "api-" + PARSER_RMF;

    private static Logger LOG = LoggerFactory.getLogger(VrapApp.class);

    public static void main(String[] args) throws Exception {

        final VrapOptions options = new VrapOptions(args);

        if (options.getDuplicateDetection()) {
            System.setProperty("yagi.json_duplicate_keys_detection", options.getDuplicateDetection().toString());
        }
        final Path filePath = options.getFilePath();
        final Path fileName = filePath.getFileName();

        final FileContentModifier contentModifier = new FileContentModifier(fileName.toString());
        final List<Path> watchFiles = new IncludeCollector(filePath).collect();
        watchFiles.add(filePath);

        if (options.getCheckOnly()) {
            RamlModelRepository.of(filePath).getApi();
            System.exit(0);
        }
        if (options.getRmfCheckOnly()) {
            RmfModelRepository.of(filePath, true);
            System.exit(0);
        }

        final RamlModelRepository ramlRepo = RamlModelRepository.of(filePath);
        final RmfModelRepository rmfRepo = RmfModelRepository.of(filePath);

        RatpackServer.start(server -> server
                .serverConfig(c -> {
                    c.findBaseDir();
                    c.port(options.getPort());
                })
                .registry(Guice.registry(b -> b.module(HandlebarsModule.class)
                        .bindInstance(options)
                        .bindInstance(ramlRepo)
                        .bindInstance(rmfRepo)
                        .bindInstance(HttpClient.class, HttpClient.of(httpClientSpec -> httpClientSpec.poolSize(options.getClientConnectionPoolSize())))
                        .bind(Validator.class)
                        .bind(RmfValidator.class)
                ))
                .handlers(chain -> chain.get(ctx -> ctx.render(handlebarsTemplate("index.html")))
                        .prefix("rmf-console4", chain1 ->
                                chain1.all(ctx -> ctx.insert(
                                        new ApiConsoleHandler("console4", filePath, API_RMF),
                                        Handlers.files(ctx.getServerConfig(), Action.noop()))))
                        .prefix("console4", chain1 ->
                                chain1.all(ctx -> ctx.insert(
                                        new ApiConsoleHandler("console4", filePath, API_RAML),
                                        Handlers.files(ctx.getServerConfig(), Action.noop()))))
                        .prefix("api-console", chain1 ->
                                chain1.all(ctx -> ctx.insert(
                                        new ApiConsoleHandler("api-console", filePath, API_RAML),
                                        new WebJarHandler("api-console", "3.0.4"),
                                        Handlers.files(ctx.getServerConfig(), Action.noop()))))
                        .prefix(API_URI, chain1 -> chain1.all(new RamlRouter(ramlRepo.getApi()).getRoutes()))
                        .prefix(RMF_URI, chain1 -> chain1.all(new RmfRouter(rmfRepo.getApi()).getRoutes()))
                        .prefix("auth", chain1 -> chain1.all(new AuthRouter(ramlRepo.getApi()).getRoutes()))
                        .prefix(API_RAML, chain1 ->
                                chain1.all(ctx -> ctx.insert(
                                        new VrapExtensionHandler(API_URI),
                                        new RamlFilesHandler(contentModifier, API_RAML, API_URI).getHandler()))
                        )
                        .prefix(API_RMF, chain1 ->
                                chain1.all(ctx -> ctx.insert(
                                        new VrapExtensionHandler(RMF_URI),
                                        new RamlFilesHandler(contentModifier, API_RMF, RMF_URI).getHandler()))
                        )
                )
        );
    }

    static class VrapOptions {
        private Path filePath;
        private VrapMode mode;
        private int port;
        private final Options options;
        private String apiUrl;
        private Boolean duplicateDetection;
        private SSLVerificationMode sslVerificationMode;
        private int clientConnectionPoolSize;
        private Boolean dryRun;
        private Boolean checkOnly;
        private Boolean rmfCheckOnly;

        public VrapOptions(String[] args)
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

            dryRun = cmd.hasOption(getDryRunOption().getOpt());
            checkOnly = cmd.hasOption(getCheckOnlyOption().getOpt());
            rmfCheckOnly = cmd.hasOption(getRmfCheckOnlyOption().getOpt());
            mode = parseModeOption(cmd.getOptionValue(getModeOption().getOpt(), VrapMode.proxy.name()));
            port = NumberUtils.toInt(cmd.getOptionValue(getPortOption().getOpt()), 5050);
            apiUrl = cmd.getOptionValue(getApiUrlOption().getOpt());
            duplicateDetection = Boolean.valueOf(
                    Optional.ofNullable(cmd.getOptionValue(getJsonDuplicateKeyOption().getOpt())).orElse("true")
            );
            sslVerificationMode = parseSslMode(cmd.getOptionValue(getSSLVerificationOption().getOpt(), SSLVerificationMode.normal.name()));
            clientConnectionPoolSize = NumberUtils.toInt(cmd.getOptionValue(getClientConnectionPoolSizeOption().getOpt()), 10);

            if (cmd.hasOption(getHelpOption().getOpt())) {
                printHelp();
                System.exit(0);
            }
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
            options.addOption(getApiUrlOption());
            options.addOption(getPortOption());
            options.addOption(getJsonDuplicateKeyOption());
            options.addOption(getSSLVerificationOption());
            options.addOption(getClientConnectionPoolSizeOption());
            options.addOption(getHelpOption());
            options.addOption(getDryRunOption());
            options.addOption(getCheckOnlyOption());
            options.addOption(getRmfCheckOnlyOption());
            return options;
        }

        private Option getCheckOnlyOption()
        {
            return Option.builder("c")
                    .longOpt("checkOnly")
                    .desc("Check given raml file for errors")
                    .hasArg(false)
                    .required(false)
                    .build();
        }

        private Option getRmfCheckOnlyOption()
        {
            return Option.builder("rc")
                    .longOpt("rmfCheckOnly")
                    .desc("Check given raml file for errors using RMF")
                    .hasArg(false)
                    .required(false)
                    .build();
        }

        private Option getModeOption()
        {
            return Option.builder("m")
                    .longOpt("mode")
                    .argName("mode")
                    .desc("vrap mode: " + Arrays.toString(VrapMode.values()))
                    .hasArg(true)
                    .required(false)
                    .build();
        }

        private Option getApiUrlOption()
        {
            return Option.builder("a")
                    .longOpt("api")
                    .argName("api")
                    .desc("URI to proxy to")
                    .hasArg(true)
                    .required(false)
                    .build();
        }

        private Option getPortOption()
        {
            return Option.builder("p")
                    .longOpt("port")
                    .argName("port")
                    .desc("port to listen for requests")
                    .hasArg(true)
                    .required(false)
                    .build();
        }

        private Option getJsonDuplicateKeyOption()
        {
            return Option.builder("d")
                    .longOpt("duplicate-detection")
                    .argName("bool")
                    .desc("Enable duplicate key detection")
                    .hasArg(true)
                    .required(false)
                    .build();
        }

        private Option getSSLVerificationOption()
        {
            return Option.builder("ssl")
                    .argName("mode")
                    .desc("SSL verification mode: " + Arrays.toString(SSLVerificationMode.values()))
                    .hasArg(true)
                    .required(false)
                    .build();
        }

        private Option getClientConnectionPoolSizeOption() {
            return Option.builder("s")
                    .longOpt("pool-size")
                    .argName("pool-size")
                    .desc("Size of the http client connection pool")
                    .hasArg(true)
                    .required(false)
                    .build();
        }

        private Option getDryRunOption() {
            return Option.builder("dr")
                    .longOpt("dry-run")
                    .desc("Report errors only")
                    .hasArg(false)
                    .required(false)
                    .build();
        }

        private Option getHelpOption() {
            return Option.builder("h")
                    .longOpt("help")
                    .desc("display help")
                    .hasArg(false)
                    .required(false)
                    .build();
        }

        private void printHelp()
        {
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("vrap [OPTIONS] <file.raml>", options);
        }

        private VrapMode parseModeOption(String value)
        {
            Optional<VrapMode> mode = VrapMode.parse(value);

            if (mode.isPresent()) {
                return mode.get();
            }

            System.out.println("Unknown vrap mode: " + value);
            printHelp();
            System.exit(1);
            return null;
        }

        private SSLVerificationMode parseSslMode(String value)
        {
            Optional<SSLVerificationMode> mode = SSLVerificationMode.parse(value);

            if (mode.isPresent()) {
                return mode.get();
            }

            System.out.println("Unknown SSL verification mode: " + value);
            printHelp();
            System.exit(1);
            return null;

        }

        public SSLVerificationMode getSslVerificationMode() { return sslVerificationMode; }

        public Boolean getDuplicateDetection() { return duplicateDetection; }

        public Path getFilePath() {
            return filePath;
        }

        public VrapMode getMode() {
            return mode;
        }

        public int getPort() {
            return port;
        }

        public Optional<String> getApiUrl() {
            return Optional.ofNullable(apiUrl);
        }

        public int getClientConnectionPoolSize() {
            return clientConnectionPoolSize;
        }

        public Boolean getDryRun() { return dryRun; }

        public Boolean getCheckOnly() { return checkOnly; }

        public Boolean getRmfCheckOnly() { return rmfCheckOnly; }

    }
}
