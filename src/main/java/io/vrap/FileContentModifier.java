package io.vrap;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * This class applies content modifiers to a file with the given path.
 */
class FileContentModifier implements Function<String, String> {
    private final String path;
    private final Function<String, String> contentModifier;

    /**
     * @param path the path
     * @param contentModifiers the content modifiers
     */
    @SafeVarargs
    public FileContentModifier(final String path, final Function<String, String>... contentModifiers) {
        this.path = path;
        this.contentModifier = Stream.of(contentModifiers).reduce(Function.identity(), Function::compose);
    }

    /**
     * Applies the content modifiers.
     *
     * @param content the content to modify
     *
     * @return the modified content
     */
    @Override
    public String apply(final String content) {
        return contentModifier.apply(content);
    }

    /**
     * Matches this path with the given path.
     *
     * @param path the path to match
     *
     * @return true iff. the given path matches this path
     */
    public boolean matches(final String path) {
        return Objects.equals(this.path, path);
    }
}
