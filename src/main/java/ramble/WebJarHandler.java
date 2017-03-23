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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
    private final String includePath;

    private final static ConcurrentMap<URI, FileSystem> JAR_FILE_SYSTEMS = new ConcurrentHashMap<>();

    public WebJarHandler(final String moduleName, final String version, final String includePath) {
        this.modueName = moduleName;
        this.version = version;
        this.includePath = includePath;
        final URI uri = jarUri(moduleName, version);
        this.jarFileSystem = JAR_FILE_SYSTEMS.computeIfAbsent(uri, this::initJarFileSystem);
    }

    private FileSystem initJarFileSystem(final URI uri) {
        try {
            return FileSystems.newFileSystem(uri, Collections.emptyMap());
        } catch (IOException e) {
            throw uncheck(e);
        }
    }

    private URI jarUri(final String moduleName, final String version) {
        final String webJarPath = Joiner.on("/").join(WEBJAR_ROOT, moduleName, version);
        final URL resource = Resources.getResource(webJarPath);
        final String[] split = resource.toString().split("!");
        return URI.create(split[0]);
    }

    @Override
    public void handle(final Context ctx) throws Exception {
        final PathBinding pathBinding = ctx.getPathBinding();
        final String path = pathBinding.getPastBinding();

        final Path resourcePath = jarFileSystem.getPath(WEBJAR_ROOT, modueName, version, includePath, path);

        if (Files.exists(resourcePath)) {
            ctx.render(resourcePath);
        } else {
            ctx.next();
        }
    }
}
