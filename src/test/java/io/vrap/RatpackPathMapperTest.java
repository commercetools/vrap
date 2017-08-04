package io.vrap;

import org.assertj.core.util.Lists;
import org.junit.Test;
import org.junit.runner.RunWith;


import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import org.raml.v2.api.model.common.ValidationResult;
import org.raml.v2.api.model.v10.datamodel.ExampleSpec;
import org.raml.v2.api.model.v10.datamodel.StringTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.XMLFacetInfo;
import org.raml.v2.api.model.v10.declarations.AnnotationRef;
import org.raml.v2.api.model.v10.declarations.AnnotationTarget;
import org.raml.v2.api.model.v10.system.types.AnnotableStringType;
import org.raml.v2.api.model.v10.system.types.MarkdownString;

import java.util.List;

import static org.assertj.core.api.Java6Assertions.assertThat;

@RunWith(DataProviderRunner.class)
public class RatpackPathMapperTest {
    public static final String DIRECTORY_PATTERN = "[-a-zA-Z0-9@:%_\\+.~#?&=]+";
    public static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @DataProvider
    public static Object[][] testExamples() {
        return new Object[][] {
                {"https://api.sphere.io", "https://api.sphere.io"},
                {"https://api.sphere.io/", "https://api.sphere.io/"},
                {"https://api.sphere.io/{projectKey}", "https://api.sphere.io/::" + DIRECTORY_PATTERN},
                {"https://api.sphere.io/{projectKey}/", "https://api.sphere.io/::" + DIRECTORY_PATTERN},
                {"https://api.sphere.io/{projectKey}/test", "https://api.sphere.io/::" + DIRECTORY_PATTERN + "/test"},
                {"{projectKey}", "::" + DIRECTORY_PATTERN},
                {"{projectKey}/", "::" + DIRECTORY_PATTERN},
                {"{projectKey}/test", "::" + DIRECTORY_PATTERN + "/test"},
                {"{projectKey}/{ID}/", "::" + DIRECTORY_PATTERN + "/::" + DIRECTORY_PATTERN},
                {"/{projectKey}", "::" + DIRECTORY_PATTERN},
                {"/{projectKey}/", "::" + DIRECTORY_PATTERN},
                {"/{projectKey}/test", "::" + DIRECTORY_PATTERN + "/test"},
                {"/{projectKey}/{ID}/", "::" + DIRECTORY_PATTERN + "/::" + DIRECTORY_PATTERN},
                {"/{projectKey}/key={key}/", "::" + DIRECTORY_PATTERN + "/::key=" + DIRECTORY_PATTERN},
        };
    }

    @DataProvider
    public static Object[][] testTypeExamples() {
        final List<TypeDeclaration> uriParameters = Lists.newArrayList();
        uriParameters.add(new UriParameter("projectKey"));
        uriParameters.add(new UriParameter("key"));
        uriParameters.add(new UriParameter("ID", "^" + UUID_PATTERN + "$"));
        return new Object[][] {
                {"https://api.sphere.io/{projectKey}", "https://api.sphere.io/::" + DIRECTORY_PATTERN, uriParameters},
                {"https://api.sphere.io/{projectKey}/", "https://api.sphere.io/::" + DIRECTORY_PATTERN, uriParameters},
                {"https://api.sphere.io/{projectKey}/test", "https://api.sphere.io/::" + DIRECTORY_PATTERN + "/test", uriParameters},
                {"{projectKey}", "::" + DIRECTORY_PATTERN, uriParameters},
                {"{projectKey}/", "::" + DIRECTORY_PATTERN, uriParameters},
                {"{projectKey}/test", "::" + DIRECTORY_PATTERN + "/test", uriParameters},
                {"{projectKey}/{ID}/", "::" + DIRECTORY_PATTERN + "/::" + UUID_PATTERN, uriParameters},
                {"/{projectKey}", "::" + DIRECTORY_PATTERN, uriParameters},
                {"/{projectKey}/", "::" + DIRECTORY_PATTERN, uriParameters},
                {"/{projectKey}/test", "::" + DIRECTORY_PATTERN + "/test", uriParameters},
                {"/{projectKey}/{ID}/", "::" + DIRECTORY_PATTERN + "/::" + UUID_PATTERN, uriParameters},
                {"/{projectKey}/categories/key={key}/", "::" + DIRECTORY_PATTERN + "/categories/::key=" + DIRECTORY_PATTERN, uriParameters},
        };
    }

    @Test
    @UseDataProvider("testExamples")
    public void mapping(final String path, final String expectedPath) {
        assertThat(RatpackPathMapper.map(path)).isEqualTo(expectedPath);
    }

    @Test
    @UseDataProvider("testTypeExamples")
    public void mappingWithUriParameters(final String path, final String expectedPath, final List<TypeDeclaration> uriParameters) {
        assertThat(RatpackPathMapper.map(path, uriParameters)).isEqualTo(expectedPath);
    }

    private static class UriParameter implements StringTypeDeclaration {
        private final String pattern;
        private final String name;

        public UriParameter(final String name, final String pattern) {
            this.pattern = pattern;
            this.name = name;
        }

        public UriParameter(final String name) {
            this(name, null);
        }

        @Override
        public String pattern() {
            return pattern;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Integer minLength() {
            return null;
        }

        @Override
        public Integer maxLength() {
            return null;
        }

        @Override
        public List<String> enumValues() {
            return null;
        }

        @Override
        public AnnotableStringType displayName() {
            return null;
        }

        @Override
        public String type() {
            return null;
        }

        @Override
        public List<TypeDeclaration> parentTypes() {
            return null;
        }

        @Override
        public String defaultValue() {
            return null;
        }

        @Override
        public ExampleSpec example() {
            return null;
        }

        @Override
        public List<ExampleSpec> examples() {
            return null;
        }

        @Override
        public Boolean required() {
            return null;
        }

        @Override
        public MarkdownString description() {
            return null;
        }

        @Override
        public List<AnnotationTarget> allowedTargets() {
            return null;
        }

        @Override
        public List<ValidationResult> validate(String payload) {
            return null;
        }

        @Override
        public List<TypeDeclaration> facets() {
            return null;
        }

        @Override
        public XMLFacetInfo xml() {
            return null;
        }

        @Override
        public String toXmlSchema() {
            return null;
        }

        @Override
        public String toJsonSchema() {
            return null;
        }

        @Override
        public List<AnnotationRef> annotations() {
            return null;
        }
    }
}
