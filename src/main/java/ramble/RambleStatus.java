package ramble;

import ratpack.http.Status;

/**
 * Defines status codes used by ramble.
 */
public interface RambleStatus {
    Status BAD_REQUEST = Status.of(400, "Ramble: Bad request");

    Status BAD_GATEWAY = Status.of(502, "Ramble: Bad gateway");
}
