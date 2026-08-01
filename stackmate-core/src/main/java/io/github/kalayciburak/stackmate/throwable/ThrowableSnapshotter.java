package io.github.kalayciburak.stackmate.throwable;

import io.github.kalayciburak.stackmate.incident.StackFrameSnapshot;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import io.github.kalayciburak.stackmate.privacy.Sanitizer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Copies hostile or unusual throwable graphs into bounded immutable data. */
public final class ThrowableSnapshotter {

    private final ThrowableSnapshotLimits limits;
    private final Sanitizer sanitizer;

    public ThrowableSnapshotter(ThrowableSnapshotLimits limits, Sanitizer sanitizer) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
    }

    public static ThrowableSnapshotter defaults() {
        return new ThrowableSnapshotter(
                ThrowableSnapshotLimits.defaults(), DefaultSanitizer.defaults());
    }

    public ThrowableSnapshot capture(Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable");
        preserveInterrupt(throwable);
        CaptureState state = new CaptureState();
        List<ThrowableNode> chain = new ArrayList<>();
        IdentityHashMap<Throwable, Boolean> causeSeen = new IdentityHashMap<>();
        Throwable current = throwable;
        int depth = 0;

        while (current != null) {
            if (causeSeen.containsKey(current)) {
                state.truncated = true;
                markLastCycle(chain);
                break;
            }
            if (depth >= limits.maxCauseDepth() || state.nodeCount >= limits.maxTotalNodes()) {
                state.truncated = true;
                markLastDepthTruncated(chain);
                break;
            }
            causeSeen.put(current, Boolean.TRUE);
            preserveInterrupt(current);
            NodeCapture captured = captureNode(current, depth, causeSeen, state);
            chain.add(captured.node());
            current = captured.cause();
            depth++;
        }
        return new ThrowableSnapshot(chain, state.nodeCount, state.truncated);
    }

    @SuppressWarnings("removal")
    public static boolean isSevereJvmFailure(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        return failure instanceof VirtualMachineError
                || failure instanceof ThreadDeath
                || failure instanceof LinkageError;
    }

    private NodeCapture captureNode(
            Throwable throwable,
            int depth,
            IdentityHashMap<Throwable, Boolean> activePath,
            CaptureState state) {
        state.nodeCount++;
        List<String> failures = new ArrayList<>();
        String message = captureMessage(throwable, failures);
        FrameCapture frameCapture = captureFrames(throwable, depth, failures);
        SuppressedCapture suppressed =
                captureSuppressedNodes(throwable, depth, activePath, state, failures);
        Throwable cause = captureCause(throwable, failures);
        SqlExceptionDetails sql = captureSqlDetails(throwable, failures);
        ThrowableNode node =
                new ThrowableNode(
                        captureType(throwable, failures),
                        message,
                        frameCapture.frames(),
                        suppressed.nodes(),
                        sql,
                        depth,
                        isWrapper(throwable),
                        false,
                        false,
                        frameCapture.truncated(),
                        suppressed.truncated(),
                        failures);
        return new NodeCapture(node, cause);
    }

    private SuppressedCapture captureSuppressedNodes(
            Throwable owner,
            int depth,
            IdentityHashMap<Throwable, Boolean> activePath,
            CaptureState state,
            List<String> failures) {
        Throwable[] liveSuppressed = captureSuppressed(owner, failures);
        int permitted = Math.min(liveSuppressed.length, limits.maxSuppressedPerThrowable());
        List<ThrowableNode> nodes = new ArrayList<>(permitted);
        boolean truncated = liveSuppressed.length > permitted;
        for (int index = 0; index < permitted; index++) {
            Throwable child = liveSuppressed[index];
            if (child == null) {
                addFailure(failures, "suppressed:null");
                continue;
            }
            if (state.nodeCount >= limits.maxTotalNodes()) {
                truncated = true;
                break;
            }
            if (depth + 1 >= limits.maxCauseDepth()) {
                truncated = true;
                break;
            }
            if (activePath.containsKey(child)) {
                state.nodeCount++;
                nodes.add(cycleNode(child, depth + 1));
                truncated = true;
                continue;
            }
            activePath.put(child, Boolean.TRUE);
            try {
                NodeCapture captured = captureNode(child, depth + 1, activePath, state);
                nodes.add(captured.node());
            } finally {
                activePath.remove(child);
            }
        }
        if (truncated) {
            state.truncated = true;
        }
        return new SuppressedCapture(List.copyOf(nodes), truncated);
    }

    private ThrowableNode cycleNode(Throwable throwable, int depth) {
        return new ThrowableNode(
                captureType(throwable, new ArrayList<>()),
                "",
                List.of(),
                List.of(),
                SqlExceptionDetails.none(),
                depth,
                isWrapper(throwable),
                true,
                false,
                false,
                false,
                List.of());
    }

    private String captureMessage(Throwable throwable, List<String> failures) {
        try {
            String message = throwable.getMessage();
            if (message == null) {
                return "";
            }
            return limit(
                    sanitizer.sanitize("exceptionMessage", message), limits.maxMessageLength());
        } catch (Throwable failure) {
            handleFailure(failure, failures, "message");
            return "";
        }
    }

    private String captureType(Throwable throwable, List<String> failures) {
        String type = throwable.getClass().getName();
        try {
            String sanitized = sanitizer.sanitize("className", type);
            if (sanitized == null || sanitized.isBlank()) {
                addFailure(failures, "type:empty-sanitizer-result");
                return "unknown.throwable";
            }
            return sanitized;
        } catch (Throwable failure) {
            handleFailure(failure, failures, "type");
            return "unknown.throwable";
        }
    }

    private FrameCapture captureFrames(Throwable throwable, int depth, List<String> failures) {
        StackTraceElement[] stack;
        try {
            stack = throwable.getStackTrace();
        } catch (Throwable failure) {
            handleFailure(failure, failures, "stackTrace");
            return new FrameCapture(List.of(), false);
        }
        if (stack == null) {
            addFailure(failures, "stackTrace:null");
            return new FrameCapture(List.of(), false);
        }
        int count = Math.min(stack.length, limits.maxFramesPerThrowable());
        List<StackFrameSnapshot> frames = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            StackTraceElement frame = stack[index];
            if (frame == null) {
                addFailure(failures, "stackFrame:null");
                continue;
            }
            try {
                frames.add(sanitizeFrame(frame, depth));
            } catch (Throwable failure) {
                handleFailure(failure, failures, "stackFrame");
            }
        }
        return new FrameCapture(List.copyOf(frames), stack.length > count);
    }

    private Throwable[] captureSuppressed(Throwable throwable, List<String> failures) {
        try {
            Throwable[] suppressed = throwable.getSuppressed();
            return suppressed == null ? new Throwable[0] : suppressed;
        } catch (Throwable failure) {
            handleFailure(failure, failures, "suppressed");
            return new Throwable[0];
        }
    }

    private StackFrameSnapshot sanitizeFrame(StackTraceElement frame, int depth) {
        return new StackFrameSnapshot(
                sanitizer.sanitize("className", safe(frame.getClassName())),
                sanitizer.sanitize("methodName", safe(frame.getMethodName())),
                sanitizer.sanitize("fileName", safe(frame.getFileName())),
                frame.getLineNumber(),
                frame.isNativeMethod(),
                sanitizer.sanitize("moduleName", safe(frame.getModuleName())),
                sanitizer.sanitize("moduleVersion", safe(frame.getModuleVersion())),
                sanitizer.sanitize("classLoaderName", safe(frame.getClassLoaderName())),
                depth);
    }

    private Throwable captureCause(Throwable throwable, List<String> failures) {
        try {
            return throwable.getCause();
        } catch (Throwable failure) {
            handleFailure(failure, failures, "cause");
            return null;
        }
    }

    private SqlExceptionDetails captureSqlDetails(Throwable throwable, List<String> failures) {
        if (!(throwable instanceof SQLException sqlException)) {
            return SqlExceptionDetails.none();
        }
        String sqlState = "";
        int vendorCode = 0;
        try {
            String capturedState = sqlException.getSQLState();
            sqlState =
                    capturedState == null
                            ? ""
                            : limit(sanitizer.sanitize("sqlState", capturedState), 64);
        } catch (Throwable failure) {
            handleFailure(failure, failures, "sqlState");
        }
        try {
            vendorCode = sqlException.getErrorCode();
        } catch (Throwable failure) {
            handleFailure(failure, failures, "sqlVendorCode");
        }
        return new SqlExceptionDetails(sqlState, vendorCode);
    }

    private void handleFailure(Throwable failure, List<String> failures, String operation) {
        preserveInterrupt(failure);
        if (isSevereJvmFailure(failure)) {
            throwUnchecked(failure);
        }
        addFailure(failures, operation + ':' + failure.getClass().getName());
    }

    private void addFailure(List<String> failures, String failure) {
        if (failures.size() < limits.maxCaptureFailures()) {
            failures.add(failure);
        }
    }

    private static void markLastCycle(List<ThrowableNode> chain) {
        if (chain.isEmpty()) {
            return;
        }
        ThrowableNode node = chain.getLast();
        chain.set(chain.size() - 1, copyWithFlags(node, true, node.depthTruncated()));
    }

    private static void markLastDepthTruncated(List<ThrowableNode> chain) {
        if (chain.isEmpty()) {
            return;
        }
        ThrowableNode node = chain.getLast();
        chain.set(chain.size() - 1, copyWithFlags(node, node.cycleDetected(), true));
    }

    private static ThrowableNode copyWithFlags(
            ThrowableNode node, boolean cycleDetected, boolean depthTruncated) {
        return new ThrowableNode(
                node.type(),
                node.message(),
                node.frames(),
                node.suppressed(),
                node.sql(),
                node.depth(),
                node.wrapper(),
                cycleDetected,
                depthTruncated,
                node.framesTruncated(),
                node.suppressedTruncated(),
                node.captureFailures());
    }

    private static boolean isWrapper(Throwable throwable) {
        return throwable instanceof CompletionException || throwable instanceof ExecutionException;
    }

    private static void preserveInterrupt(Throwable throwable) {
        if (throwable instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private static String limit(String value, int maxLength) {
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints <= maxLength) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxLength));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void throwUnchecked(Throwable failure) {
        ThrowableSnapshotter.<RuntimeException>throwAny(failure);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwAny(Throwable failure) throws T {
        throw (T) failure;
    }

    private record NodeCapture(ThrowableNode node, Throwable cause) {}

    private record FrameCapture(List<StackFrameSnapshot> frames, boolean truncated) {}

    private record SuppressedCapture(List<ThrowableNode> nodes, boolean truncated) {}

    private static final class CaptureState {
        private int nodeCount;
        private boolean truncated;
    }
}
