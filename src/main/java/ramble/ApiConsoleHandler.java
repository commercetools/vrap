package ramble;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.path.PathBinding;

import java.nio.file.Path;

import static ratpack.handlebars.Template.handlebarsTemplate;

/**
 * Handler for the api-console integration.
 */
class ApiConsoleHandler implements Handler {
    private final static Logger LOG = LoggerFactory.getLogger(ApiConsoleHandler.class);

    private final Path fileName;

    public ApiConsoleHandler(final Path ramlFile) {
        this.fileName = ramlFile;
    }

    @Override
    public void handle(final Context ctx) throws Exception {
        final PathBinding pathBinding = ctx.getPathBinding();
        final String path = pathBinding.getPastBinding();

        if (path.isEmpty() || path.equals("index.html")) {
            final String queryParams = QueryParams.queryParams(ctx);
            final ImmutableMap<String, Object> model =
                    ImmutableMap.of("ramlPath", "api-raml/" + fileName.toString(), "queryParams", queryParams);
            ctx.render(handlebarsTemplate(model, "api-console/index.html"));
        }
        else  {
            ctx.next();
        }
    }
}
