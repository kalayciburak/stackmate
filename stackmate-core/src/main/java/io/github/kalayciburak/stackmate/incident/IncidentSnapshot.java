package io.github.kalayciburak.stackmate.incident;

import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Complete immutable input to deterministic incident analysis. */
public record IncidentSnapshot(
        String incidentId,
        Instant timestamp,
        ThrowableSnapshot throwable,
        List<StackFrameSnapshot> applicationFrames,
        RequestContext requestContext,
        SpringRuntimeContext springRuntimeContext,
        JvmRuntimeContext jvmRuntimeContext) {

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
