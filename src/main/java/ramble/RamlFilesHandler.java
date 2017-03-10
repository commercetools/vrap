package ramble;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import ratpack.handling.Context;
import ratpack.handling.Handler;

import java.io.File;
import java.nio.file.Path;

/**
 * Serves raml files from the given base dir.
 */
class RamlFilesHandler implements Handler {
    private final Path baseDir;

    public RamlFilesHandler(final Path baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public void handle(final Context ctx) throws Exception {
        final String path = ctx.getPathBinding().getPastBinding();

        final Path resolvedFilePath = baseDir.resolve(path);
        final File file = resolvedFilePath.toFile();
        if (file.getName().endsWith(".raml") && file.exists()) {
            final String content = Files.asByteSource(file).asCharSource(Charsets.UTF_8).read();
            ctx.getResponse().send("text/plain", content);
        } else {
            ctx.next();
        }
    }
}
