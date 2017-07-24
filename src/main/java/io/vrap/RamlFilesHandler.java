package io.vrap;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.raml.v2.api.model.v10.api.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.func.Predicate;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.handling.Handlers;
import ratpack.handling.RequestLogger;
import ratpack.http.MediaType;
import ratpack.http.Response;
import ratpack.http.internal.MimeParse;
import ratpack.registry.Registry;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static ratpack.handlebars.Template.handlebarsTemplate;

/**
 * Serves raml files from the given base dir.
 */
class RamlFilesHandler {
    private final static Logger LOG = LoggerFactory.getLogger(RamlFilesHandler.class);

    private final Handler handler;

    public RamlFilesHandler(final FileContentModifier contentModifier) {
        handler = new RamlHandler(contentModifier);
    }

    public Handler getHandler() {
        return handler;
    }

    private static class RamlHandler implements Handler {
        private final static Logger LOG = LoggerFactory.getLogger(RamlRouter.Route.class);
        public static final Pattern jsonFile = Pattern.compile("json$");

        private final Handler delegate;

        public RamlHandler(FileContentModifier contentModifier) {
            final Registry registry = Registry.builder().add("get").build();
            final Handler chain = Handlers.chain(
                    RequestLogger.ncsa(LOG),
                    Handlers.when(
                            isJsonFile(),
                            new JsonFileHandler(contentModifier)
                    ),
                    new RamlFileHandler(contentModifier)
            );
            this.delegate = Handlers.register(registry, chain);
        }

        @Override
        public void handle(Context ctx) throws Exception {
            final String path = ctx.getRequest().getPath();
            LOG.debug("Request path: {}", path);

            delegate.handle(ctx);
        }

        private Predicate<Context> isJsonFile() {
            return this::jsonFile;
        }

        private Boolean jsonFile(final Context ctx) {
            final String path = ctx.getRequest().getPath();

            return jsonFile.matcher(path).find();
        }
    }

    private static class JsonFileHandler extends FileHandler {

        public JsonFileHandler(FileContentModifier contentModifier) {
            super(contentModifier);
        }

        @Override
        public void handle(Context ctx) throws Exception {
            final RamlModelRepository ramlModelRepository = ctx.get(RamlModelRepository.class);
            final Path filePath = ramlModelRepository.getFilePath();
            final Path parent = ramlModelRepository.getParent();
            final String path = ctx.getPathBinding().getPastBinding();
            final Path resolvedFilePath = path.isEmpty() ? filePath : parent.resolve(path).normalize();

            final String content = new BaseUriReplacer().preprocess(ctx, resolvedFilePath).toString();
            ctx.byContent(byContentSpec -> byContentSpec
                    .json(() -> renderReplacedContent(ctx, content))
                    .noMatch("application/json"));
        }
    }

    private static class RamlFileHandler extends FileHandler {

        public RamlFileHandler(FileContentModifier contentModifier) {
            super(contentModifier);
        }

        @Override
        public void handle(Context ctx) throws Exception {
            final RamlModelRepository ramlModelRepository = ctx.get(RamlModelRepository.class);
            final Path filePath = ramlModelRepository.getFilePath();
            final Path parent = ramlModelRepository.getParent();
            final String path = ctx.getPathBinding().getPastBinding();

            final Path resolvedFilePath = path.isEmpty() ? filePath : parent.resolve(path).normalize();
            final File file = resolvedFilePath.toFile();
            if (file.exists()) {
                final String content;
                if (QueryParams.resolveIncludes(ctx)) {
                    content = new IncludeResolver().preprocess(resolvedFilePath).toString();
                } else {
                    content = Files.asByteSource(file).asCharSource(Charsets.UTF_8).read();
                }
                ctx.byContent(byContentSpec -> byContentSpec
                        .type("application/raml+yaml", () -> renderReplacedContent(ctx, content))
                        .html(() -> renderHtml(ctx, path, content))
                        .noMatch("application/raml+yaml"));
            } else {
                ctx.byContent(byContentSpec -> byContentSpec.noMatch(() -> ctx.render(ctx.file("api-raml/" + path))));
            }
        }

        private void renderHtml(final Context ctx, final String fileName, final String content) {
            final RamlModelRepository ramlModelRepository = ctx.get(RamlModelRepository.class);
            final Api api = ramlModelRepository.getApi();
            final Integer port = ctx.getServerConfig().getPort();

            String contentWithIncludeLinks = content.replaceAll("(!include\\s*)(\\S*)", "$1<a class=\"hljs-string\" href=\"$2\">$2</a>");
            final ImmutableMap<String, String> model =
                    ImmutableMap.of("fileName", fileName,
                            "fileContent", contentWithIncludeLinks,
                            "apiTitle", api.title().value(),
                            "proxyUri", "http://localhost:" + port.toString());
            ctx.render(handlebarsTemplate(model, "api-raml/raml.html"));
        }
    }

    abstract private static class FileHandler implements Handler {

        private final FileContentModifier contentModifier;

        public FileHandler(final FileContentModifier contentModifier) {
            this.contentModifier = contentModifier;
        }

        protected void renderReplacedContent(final Context ctx, final String content) {
            final String replacedContent = contentModifier.apply(content);
            final String acceptHeader = ctx.getRequest().getHeaders().get(HttpHeaderNames.ACCEPT);
            final List<String> contentTypes = Arrays.asList(MediaType.APPLICATION_JSON, "application/raml+yaml", MediaType.PLAIN_TEXT_UTF8);
            final String contentType = MimeParse.bestMatch(contentTypes, acceptHeader);
            final Response response = ctx.getResponse();
            response.send(contentType, replacedContent);
        }
    }
}
