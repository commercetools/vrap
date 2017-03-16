package ramble;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the inline tags of a raml file by inlining their content.
 */
class IncludeResolver {
    private final static Pattern INCLUDE_TAG_PATTERN = Pattern.compile("(\\s*)([^#]+) !include (\\S+)");

    public StringWriter preprocess(final Path filePath) throws IOException {
        return preprocess(filePath, "");
    }

    private StringWriter preprocess(final Path filePath, final String currentIndent) throws IOException {
        final StringWriter stringWriter = new StringWriter();

        for (final String line : Files.readAllLines(filePath)) {
            final Matcher matcher = INCLUDE_TAG_PATTERN.matcher(line);

            if (!line.startsWith("#%RAML 1.0 DataType")) {
                if (matcher.matches()) {
                    final String indent = matcher.group(1) + "  ";
                    final Path includePath = filePath.getParent().resolve(matcher.group(3));
                    stringWriter.append(currentIndent).append(matcher.group(1)).append(matcher.group(2));
                    if (includePath.getFileName().toString().endsWith(".json")) {
                        stringWriter.append(" |");
                    }
                    stringWriter.append("\n");
                    final StringWriter includeWriter = preprocess(includePath, currentIndent + indent);
                    stringWriter.append(includeWriter.toString()).append("\n");
                } else {
                    stringWriter.append(currentIndent).append(line).append("\n");
                }
            }
        }
        return stringWriter;
    }
}
