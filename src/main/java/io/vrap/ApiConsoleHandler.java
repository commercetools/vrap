package io.vrap;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.path.PathBinding;

import static ratpack.handlebars.Template.handlebarsTemplate;

/**
 * Handler for the api-console integration.
 */
class ApiConsoleHandler implements Handler {
    private final static Logger LOG = LoggerFactory.getLogger(ApiConsoleHandler.class);

    @Override
    public void handle(final Context ctx) throws Exception {
        final PathBinding pathBinding = ctx.getPathBinding();
        final String path = pathBinding.getPastBinding();

        if (path.isEmpty() || path.equals("index.html")) {
            final String queryParams = QueryParams.queryParams(ctx);
            final Integer port = ctx.getServerConfig().getPort();
            final ImmutableMap<String, Object> model =
                    ImmutableMap.of(
                            "ramlPath", "api-raml/Vrap-Extension.raml",
                            "queryParams", queryParams,
                            "proxyHost", "localhost",
                            "proxyPort", port
                    );
            ctx.render(handlebarsTemplate(model, "api-console/index.html"));
        }
        else  {
            ctx.next();
        }
    }
}
