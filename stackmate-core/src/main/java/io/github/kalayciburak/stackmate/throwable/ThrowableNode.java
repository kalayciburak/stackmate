package io.github.kalayciburak.stackmate.throwable;

import io.github.kalayciburak.stackmate.incident.StackFrameSnapshot;
import java.util.List;
import java.util.Objects;

/**
 * A bounded immutable throwable node. It deliberately contains no live {@link Throwable}.
 *
 * @param type fully qualified throwable type
 * @param message sanitized throwable message
 * @param frames bounded stack frames captured for this node
 * @param suppressed bounded snapshots of directly suppressed throwables
 * @param sql structured JDBC details, if present
 * @param depth zero-based depth in the causal chain
 * @param wrapper whether this node is classified as a wrapper exception
 * @param cycleDetected whether capture stopped at this node because a cycle was detected
 * @param depthTruncated whether deeper causes were omitted by the depth limit
 * @param framesTruncated whether additional stack frames were omitted
 * @param suppressedTruncated whether additional suppressed throwables were omitted
 * @param captureFailures safe descriptions of facts that could not be captured
 */
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

    /**
     * Validates and creates a bounded throwable node.
     *
     * @param type fully qualified throwable type
     * @param message sanitized throwable message
     * @param frames bounded stack frames captured for this node
     * @param suppressed bounded snapshots of directly suppressed throwables
     * @param sql structured JDBC details, if present
     * @param depth zero-based depth in the causal chain
     * @param wrapper whether this node is classified as a wrapper exception
     * @param cycleDetected whether capture stopped at this node because a cycle was detected
     * @param depthTruncated whether deeper causes were omitted by the depth limit
     * @param framesTruncated whether additional stack frames were omitted
     * @param suppressedTruncated whether additional suppressed throwables were omitted
     * @param captureFailures safe descriptions of facts that could not be captured
     */
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
