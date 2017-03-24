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
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static ratpack.util.Exceptions.uncheck;

/**
 * Handler for loading assets from a web jar.
 */
class WebJarHandler implements Handler {
    private final static Logger LOG = LoggerFactory.getLogger(WebJarHandler.class);

    private static final String WEBJAR_ROOT = "META-INF/resources/webjars";
    private static final String DEFAULT_INCLUDE_PATH = "dist";

    /**
     * If ramble is running from a shadow/flat jar, the webjars will be directly available on the classpath.
     *
     * In this case the jar file system will be empty.
     */
    private final Optional<FileSystem> jarFileSystem;

    private final String moduleName;
    private final String version;
    private final String includePath;

    private final static ConcurrentMap<URI, FileSystem> JAR_FILE_SYSTEMS = new ConcurrentHashMap<>();

    public WebJarHandler(final String moduleName, final String version) {
        this(moduleName, version, DEFAULT_INCLUDE_PATH);
    }

    public WebJarHandler(final String moduleName, final String version, final String includePath) {
        this.moduleName = moduleName;
        this.version = version;
        this.includePath = includePath;
        final Optional<URI> jarUri = jarUri(moduleName, version);
        this.jarFileSystem = jarUri.map(uri -> JAR_FILE_SYSTEMS.computeIfAbsent(uri, this::initJarFileSystem));
    }

    /**
     * If ramble is running from a shadow/flat jar, the webjars will be directly available on the classpath.
     * In this case the jar file system will be empty and the returned uri optional uri will be empty.
     *
     * @param moduleName the module name
     * @param version the version
     *
     * @return the optional jar uri
     */
    private Optional<URI> jarUri(final String moduleName, final String version) {
        final String webJarPath = Joiner.on("/").join(WEBJAR_ROOT, moduleName, version);
        final String resourceUrl = Resources.getResource(webJarPath).toString();
        final String codeLocationUrl = "jar:" + getClass().getProtectionDomain().getCodeSource().getLocation().toString();

        return resourceUrl.startsWith(codeLocationUrl) ?
                Optional.empty() :
                Optional.of(URI.create(resourceUrl.split("!")[0]));
    }

    private FileSystem initJarFileSystem(final URI uri) {
        try {
            return FileSystems.newFileSystem(uri, Collections.emptyMap());
        } catch (IOException e) {
            throw uncheck(e);
        }
    }

    @Override
    public void handle(final Context ctx) throws Exception {
        final PathBinding pathBinding = ctx.getPathBinding();
        final String path = pathBinding.getPastBinding();
        final String resourePathStr = Joiner.on("/").join(WEBJAR_ROOT, moduleName, version, includePath, path);

        final Path resourcePath = jarFileSystem.isPresent() ?
                jarFileSystem.map(fs -> fs.getPath(resourePathStr)).get() :
                ctx.getFileSystemBinding().file(resourePathStr);

        if (Files.exists(resourcePath)) {
            ctx.render(resourcePath);
        } else {
            ctx.next();
        }
    }
}
