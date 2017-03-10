package ramble;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import ratpack.file.MimeTypes;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.path.PathBinding;

import java.net.URL;
import java.nio.file.Path;

import static ratpack.handlebars.Template.handlebarsTemplate;

/**
 * Handler for the api-console integration.
 */
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
            ctx.render(handlebarsTemplate(ImmutableMap.of("fileName", fileName), "api-console/index.html"));
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
