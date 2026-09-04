package io.github.kalayciburak.stackmate.capture;

import io.github.kalayciburak.stackmate.context.ServletIncidentContext;
import java.util.Objects;

/**
 * An escaping application exception paired with its bounded immutable servlet context.
 *
 * @param exception exception that escaped the servlet chain
 * @param context servlet and Spring facts captured for that exception
 */
public record CapturedServletException(Throwable exception, ServletIncidentContext context) {

    /**
     * Validates and creates a captured servlet exception.
     *
     * @param exception exception that escaped the servlet chain
     * @param context servlet and Spring facts captured for that exception
     */
    public CapturedServletException {
        exception = Objects.requireNonNull(exception, "exception");
        context = Objects.requireNonNull(context, "context");
    }
}
