package io.vrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.exec.Downstream;
import ratpack.exec.Promise;
import ratpack.service.StopEvent;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Watches files for changes and uses the default watch service provided by the jdk.
 */
class DefaultFileWatcher implements FileWatcher {
    private final static Logger LOG = LoggerFactory.getLogger(DefaultFileWatcher.class);

    private final Path parent;
    private final Set<Path> watchFiles;
    private final WatchService watchService;

    DefaultFileWatcher(final Path parent, final List<Path> watchFiles) throws IOException {
        this.parent = parent;
        this.watchFiles = watchFiles.stream().map(w -> parent.relativize(w)).collect(Collectors.toSet());
        this.watchService = FileSystems.getDefault().newWatchService();
        register(parent);
    }

    @Override
    public Promise<FileTime> lastModified() {
        return Promise.async(downstream -> pollLastModified(downstream));
    }

    @Override
    public void onStop(final StopEvent event) throws Exception {
        watchService.close();
        LOG.info("Closed watch service");
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
                        final Path changedPath = (Path) watchEvent.context();
                        if (watchFiles.contains(changedPath) && watchEvent.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            lastModified = Files.getLastModifiedTime(parent.resolve(changedPath));
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
