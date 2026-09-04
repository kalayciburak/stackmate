package io.github.kalayciburak.stackmate.incident;

import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Complete immutable input to deterministic incident analysis.
 *
 * @param incidentId identifier assigned to this captured incident
 * @param timestamp time at which the incident was captured
 * @param throwable bounded throwable snapshot
 * @param applicationFrames application-owned frames selected from the throwable chain
 * @param requestContext sanitized request facts, if supplied by an adapter
 * @param springRuntimeContext Spring runtime facts, if supplied by an adapter
 * @param jvmRuntimeContext JVM and operating-system facts captured for the incident
 */
public record IncidentSnapshot(
        String incidentId,
        Instant timestamp,
        ThrowableSnapshot throwable,
        List<StackFrameSnapshot> applicationFrames,
        RequestContext requestContext,
        SpringRuntimeContext springRuntimeContext,
        JvmRuntimeContext jvmRuntimeContext) {

    /**
     * Validates and creates an incident snapshot.
     *
     * @param incidentId identifier assigned to this captured incident
     * @param timestamp time at which the incident was captured
     * @param throwable bounded throwable snapshot
     * @param applicationFrames application-owned frames selected from the throwable chain
     * @param requestContext sanitized request facts, if supplied by an adapter
     * @param springRuntimeContext Spring runtime facts, if supplied by an adapter
     * @param jvmRuntimeContext JVM and operating-system facts captured for the incident
     */
    public IncidentSnapshot {
        incidentId = requireText(incidentId, "incidentId");
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
        throwable = Objects.requireNonNull(throwable, "throwable");
        applicationFrames =
                List.copyOf(Objects.requireNonNull(applicationFrames, "applicationFrames"));
        requestContext = Objects.requireNonNull(requestContext, "requestContext");
        springRuntimeContext = Objects.requireNonNull(springRuntimeContext, "springRuntimeContext");
        jvmRuntimeContext = Objects.requireNonNull(jvmRuntimeContext, "jvmRuntimeContext");
    }

    /**
     * Creates an incident without framework request or Spring runtime facts.
     *
     * @param incidentId identifier assigned to this captured incident
     * @param timestamp time at which the incident was captured
     * @param throwable bounded throwable snapshot
     * @param applicationFrames application-owned frames selected from the throwable chain
     * @return an incident using empty request and Spring contexts and the current JVM context
     */
    public static IncidentSnapshot of(
            String incidentId,
            Instant timestamp,
            ThrowableSnapshot throwable,
            List<StackFrameSnapshot> applicationFrames) {
        return new IncidentSnapshot(
                incidentId,
                timestamp,
                throwable,
                applicationFrames,
                RequestContext.none(),
                SpringRuntimeContext.unknown(),
                JvmRuntimeContext.current());
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
