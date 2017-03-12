package ramble;

import com.google.common.base.Joiner;
import com.google.common.io.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.path.PathBinding;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collections;

import static ratpack.util.Exceptions.uncheck;

/**
 * Handler for loading assets from a web jar.
 */
class WebJarHandler implements Handler {
    private final static Logger LOG = LoggerFactory.getLogger(WebJarHandler.class);

    private static final String WEBJAR_ROOT = "META-INF/resources/webjars";

    private final FileSystem jarFileSystem;
    private final String modueName;
    private final String version;

    public WebJarHandler(final String moduleName, final String version) {
        this.jarFileSystem = initJarFileSystem(moduleName, version);
        this.modueName = moduleName;
        this.version = version;
    }

    private FileSystem initJarFileSystem(final String moduleName, final String version) {
        final String webJarPath = Joiner.on("/").join(WEBJAR_ROOT, moduleName, version);
        final URL resource = Resources.getResource(webJarPath);
        final String[] split = resource.toString().split("!");
        try {
            return FileSystems.newFileSystem(URI.create(split[0]), Collections.emptyMap());
        } catch (IOException e) {
            throw uncheck(e);
        }
    }

    @Override
    public void handle(final Context ctx) throws Exception {
        final PathBinding pathBinding = ctx.getPathBinding();
        final String path = pathBinding.getPastBinding();

        try {
            final Path resourcePath = jarFileSystem.getPath(WEBJAR_ROOT, modueName, version, path);
            ctx.render(resourcePath);
        } catch (IllegalArgumentException e) {
            LOG.error("Resource {} not found", path);
        }
    }
}
