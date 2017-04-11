package io.vrap;

import java.util.Optional;
import java.util.stream.Stream;

public enum ValidationFlag {

    response,

    request,

    header,

    queryParameter;

    public static Optional<ValidationFlag> parse(String value)
    {
        return Optional.ofNullable(value)
                .map(o -> Stream.of(values())
                        .filter(m -> m.name().equals(o))
                        .findFirst()
                        .orElse(null)
                );
    }
}
