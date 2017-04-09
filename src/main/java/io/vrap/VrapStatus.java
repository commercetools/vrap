package io.vrap;

import ratpack.http.Status;

/**
 * Defines status codes used by vrap.
 */
public interface VrapStatus {
    Status INVALID_REQUEST = Status.of(400, "Vrap: Invalid request");

    Status INVALID_RESPONSE = Status.of(502, "Vrap: Invalid response");
}
