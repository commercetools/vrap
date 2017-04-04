package io.vrap;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.google.common.io.Resources.getResource;
import static org.assertj.core.api.Java6Assertions.assertThat;

public class IncludeResolverTest {

    private IncludeResolver includeResolver = new IncludeResolver();

    @Test
    public void shouldPreprocessValidRamlAndJsonIncludes() throws Exception {
        final Path path = Paths.get(getResource("test.raml").toURI());
        final String content = includeResolver.preprocess(path).toString();

        final String expectedContent = Resources.toString(getResource("test-resolved.raml.expected"), Charsets.UTF_8);

        assertThat(content).isEqualTo(expectedContent);
    }
}
