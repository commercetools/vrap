package ramble;

import ratpack.exec.Downstream;
import ratpack.exec.Promise;

import java.io.IOException;
import java.nio.file.*;

/**
 * Watches files for changes.
 */
class FileWatcher {
    private final WatchService watchService;

    public FileWatcher(final Path dirToWatch) throws IOException {
        this.watchService = FileSystems.getDefault().newWatchService();
        register(dirToWatch);
    }

    public Promise<Long> lastModified() {
        return Promise.async(downstream -> pollLastModified(downstream));
    }

    private void pollLastModified(Downstream<? super Long> downstream) {
        try {
            WatchKey watchKey = watchService.take();
            if (watchKey.reset()) {
                final Path path = (Path) watchKey.watchable();
                downstream.success(path.toFile().lastModified());
            } else {
                downstream.success(-1L);
            }
        } catch (InterruptedException e) {
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
