package ramble;

import com.barbarysoftware.watchservice.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.exec.Downstream;
import ratpack.exec.Promise;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Watches files for changes. Uses a mac os x specific file watcher which improves the performance
 * of the file watcher shipped with oracles jdk dramatically.
 */
class MacOSXFileWatcher implements FileWatcher {
    private final static Logger LOG = LoggerFactory.getLogger(MacOSXFileWatcher.class);

    private final Path parent;
    private final Set<Path> watchFiles;
    private final WatchService watchService;

    MacOSXFileWatcher(final Path parent, final List<Path> watchFiles) throws IOException {
        this.parent = parent;
        this.watchFiles = watchFiles.stream().map(this::toRealPath).collect(Collectors.toSet());
        this.watchService = WatchService.newWatchService();
        register(this.parent);
    }

    private Path toRealPath(final Path w) {
        try {
            return w.toRealPath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Promise<FileTime> lastModified() {
        return Promise.async(downstream -> pollLastModified(downstream));
    }

    private void pollLastModified(Downstream<? super FileTime> downstream) {
        try {
            WatchKey watchKey = watchService.poll(100, TimeUnit.MILLISECONDS);
            while (watchKey == null) {
                watchKey = watchService.poll(250, TimeUnit.MILLISECONDS);
            }
            FileTime lastModified = Files.getLastModifiedTime(parent);
            if (watchKey.isValid()) {
                final List<WatchEvent<?>> watchEvents = watchKey.pollEvents();
                for (final WatchEvent watchEvent : watchEvents) {
                    if (watchEvent.count() <= 1) {
                        final File changedFile = (File) watchEvent.context();
                        final Path realPath = toRealPath(changedFile.toPath());
                        final boolean fileIsWatched = watchFiles.contains(realPath);
                        final boolean fileWasModified = watchEvent.kind().equals(StandardWatchEventKind.ENTRY_MODIFY);
                        final boolean watchFileChanged = fileIsWatched
                                && fileWasModified;
                        if (watchFileChanged) {
                            lastModified = Files.getLastModifiedTime(changedFile.toPath());
                        }
                    } else if (watchEvent.kind().equals(StandardWatchEventKind.OVERFLOW)) {
                        LOG.warn("Received overflow event {}", watchEvent.context());
                    }
                }
            }
            watchKey.reset();
            downstream.success(lastModified);
        } catch (InterruptedException|IOException e) {
            downstream.error(e);
        }
    }

    private void register(final Path watch) {
        try {
            WatchableFile watchableFile = new WatchableFile(watch.toFile());
            watchableFile.register(watchService, StandardWatchEventKind.ENTRY_CREATE,
                    StandardWatchEventKind.ENTRY_DELETE,
                    StandardWatchEventKind.ENTRY_MODIFY);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
