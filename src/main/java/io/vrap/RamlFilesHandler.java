package io.vrap;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.raml.v2.api.model.v10.api.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.http.MediaType;
import ratpack.http.Response;
import ratpack.http.internal.MimeParse;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static ratpack.handlebars.Template.handlebarsTemplate;

/**
 * Serves raml files from the given base dir.
 */
class RamlFilesHandler implements Handler {
    private final static Logger LOG = LoggerFactory.getLogger(RamlFilesHandler.class);

    private final FileContentModifier contentModifier;

    public RamlFilesHandler(final FileContentModifier contentModifier) {
        this.contentModifier = contentModifier;
    }

    @Override
    public void handle(final Context ctx) throws Exception {
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
                    .html(() -> renderHtml(ctx, path, content))
                    .noMatch(() -> renderReplacedContent(ctx, content)));
        } else {
            ctx.byContent(byContentSpec -> byContentSpec.noMatch(() -> ctx.render(ctx.file("api-raml/" + path))));
        }
    }

    private void renderReplacedContent(final Context ctx, final String content) {
        final String replacedContent = contentModifier.apply(content);
        final String acceptHeader = ctx.getRequest().getHeaders().get(HttpHeaderNames.ACCEPT);
        final List<String> contentTypes = Arrays.asList(MediaType.APPLICATION_JSON, "application/raml+yaml", MediaType.PLAIN_TEXT_UTF8);
        final String contentType = MimeParse.bestMatch(contentTypes, acceptHeader);
        final Response response = ctx.getResponse();
        response.send(contentType, replacedContent);
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
                        "rambleUri", "http://localhost:" + port.toString());
        ctx.render(handlebarsTemplate(model, "api-raml/raml.html"));
    }
}
