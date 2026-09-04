package io.github.kalayciburak.stackmate.context;

import io.github.kalayciburak.stackmate.incident.RequestContext;
import io.github.kalayciburak.stackmate.incident.SpringRuntimeContext;
import java.util.Objects;

/**
 * Bounded immutable request and Spring facts captured at the servlet exception boundary.
 *
 * @param request sanitized request facts
 * @param springRuntime sanitized Spring runtime facts
 */
public record ServletIncidentContext(RequestContext request, SpringRuntimeContext springRuntime) {

    /**
     * Validates and creates a servlet incident context.
     *
     * @param request sanitized request facts
     * @param springRuntime sanitized Spring runtime facts
     */
    public ServletIncidentContext {
        request = Objects.requireNonNull(request, "request");
        springRuntime = Objects.requireNonNull(springRuntime, "springRuntime");
    }
}
