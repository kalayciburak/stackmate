package io.github.kalayciburak.stackmate.throwable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApplicationFrameSelectorTest {

    @Test
    void ranksRootCauseFramesFirstFiltersGeneratedNoiseAndBoundsOutput() {
        RuntimeException root = new RuntimeException("root");
        root.setStackTrace(
                new StackTraceElement[] {
                    frame("com.example.RootService", "load", 30),
                    frame("com.example.RootService$$SpringCGLIB$$0", "load", 31),
                    frame("org.springframework.Dispatcher", "invoke", 1)
                });
        RuntimeException outer = new RuntimeException("outer", root);
        outer.setStackTrace(
                new StackTraceElement[] {
                    frame("com.example.WebController", "handle", 10),
                    frame("com.example.Other", "call", 20)
                });
        ThrowableSnapshot snapshot = ThrowableSnapshotter.defaults().capture(outer);

        var selected = new ApplicationFrameSelector(List.of("com.example"), 2).select(snapshot);

        assertThat(selected)
                .extracting(frame -> frame.className())
                .containsExactly("com.example.RootService", "com.example.WebController");
        assertThat(selected.getFirst().causalDepth()).isEqualTo(1);
    }

    @Test
    void validatesExplicitPackageAndBoundContracts() {
        assertThatThrownBy(() -> new ApplicationFrameSelector(null, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ApplicationFrameSelector(List.of(), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new ApplicationFrameSelector(java.util.Arrays.asList("com", null), 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ApplicationFrameSelector(List.of("."), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplicationFrameSelector(List.of("com.example"), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new ApplicationFrameSelector(List.of("com.example"), 1).select(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void normalizesTrailingDotAndKeepsExactPackageClass() {
        RuntimeException exception = new RuntimeException("failed");
        exception.setStackTrace(new StackTraceElement[] {frame("com.example", "exact", 1)});

        var selected =
                new ApplicationFrameSelector(List.of("com.example."), 4)
                        .select(ThrowableSnapshotter.defaults().capture(exception));

        assertThat(selected).extracting(frame -> frame.className()).containsExactly("com.example");
    }

    @Test
    void deduplicatesSharedFramesAndIncludesSuppressedCausalContext() {
        StackTraceElement shared = frame("com.example.Service", "run", 10);
        RuntimeException outer = new RuntimeException("outer", new IllegalStateException("root"));
        outer.setStackTrace(new StackTraceElement[] {shared});
        outer.getCause().setStackTrace(new StackTraceElement[] {shared});
        IllegalArgumentException suppressed = new IllegalArgumentException("suppressed");
        suppressed.setStackTrace(
                new StackTraceElement[] {frame("com.example.Suppressed", "check", 11)});
        outer.addSuppressed(suppressed);

        var selected =
                new ApplicationFrameSelector(List.of("com.example"), 10)
                        .select(ThrowableSnapshotter.defaults().capture(outer));

        assertThat(selected)
                .extracting(frame -> frame.className())
                .containsExactly("com.example.Service", "com.example.Suppressed");
    }

    @Test
    void filtersFrameworkNoiseForBroadPackageButAllowsPreciselyOwnedFrameworkSample() {
        RuntimeException exception = new RuntimeException("failed");
        exception.setStackTrace(
                new StackTraceElement[] {
                    frame("org.springframework.Dispatcher", "dispatch", 1),
                    frame("org.springframework.samples.App", "run", 2),
                    frame("outside.example.Other", "run", 3)
                });
        ThrowableSnapshot snapshot = ThrowableSnapshotter.defaults().capture(exception);

        var broad = new ApplicationFrameSelector(List.of("org"), 10).select(snapshot);
        var precise =
                new ApplicationFrameSelector(List.of("org.springframework.samples"), 10)
                        .select(snapshot);

        assertThat(broad).isEmpty();
        assertThat(precise)
                .extracting(frame -> frame.className())
                .containsExactly("org.springframework.samples.App");
    }

    private static StackTraceElement frame(String className, String methodName, int lineNumber) {
        return new StackTraceElement(className, methodName, "Example.java", lineNumber);
    }
}
