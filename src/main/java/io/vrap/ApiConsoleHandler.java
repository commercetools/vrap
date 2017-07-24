package io.vrap;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.path.PathBinding;

import java.io.File;
import java.nio.file.Path;

import static ratpack.handlebars.Template.handlebarsTemplate;

/**
 * Handler for the api-console integration.
 */
class ApiConsoleHandler implements Handler {
    private final static Logger LOG = LoggerFactory.getLogger(ApiConsoleHandler.class);
    private final String basePath;

    public ApiConsoleHandler(String basePath) {

        this.basePath = basePath;
    }

    @Override
    public void handle(final Context ctx) throws Exception {
        final RamlModelRepository ramlModelRepository = ctx.get(RamlModelRepository.class);
        final Path filePath = ramlModelRepository.getFilePath();
        final PathBinding pathBinding = ctx.getPathBinding();
        final String path = pathBinding.getPastBinding();

        if (path.isEmpty() || path.equals("index.html")) {
            final String queryParams = QueryParams.queryParams(ctx);
            final Integer port = ctx.getServerConfig().getPort();
            final String jsonFileName = filePath.toAbsolutePath().toString().replaceAll("raml$", "json");
            final File jsonFile = new File(jsonFileName);
            final ImmutableMap<String, Object> model =
                    ImmutableMap.of(
                            "ramlPath", "api-raml/Vrap-Extension.raml",
                            "queryParams", queryParams,
                            "proxyHost", "localhost",
                            "proxyPort", port,
                            "jsonFile", jsonFile.exists() ? "/api-raml/" + jsonFile.getName() : ""
                    );
            ctx.render(handlebarsTemplate(model, basePath + "/index.html"));
        }
        else  {
            ctx.next();
        }
    }
}
