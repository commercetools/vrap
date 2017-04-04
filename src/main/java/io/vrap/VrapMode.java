package io.vrap;

import org.raml.v2.api.model.v10.api.Api;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Enumerates the mode in which vrap can operate.
 */
public enum VrapMode {
    /**
     * Serves example responses.
     */
    example,
    /**
     * Proxy request to server given by {@link Api#baseUri()}
     */
    proxy;

    public static Optional<VrapMode> parse(String value)
    {
        return Optional.ofNullable(value)
                .map(o -> Stream.of(values())
                        .filter(m -> m.name().equals(o))
                        .findFirst()
                        .orElse(null)
                );
    }
}
