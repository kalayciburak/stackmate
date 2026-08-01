package io.github.kalayciburak.stackmate.capture;

import io.github.kalayciburak.stackmate.context.ServletIncidentContext;
import java.util.Objects;

/** An escaping application exception paired with its bounded immutable servlet context. */
public record CapturedServletException(Throwable exception, ServletIncidentContext context) {

    public CapturedServletException {
        exception = Objects.requireNonNull(exception, "exception");
        context = Objects.requireNonNull(context, "context");
    }
}
