package io.github.kalayciburak.stackmate.incident;

import java.util.Map;
import java.util.Objects;

/** Bounded request facts supplied by an adapter after privacy filtering. */
public record RequestContext(
        String method,
        String path,
        String handlerClass,
        String handlerMethod,
        int responseStatus,
        Map<String, String> selectedCorrelationIds) {

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

    public static RequestContext none() {
        return new RequestContext("", "", "", "", 0, Map.of());
    }
}
