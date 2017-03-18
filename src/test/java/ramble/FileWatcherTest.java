package ramble;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import ratpack.test.exec.ExecHarness;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Unit tests for {@link FileWatcher}.
 */
public class FileWatcherTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void lastModified() throws Exception {
        final File watchedFile = folder.newFile();
        final FileWatcher fileWatcher = new FileWatcher(folder.getRoot().toPath(), Arrays.asList(watchedFile.toPath()));

        folder.newFile();
        final FileTime expectedLastModified = Files.getLastModifiedTime(watchedFile.toPath());
        FileTime result = ExecHarness.yieldSingle(execution -> fileWatcher.lastModified()).getValue();

        assertThat(result).isEqualTo(expectedLastModified);

        final FileTime newExpectedLastModified = FileTime.from(expectedLastModified.toInstant().plusSeconds(60));
        Files.setLastModifiedTime(watchedFile.toPath(), newExpectedLastModified);

        result = ExecHarness.yieldSingle(execution -> fileWatcher.lastModified()).getValue();

        assertThat(result.toMillis()).isGreaterThan(expectedLastModified.toMillis());
    }
}
