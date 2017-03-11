package ramble;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.path.PathBinding;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static ratpack.handlebars.Template.handlebarsTemplate;
import static ratpack.util.Exceptions.uncheck;

/**
 * Handler for the api-console integration.
 */
class ApiConsoleHandler implements Handler {
    private final static Logger LOG = LoggerFactory.getLogger(ApiConsoleHandler.class);

    private static final String API_CONSOLE_ROOT = "META-INF/resources/webjars/api-console/3.0.4/";
    private final static String API_CONSOLE_ASSETS = API_CONSOLE_ROOT + "dist";

    private final Path fileName;

    public ApiConsoleHandler(final Path fileName) {
        this.fileName = fileName;
        initJarFileSystem();
    }

    private void initJarFileSystem() {
        final URL resource = Resources.getResource(API_CONSOLE_ROOT + "package.json");
        final String[] split = resource.toString().split("!");
        try {
            FileSystems.newFileSystem(URI.create(split[0]), Collections.emptyMap());
        } catch (IOException e) {
            uncheck(e);
        }
    }

    @Override
    public void handle(final Context ctx) throws Exception {
        final PathBinding pathBinding = ctx.getPathBinding();

        final String path = pathBinding.getPastBinding();

        if (path.isEmpty() || path.equals("index.html")) {
            ctx.render(handlebarsTemplate(ImmutableMap.of("fileName", fileName), "api-console/index.html"));
        }
        else  {
            final String apiConsoleResourcePath = Joiner.on("/").join(API_CONSOLE_ASSETS, path);

            try {
                final URL resource = Resources.getResource(apiConsoleResourcePath);
                ctx.render(Paths.get(resource.toURI()));
            } catch (IllegalArgumentException e) {
                LOG.error("Resource {} not found at {}", path, apiConsoleResourcePath);
            }
        }
    }
}
