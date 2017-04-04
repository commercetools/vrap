package io.vrap;

import ratpack.http.Status;

/**
 * Defines status codes used by vrap.
 */
public interface VrapStatus {
    Status BAD_REQUEST = Status.of(400, "Vrap: Bad request");

    Status BAD_GATEWAY = Status.of(502, "Vrap: Bad gateway");
}
