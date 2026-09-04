package io.github.kalayciburak.stackmate.incident;

import java.util.Map;
import java.util.Objects;

/**
 * Bounded request facts supplied by an adapter after privacy filtering.
 *
 * @param method HTTP method, or an empty string when unavailable
 * @param path request path without a query string, or an empty string when unavailable
 * @param handlerClass selected handler class name, or an empty string when unavailable
 * @param handlerMethod selected handler method name, or an empty string when unavailable
 * @param responseStatus HTTP response status, or {@code 0} when unavailable
 * @param selectedCorrelationIds allowlisted, sanitized correlation identifiers
 */
public record RequestContext(
        String method,
        String path,
        String handlerClass,
        String handlerMethod,
        int responseStatus,
        Map<String, String> selectedCorrelationIds) {

    /**
     * Validates and creates sanitized request context.
     *
     * @param method HTTP method, or an empty string when unavailable
     * @param path request path without a query string, or an empty string when unavailable
     * @param handlerClass selected handler class name, or an empty string when unavailable
     * @param handlerMethod selected handler method name, or an empty string when unavailable
     * @param responseStatus HTTP response status, or {@code 0} when unavailable
     * @param selectedCorrelationIds allowlisted, sanitized correlation identifiers
     */
    public RequestContext {
        method = Objects.requireNonNull(method, "method");
        path = Objects.requireNonNull(path, "path");
        handlerClass = Objects.requireNonNull(handlerClass, "handlerClass");
        handlerMethod = Objects.requireNonNull(handlerMethod, "handlerMethod");
        selectedCorrelationIds =
                Map.copyOf(
                        Objects.requireNonNull(selectedCorrelationIds, "selectedCorrelationIds"));
        if (path.indexOf('?') >= 0) {
            throw new IllegalArgumentException("path must not contain a query string");
        }
        if (responseStatus != 0 && (responseStatus < 100 || responseStatus > 599)) {
            throw new IllegalArgumentException("responseStatus must be 0 or a valid HTTP status");
        }
    }

    /**
     * Creates a request context with no available request facts.
     *
     * @return an empty request context for incidents captured outside an HTTP request
     */
    public static RequestContext none() {
        return new RequestContext("", "", "", "", 0, Map.of());
    }
}
