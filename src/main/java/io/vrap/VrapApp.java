package io.vrap;

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
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.commons.cli.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import static ratpack.handlebars.Template.handlebarsTemplate;

/**
 * The vrap app.
 */
public class VrapApp {
    private static Logger LOG = LoggerFactory.getLogger(VrapApp.class);

    public static void main(String[] args) throws Exception {

        final VrapOptions options = new VrapOptions(args);

        if (options.getDuplicateDetection()) {
            System.setProperty("yagi.json_duplicate_keys_detection", options.getDuplicateDetection().toString());
        }
        final Path filePath = options.getFilePath();
        final Path fileName = filePath.getFileName();

        if (options.getSslVerificationMode().equals(SSLVerificationMode.insecure)) {
            SSLContext sslContext = SSLContext.getInstance("SSL");
            javax.net.ssl.TrustManager[] trustManagers = {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }
                        public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                        }
                        public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                        }
                    }
            };
            sslContext.init(null, trustManagers, new SecureRandom());
            SSLContext.setDefault(sslContext);
        }

        final RamlModelRepository ramlRepo = RamlModelRepository.of(filePath);
        final FileContentModifier contentModifier = new FileContentModifier(fileName.toString());
        final List<Path> watchFiles = new IncludeCollector(filePath).collect();
        watchFiles.add(filePath);

        RatpackServer.start(server -> server
                .serverConfig(c -> {
                    c.findBaseDir();
                    c.port(options.getPort());
                })
                .registry(Guice.registry(b -> b.module(HandlebarsModule.class)
                        .bindInstance(options)
                        .bindInstance(ramlRepo)
                        .bindInstance(HttpClient.class, HttpClient.of(httpClientSpec -> httpClientSpec.poolSize(options.getClientConnectionPoolSize())))
                        .bind(Validator.class)))
                .handlers(chain -> chain.get(ctx -> ctx.render(handlebarsTemplate("index.html")))
                        .prefix("api-console", chain1 ->
                                chain1.all(ctx -> ctx.insert(
                                        new ApiConsoleHandler(),
                                        new WebJarHandler("api-console", "3.0.4"),
                                        Handlers.files(ctx.getServerConfig(), Action.noop()))))
                        .prefix("api", chain1 -> chain1.all(new RamlRouter(ramlRepo.getApi()).getRoutes()))
                        .prefix("api-raml", chain1 ->
                                chain1.all(ctx -> ctx.insert(
                                        new VrapExtensionHandler(),
                                        new RamlFilesHandler(contentModifier))))));
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

            return options;
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
    }
}
