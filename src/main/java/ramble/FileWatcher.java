package ramble;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.exec.Downstream;
import ratpack.exec.Promise;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Watches files for changes.
 */
class FileWatcher {
    private final static Logger LOG = LoggerFactory.getLogger(FileWatcher.class);

    private final Path baseDir;
    private final Set<Path> watchedFiles;
    private final WatchService watchService;

    public FileWatcher(final Path baseDir, final List<Path> watchedFiles) throws IOException {
        this.baseDir = baseDir;
        this.watchedFiles = watchedFiles.stream().map(w -> baseDir.relativize(w)).collect(Collectors.toSet());
        this.watchService = FileSystems.getDefault().newWatchService();
        register(baseDir);
    }

    public Promise<FileTime> lastModified() {
        return Promise.async(downstream -> pollLastModified(downstream));
    }

    private void pollLastModified(Downstream<? super FileTime> downstream) {
        try {
            WatchKey watchKey = watchService.poll(100, TimeUnit.MILLISECONDS);
            while (watchKey == null) {
                watchKey = watchService.poll(250, TimeUnit.MILLISECONDS);
            }
            FileTime lastModified = Files.getLastModifiedTime(baseDir);
            if (watchKey.isValid()) {
                final List<WatchEvent<?>> watchEvents = watchKey.pollEvents();
                for (final WatchEvent watchEvent : watchEvents) {
                    if (watchEvent.count() <= 1) {
                        final Path changedPath = (Path) watchEvent.context();
                        if (watchedFiles.contains(changedPath) && watchEvent.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            lastModified = Files.getLastModifiedTime(baseDir.resolve(changedPath));
                        }
                    } else if (watchEvent.kind() == StandardWatchEventKinds.OVERFLOW) {
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
            watch.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
