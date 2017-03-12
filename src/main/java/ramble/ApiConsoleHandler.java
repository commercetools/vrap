package ramble;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.handling.Context;
import ratpack.path.PathBinding;

import java.nio.file.Path;

import static ratpack.handlebars.Template.handlebarsTemplate;

/**
 * Handler for the api-console integration.
 */
class ApiConsoleHandler extends WebJarHandler {
    private final static Logger LOG = LoggerFactory.getLogger(ApiConsoleHandler.class);

    private final Path fileName;

    public ApiConsoleHandler(final Path ramlFile) {
        super("api-console", "3.0.4", "dist");
        this.fileName = ramlFile;
    }

    @Override
    public void handle(final Context ctx) throws Exception {
        final PathBinding pathBinding = ctx.getPathBinding();
        final String path = pathBinding.getPastBinding();

        if (path.isEmpty() || path.equals("index.html")) {
            ctx.render(handlebarsTemplate(ImmutableMap.of("fileName", fileName), "api-console/index.html"));
        }
        else  {
            super.handle(ctx);
        }
    }
}
