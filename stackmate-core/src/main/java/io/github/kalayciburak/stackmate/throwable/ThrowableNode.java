package io.github.kalayciburak.stackmate.throwable;

import io.github.kalayciburak.stackmate.incident.StackFrameSnapshot;
import java.util.List;
import java.util.Objects;

/** A bounded immutable throwable node. It deliberately contains no live {@link Throwable}. */
public record ThrowableNode(
        String type,
        String message,
        List<StackFrameSnapshot> frames,
        List<ThrowableNode> suppressed,
        SqlExceptionDetails sql,
        int depth,
        boolean wrapper,
        boolean cycleDetected,
        boolean depthTruncated,
        boolean framesTruncated,
        boolean suppressedTruncated,
        List<String> captureFailures) {

    public ThrowableNode {
        type = Objects.requireNonNull(type, "type");
        message = Objects.requireNonNull(message, "message");
        frames = List.copyOf(Objects.requireNonNull(frames, "frames"));
        suppressed = List.copyOf(Objects.requireNonNull(suppressed, "suppressed"));
        sql = Objects.requireNonNull(sql, "sql");
        captureFailures = List.copyOf(Objects.requireNonNull(captureFailures, "captureFailures"));
        if (depth < 0) {
            throw new IllegalArgumentException("depth must not be negative");
        }
    }
}
