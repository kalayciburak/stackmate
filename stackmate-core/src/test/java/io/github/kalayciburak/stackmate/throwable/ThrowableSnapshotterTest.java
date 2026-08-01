package io.github.kalayciburak.stackmate.throwable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import io.github.kalayciburak.stackmate.privacy.Sanitizer;
import java.sql.SQLException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ThrowableSnapshotterTest {

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void detectsIdentityBasedCauseCyclesWithoutRetainingLiveThrowables() {
        LoopingThrowable first = new LoopingThrowable("first");
        LoopingThrowable second = new LoopingThrowable("second");
        first.cause = second;
        second.cause = first;

        ThrowableSnapshot snapshot = ThrowableSnapshotter.defaults().capture(first);

        assertThat(snapshot.causeChain()).hasSize(2);
        assertThat(snapshot.root().cycleDetected()).isTrue();
        assertThat(snapshot.truncated()).isTrue();
        assertThat(ThrowableSnapshot.class.getRecordComponents())
                .noneMatch(component -> Throwable.class.isAssignableFrom(component.getType()));
        assertThat(ThrowableNode.class.getRecordComponents())
                .noneMatch(component -> Throwable.class.isAssignableFrom(component.getType()));
    }

    @Test
    void appliesMessageFrameSuppressedAndCauseLimits() {
        RuntimeException root = new RuntimeException("root-message");
        root.setStackTrace(
                new StackTraceElement[] {
                    frame("com.example.Root", "first", 10), frame("com.example.Root", "second", 20)
                });
        IllegalStateException firstSuppressed = new IllegalStateException("one");
        firstSuppressed.addSuppressed(new IllegalStateException("nested"));
        root.addSuppressed(firstSuppressed);
        root.addSuppressed(new IllegalArgumentException("two"));
        RuntimeException middle = new RuntimeException("middle-message", root);
        RuntimeException outer = new RuntimeException("outer-message", middle);
        ThrowableSnapshotLimits limits = new ThrowableSnapshotLimits(2, 1, 1, 10, 5, 4);

        ThrowableSnapshot snapshot =
                new ThrowableSnapshotter(limits, DefaultSanitizer.defaults()).capture(outer);

        assertThat(snapshot.causeChain()).hasSize(2);
        assertThat(snapshot.outer().message()).isEqualTo("outer");
        assertThat(snapshot.root().depthTruncated()).isTrue();
        assertThat(snapshot.truncated()).isTrue();

        ThrowableSnapshot rootSnapshot =
                new ThrowableSnapshotter(limits, DefaultSanitizer.defaults()).capture(root);
        assertThat(rootSnapshot.outer().frames()).hasSize(1);
        assertThat(rootSnapshot.outer().framesTruncated()).isTrue();
        assertThat(rootSnapshot.outer().suppressed()).hasSize(1);
        assertThat(rootSnapshot.outer().suppressedTruncated()).isTrue();
        assertThat(rootSnapshot.outer().suppressed().getFirst().suppressed()).isEmpty();
        assertThat(rootSnapshot.outer().suppressed().getFirst().suppressedTruncated()).isTrue();
    }

    @Test
    void preservesCompletionAndExecutionWrappersInOuterToRootOrder() {
        IllegalArgumentException root = new IllegalArgumentException("root");
        ExecutionException execution = new ExecutionException(root);
        CompletionException completion = new CompletionException(execution);

        ThrowableSnapshot snapshot = ThrowableSnapshotter.defaults().capture(completion);

        assertThat(snapshot.causeChain())
                .extracting(ThrowableNode::type)
                .containsExactly(
                        CompletionException.class.getName(),
                        ExecutionException.class.getName(),
                        IllegalArgumentException.class.getName());
        assertThat(snapshot.causeChain())
                .extracting(ThrowableNode::wrapper)
                .containsExactly(true, true, false);
    }

    @Test
    void preservesInterruptWhenCapturingInterruptedExceptionOrHostileGetter() {
        Thread.interrupted();
        ThrowableSnapshotter.defaults().capture(new InterruptedException("stop"));
        assertThat(Thread.currentThread().isInterrupted()).isTrue();

        Thread.interrupted();
        ThrowableSnapshot snapshot =
                ThrowableSnapshotter.defaults().capture(new InterruptingThrowable());
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(snapshot.outer().captureFailures())
                .contains("message:" + InterruptedException.class.getName());
    }

    @Test
    void failsOpenForUnusualThrowableButRethrowsSevereJvmFailure() {
        ThrowableSnapshot snapshot = ThrowableSnapshotter.defaults().capture(new BrokenThrowable());

        assertThat(snapshot.outer().message()).isEmpty();
        assertThat(snapshot.outer().frames()).isEmpty();
        assertThat(snapshot.outer().captureFailures())
                .contains(
                        "message:" + AssertionError.class.getName(),
                        "stackTrace:" + IllegalStateException.class.getName(),
                        "cause:" + UnsupportedOperationException.class.getName());

        assertThatThrownBy(() -> ThrowableSnapshotter.defaults().capture(new SevereThrowable()))
                .isInstanceOf(OutOfMemoryError.class);
    }

    @Test
    void copiesSqlStateAndVendorCodeWithoutMessageParsing() {
        SQLException exception = new SQLException("constraint secret", "23505", 99);

        SqlExceptionDetails details =
                ThrowableSnapshotter.defaults().capture(exception).outer().sql();

        assertThat(details.sqlState()).isEqualTo("23505");
        assertThat(details.vendorCode()).isEqualTo(99);
    }

    @Test
    void boundsMessagesByUnicodeCodePointAndSanitizesFrameControlSequences() {
        RuntimeException exception = new RuntimeException("🙂🙂🙂");
        exception.setStackTrace(
                new StackTraceElement[] {
                    new StackTraceElement(
                            "com.example.\u001B[31mService", "run\u0000", "Şifre🙂.java", 4)
                });
        ThrowableSnapshotLimits limits = new ThrowableSnapshotLimits(2, 2, 1, 4, 2, 4);

        ThrowableNode node =
                new ThrowableSnapshotter(limits, DefaultSanitizer.defaults())
                        .capture(exception)
                        .outer();

        assertThat(node.message()).isEqualTo("🙂🙂");
        assertThat(node.frames().getFirst().className()).isEqualTo("com.example.Service");
        assertThat(node.frames().getFirst().methodName()).isEqualTo("run ");
        assertThat(node.frames().getFirst().fileName()).isEqualTo("Şifre🙂.java");
    }

    @Test
    void stopsSuppressedTraversalAtTotalNodeLimit() {
        RuntimeException outer = new RuntimeException("outer");
        outer.addSuppressed(new IllegalStateException("suppressed"));
        ThrowableSnapshotLimits limits = new ThrowableSnapshotLimits(8, 8, 8, 1, 128, 4);

        ThrowableSnapshot snapshot =
                new ThrowableSnapshotter(limits, DefaultSanitizer.defaults()).capture(outer);

        assertThat(snapshot.nodeCount()).isEqualTo(1);
        assertThat(snapshot.outer().suppressed()).isEmpty();
        assertThat(snapshot.outer().suppressedTruncated()).isTrue();
        assertThat(snapshot.truncated()).isTrue();
    }

    @Test
    void stopsCauseTraversalAtTotalNodeLimitIndependentlyOfDepthLimit() {
        RuntimeException exception =
                new RuntimeException("outer", new IllegalStateException("root"));
        ThrowableSnapshotLimits limits = new ThrowableSnapshotLimits(8, 8, 8, 1, 128, 4);

        ThrowableSnapshot snapshot =
                new ThrowableSnapshotter(limits, DefaultSanitizer.defaults()).capture(exception);

        assertThat(snapshot.causeChain()).hasSize(1);
        assertThat(snapshot.root().depthTruncated()).isTrue();
        assertThat(snapshot.truncated()).isTrue();
    }

    @Test
    void marksCyclesReachedThroughSuppressedNodes() {
        RuntimeException outer = new RuntimeException("outer");
        RuntimeException suppressed = new RuntimeException("suppressed");
        outer.addSuppressed(suppressed);
        suppressed.addSuppressed(outer);

        ThrowableSnapshot snapshot = ThrowableSnapshotter.defaults().capture(outer);

        ThrowableNode capturedSuppressed = snapshot.outer().suppressed().getFirst();
        assertThat(capturedSuppressed.suppressed()).hasSize(1);
        assertThat(capturedSuppressed.suppressed().getFirst().cycleDetected()).isTrue();
        assertThat(snapshot.truncated()).isTrue();
    }

    @Test
    void recordsNullMessageStackAndFrameWithoutFailingCapture() {
        ThrowableSnapshot nullMessage =
                ThrowableSnapshotter.defaults().capture(new RuntimeException());
        ThrowableSnapshot nullStack =
                ThrowableSnapshotter.defaults().capture(new NullStackThrowable());
        ThrowableSnapshot nullFrame =
                ThrowableSnapshotter.defaults().capture(new NullFrameThrowable());

        assertThat(nullMessage.outer().message()).isEmpty();
        assertThat(nullStack.outer().frames()).isEmpty();
        assertThat(nullStack.outer().captureFailures()).contains("stackTrace:null");
        assertThat(nullFrame.outer().frames()).isEmpty();
        assertThat(nullFrame.outer().captureFailures()).contains("stackFrame:null");
    }

    @Test
    void containsBrokenSanitizerAndBoundsRecordedCaptureFailures() {
        RuntimeException exception = new RuntimeException("message");
        exception.setStackTrace(new StackTraceElement[0]);
        ThrowableSnapshotLimits noFailures = new ThrowableSnapshotLimits(2, 0, 0, 2, 64, 0);

        ThrowableNode throwing =
                new ThrowableSnapshotter(noFailures, new ThrowingSanitizer())
                        .capture(exception)
                        .outer();
        ThrowableNode nullType =
                new ThrowableSnapshotter(
                                ThrowableSnapshotLimits.defaults(), new NullTypeSanitizer())
                        .capture(exception)
                        .outer();

        assertThat(throwing.type()).isEqualTo("unknown.throwable");
        assertThat(throwing.captureFailures()).isEmpty();
        assertThat(nullType.type()).isEqualTo("unknown.throwable");
        assertThat(nullType.captureFailures()).contains("type:empty-sanitizer-result");
    }

    @Test
    void containsHostileSqlMetadataAccessAndAcceptsNullSqlState() {
        ThrowableNode hostile =
                ThrowableSnapshotter.defaults().capture(new HostileSQLException()).outer();
        ThrowableNode nullState =
                ThrowableSnapshotter.defaults()
                        .capture(new SQLException("database", null, 7))
                        .outer();

        assertThat(hostile.sql()).isEqualTo(SqlExceptionDetails.none());
        assertThat(hostile.captureFailures())
                .contains(
                        "sqlState:" + AssertionError.class.getName(),
                        "sqlVendorCode:" + UnsupportedOperationException.class.getName());
        assertThat(nullState.sql()).isEqualTo(new SqlExceptionDetails("", 7));
    }

    @Test
    @SuppressWarnings("removal")
    void classifiesOnlySevereJvmFailures() {
        assertThat(ThrowableSnapshotter.isSevereJvmFailure(new OutOfMemoryError())).isTrue();
        assertThat(ThrowableSnapshotter.isSevereJvmFailure(new ThreadDeath())).isTrue();
        assertThat(ThrowableSnapshotter.isSevereJvmFailure(new LinkageError())).isTrue();
        assertThat(ThrowableSnapshotter.isSevereJvmFailure(new AssertionError())).isFalse();
        assertThatThrownBy(() -> ThrowableSnapshotter.isSevereJvmFailure(null))
                .isInstanceOf(NullPointerException.class);
    }

    private static StackTraceElement frame(String className, String methodName, int lineNumber) {
        return new StackTraceElement(className, methodName, "Example.java", lineNumber);
    }

    private static final class LoopingThrowable extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private Throwable cause;

        private LoopingThrowable(String message) {
            super(message, null, true, true);
        }

        @Override
        public synchronized Throwable getCause() {
            return cause;
        }
    }

    private static final class InterruptingThrowable extends RuntimeException {
        private static final long serialVersionUID = 1L;

        @Override
        public String getMessage() {
            return sneakyThrow(new InterruptedException("interrupted"));
        }
    }

    private static final class BrokenThrowable extends RuntimeException {
        private static final long serialVersionUID = 1L;

        @Override
        public String getMessage() {
            throw new AssertionError("broken");
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            throw new IllegalStateException("broken");
        }

        @Override
        public synchronized Throwable getCause() {
            throw new UnsupportedOperationException("broken");
        }
    }

    private static final class SevereThrowable extends RuntimeException {
        private static final long serialVersionUID = 1L;

        @Override
        public String getMessage() {
            throw new OutOfMemoryError("simulated");
        }
    }

    private static final class NullStackThrowable extends RuntimeException {
        private static final long serialVersionUID = 1L;

        @Override
        public StackTraceElement[] getStackTrace() {
            return null;
        }
    }

    private static final class NullFrameThrowable extends RuntimeException {
        private static final long serialVersionUID = 1L;

        @Override
        public StackTraceElement[] getStackTrace() {
            return new StackTraceElement[] {null};
        }
    }

    private static final class HostileSQLException extends SQLException {
        private static final long serialVersionUID = 1L;

        @Override
        public String getSQLState() {
            throw new AssertionError("broken state");
        }

        @Override
        public int getErrorCode() {
            throw new UnsupportedOperationException("broken code");
        }
    }

    private static final class ThrowingSanitizer implements Sanitizer {
        @Override
        public String sanitize(String value) {
            throw new AssertionError("broken sanitizer");
        }

        @Override
        public String sanitize(String fieldName, String value) {
            throw new AssertionError("broken sanitizer");
        }
    }

    private static final class NullTypeSanitizer implements Sanitizer {
        @Override
        public String sanitize(String value) {
            return value;
        }

        @Override
        public String sanitize(String fieldName, String value) {
            return "className".equals(fieldName) ? null : value;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T sneakyThrow(Throwable throwable) throws E {
        throw (E) throwable;
    }
}
