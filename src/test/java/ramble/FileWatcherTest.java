package ramble;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import ratpack.test.exec.ExecHarness;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Unit tests for {@link FileWatcher}.
 */
public class FileWatcherTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void lastModified() throws Exception {
        final FileWatcher fileWatcher = new FileWatcher(folder.getRoot().toPath());

        final long expectedLastModified = folder.newFile().lastModified();
        final long result = ExecHarness.yieldSingle(execution -> fileWatcher.lastModified()).getValue();

        assertThat(result).isEqualTo(expectedLastModified);
    }
}
