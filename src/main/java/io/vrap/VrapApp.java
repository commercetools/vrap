package io.vrap;

import io.vrap.reflection.ApiHandler;
import io.vrap.reflection.ResourceHandler;
import io.vrap.reflection.ResourceSearchHandler;
import io.vrap.reflection.TypeDeclarationHandler;
import org.apache.commons.lang.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.func.Action;
import ratpack.guice.Guice;
import ratpack.handlebars.HandlebarsModule;
import ratpack.handling.Handlers;
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

        final Path filePath = options.getFilePath();
        final Path fileName = filePath.getFileName();

        if (Optional.ofNullable(System.getenv("DISABLE_SSL_VERIFICATION")).orElse("false").equals("true")) {
            LOG.info("disable SSL");
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
                        .bind(Validator.class)))
                .handlers(chain -> chain.get(ctx -> ctx.render(handlebarsTemplate("index.html")))
                        .prefix("api-console", chain1 ->
                                chain1.all(ctx -> ctx.insert(
                                        new ApiConsoleHandler(),
                                        new WebJarHandler("api-console", "3.0.4"),
                                        Handlers.files(ctx.getServerConfig(), Action.noop()))))
                        .prefix("api", chain1 -> chain1.all(new RamlRouter(ramlRepo.getApi()).getRoutes()))
                        .prefix("reflection", chain1 -> chain1
                                .get("", new ApiHandler(options))
                                .prefix("search", chain2 -> chain2.all(new ResourceSearchHandler()))
                                .prefix("resources", chain2 -> chain2.all(new ResourceHandler()))
                                .prefix("type-declarations", chain2 -> chain2.all(new TypeDeclarationHandler())))
                        .prefix("api-raml", chain1 ->
                                chain1.all(ctx -> ctx.insert(
                                        new VrapExtensionHandler(),
                                        new RamlFilesHandler(contentModifier))))));
    }

    public static class VrapOptions {
        private Path filePath;
        private VrapMode mode;
        private int port;
        private final Options options;
        private String apiUrl;

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

            mode = parseModeOption(cmd.getOptionValue(getModeOption().getOpt(), VrapMode.proxy.name()));
            port = NumberUtils.toInt(cmd.getOptionValue(getPortOption().getOpt()), 5050);
            apiUrl = cmd.getOptionValue(getApiUrlOption().getOpt());

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

            return options;
        }

        private Option getModeOption()
        {
            return Option.builder("m")
                    .argName("mode")
                    .desc("vrap mode: " + Arrays.toString(VrapMode.values()))
                    .hasArg(true)
                    .required(false)
                    .build();
        }

        private Option getApiUrlOption()
        {
            return Option.builder("a")
                    .argName("api")
                    .desc("URI to proxy to")
                    .hasArg(true)
                    .required(false)
                    .build();
        }

        private Option getPortOption()
        {
            return Option.builder("p")
                    .argName("port")
                    .desc("port to listen for requests")
                    .hasArg(true)
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
    }
}
