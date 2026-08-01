package io.github.kalayciburak.stackmate.context;

import io.github.kalayciburak.stackmate.incident.RequestContext;
import io.github.kalayciburak.stackmate.incident.SpringRuntimeContext;
import java.util.Objects;

/** Bounded immutable request and Spring facts captured at the servlet exception boundary. */
public record ServletIncidentContext(RequestContext request, SpringRuntimeContext springRuntime) {

    public ServletIncidentContext {
        request = Objects.requireNonNull(request, "request");
        springRuntime = Objects.requireNonNull(springRuntime, "springRuntime");
    }
}
