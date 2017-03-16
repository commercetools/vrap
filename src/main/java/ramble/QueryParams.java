package ramble;

import ratpack.handling.Context;
import ratpack.http.Request;

/**
 * This interface defines predicates on query parameters that are used across our endpoints.
 */
interface QueryParams {
    /**
     * Enables resolving of {@code !include} RAML tags.
     *
     * @see IncludeResolver
     */
    String INCLUDE = "include";

    /**
     * Returns true iff. the given parameter has the {@link #INCLUDE} query parameter.
     *
     * @param request the request
     *
     * @return true iff. the resolve includes query parameter is present
     */
    static boolean resolveIncludes(final Request request) {
        return request.getQueryParams().containsKey(INCLUDE);
    }

    /**
     * Returns true iff. the given parameter has the {@link #INCLUDE} query parameter.
     *
     * @param context the context
     *
     * @return true iff. the resolve includes query parameter is present
     */
    static boolean resolveIncludes(final Context context) {
        return resolveIncludes(context.getRequest());
    }

    /**
     * Returns the supported query params as query string.
     *
     * @param context the context
     *
     * @return the supported query string or the empty string
     */
    static String queryParams(final Context context) {
        return resolveIncludes(context) ? "?" + INCLUDE : "";
    }
}
