package io.vrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collects the includes files of a raml file.
 */
class IncludeCollector {
    private final static Pattern INCLUDE_TAG_PATTERN = Pattern.compile("(\\s*)([^#]+) !include (\\S+)");

    private final Path ramlFile;

    public IncludeCollector(final Path ramlFile) {
        this.ramlFile = ramlFile;
    }

    public List<Path> collect()  {
        try {
            return collect(ramlFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Path> collect(final Path current) throws IOException {
        final List<Path> includePaths = new ArrayList<>();
        for (final String line : Files.readAllLines(current)) {
            final Matcher matcher = INCLUDE_TAG_PATTERN.matcher(line);
            if (matcher.matches()) {
                final Path includePath = current.getParent().resolve(matcher.group(3));
                includePaths.add(includePath);
                includePaths.addAll(collect(includePath));
            }
        }
        return includePaths;
    }
}
