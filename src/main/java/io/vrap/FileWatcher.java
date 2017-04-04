package io.vrap;

import ratpack.exec.Promise;
import ratpack.service.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

/**
 * This interface describes the operations of a file watcher.
 */
interface FileWatcher extends Service {
    /**
     * The returned promise completes if a file change was detected and returns the latest modification time.
     *
     * @return the last modification time
     */
    Promise<FileTime> lastModified();

    /**
     * Creates a file watcher for the given parameters.
     *
     * @param parent the base directory that must be a parent of the given watch files
     * @param watchFiles the files to watch that must be relative to the given parent path
     * @return new file watcher
     *
     * @throws IOException
     */
    static FileWatcher of(final Path parent, final List<Path> watchFiles)
    {
        try {
            final String osName = System.getProperty("os.name");
            final FileWatcher fileWatcher = "Mac OS X".equalsIgnoreCase(osName) ?
                    new MacOSXFileWatcher(parent, watchFiles) : new DefaultFileWatcher(parent, watchFiles);
            return fileWatcher;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
