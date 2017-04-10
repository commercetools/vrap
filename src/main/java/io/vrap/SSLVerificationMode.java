package io.vrap;

import java.util.Optional;
import java.util.stream.Stream;

public enum SSLVerificationMode {
    /**
     * normal SSL handling
     */
    normal,
    /**
     * SSL Verification disabled
     */
    insecure;

    public static Optional<SSLVerificationMode> parse(String value)
    {
        return Optional.ofNullable(value)
                .map(o -> Stream.of(values())
                        .filter(m -> m.name().equals(o))
                        .findFirst()
                        .orElse(null)
                );
    }
}
