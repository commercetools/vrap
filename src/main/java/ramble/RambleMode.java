package ramble;

import org.raml.v2.api.model.v10.api.Api;

/**
 * Enumerates the mode in which ramble can operate.
 */
public enum RambleMode {
    /**
     * Serves example responses.
     */
    example,
    /**
     * Proxy request to server given by {@link Api#baseUri()}
     */
    proxy
}
