package io.github.kalayciburak.stackmate.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.analysis.DefaultDiagnosticAnalyzers;
import io.github.kalayciburak.stackmate.analysis.DeterministicDiagnosticEngine;
import io.github.kalayciburak.stackmate.analysis.DeterministicResult;
import io.github.kalayciburak.stackmate.capture.CapturedServletException;
import io.github.kalayciburak.stackmate.configuration.ProductionProfileGuard;
import io.github.kalayciburak.stackmate.configuration.StackMateProperties;
import io.github.kalayciburak.stackmate.context.ServletIncidentContext;
import io.github.kalayciburak.stackmate.deduplication.BoundedInMemoryDeduplicationStore;
import io.github.kalayciburak.stackmate.deduplication.DeduplicationResult;
import io.github.kalayciburak.stackmate.fingerprint.ExceptionFingerprint;
import io.github.kalayciburak.stackmate.fingerprint.IncidentFingerprinter;
import io.github.kalayciburak.stackmate.incident.RequestContext;
import io.github.kalayciburak.stackmate.incident.SpringRuntimeContext;
import io.github.kalayciburak.stackmate.output.ConsoleRenderer;
import io.github.kalayciburak.stackmate.output.RenderOptions;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotter;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DefaultServletExceptionCaptureHandlerTest {

    @Test
    void should_render_once_and_use_duplicate_message_for_same_failure() {
        RecordingRenderer renderer = new RecordingRenderer();
        DefaultServletExceptionCaptureHandler handler =
                handler(new StackMateProperties(), renderer);
        IllegalStateException failure = failure("password=controlled-secret request 12345");
        CapturedServletException captured = captured(failure);

        handler.capture(captured);
        handler.capture(captured);

        assertThat(renderer.fullRenders).isOne();
        assertThat(renderer.duplicateRenders).isOne();
        assertThat(renderer.lastResult.incident().throwable().root().message())
                .doesNotContain("controlled-secret")
                .contains("[REDACTED]");
        assertThat(renderer.lastDuplicate.occurrenceCount()).isEqualTo(2);
    }

    @Test
    void should_not_analyze_when_production_guard_blocks_it() {
        StackMateProperties properties = new StackMateProperties();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        RecordingRenderer renderer = new RecordingRenderer();
        int[] submissions = {0};
        DefaultServletExceptionCaptureHandler handler =
                handler(properties, renderer, environment, (result, release) -> submissions[0]++);

        handler.capture(captured(failure("blocked")));

        assertThat(renderer.fullRenders).isZero();
        assertThat(renderer.duplicateRenders).isZero();
        assertThat(submissions[0]).isZero();
    }

    @Test
    void should_fail_open_when_renderer_throws() {
        ConsoleRenderer failingRenderer =
                new ConsoleRenderer() {
                    @Override
                    public String render(DeterministicResult result, RenderOptions options) {
                        throw new IllegalStateException("renderer failed");
                    }

                    @Override
                    public String renderDuplicate(
                            DeterministicResult result,
                            DeduplicationResult duplicate,
                            RenderOptions options) {
                        throw new IllegalStateException("renderer failed");
                    }

                    @Override
                    public String renderSevereJvmFailure(
                            ExceptionFingerprint fingerprint,
                            String failureType,
                            RenderOptions options) {
                        throw new IllegalStateException("renderer failed");
                    }
                };
        DefaultServletExceptionCaptureHandler handler =
                handler(new StackMateProperties(), failingRenderer);

        handler.capture(captured(failure("original")));
    }

    @Test
    void should_render_quick_output_before_submitting_one_reserved_inference() {
        StackMateProperties properties = new StackMateProperties();
        RecordingRenderer renderer = new RecordingRenderer();
        int[] submissions = {0};
        AsyncLocalInferenceCoordinator coordinator =
                (result, release) -> {
                    assertThat(renderer.fullRenders).isOne();
                    submissions[0]++;
                };
        DefaultServletExceptionCaptureHandler handler =
                handler(properties, renderer, new MockEnvironment(), coordinator);
        CapturedServletException captured = captured(failure("same failure"));

        handler.capture(captured);
        handler.capture(captured);

        assertThat(submissions[0]).isOne();
        assertThat(renderer.duplicateRenders).isOne();
    }

    private DefaultServletExceptionCaptureHandler handler(
            StackMateProperties properties, ConsoleRenderer renderer) {
        return handler(properties, renderer, new MockEnvironment());
    }

    private DefaultServletExceptionCaptureHandler handler(
            StackMateProperties properties, ConsoleRenderer renderer, MockEnvironment environment) {
        return handler(properties, renderer, environment, null);
    }

    private DefaultServletExceptionCaptureHandler handler(
            StackMateProperties properties,
            ConsoleRenderer renderer,
            MockEnvironment environment,
            AsyncLocalInferenceCoordinator coordinator) {
        return new DefaultServletExceptionCaptureHandler(
                ThrowableSnapshotter.defaults(),
                new DeterministicDiagnosticEngine(
                        DefaultDiagnosticAnalyzers.aggregator(), new IncidentFingerprinter()),
                new BoundedInMemoryDeduplicationStore(8, Duration.ofMinutes(1)),
                renderer,
                properties,
                new ProductionProfileGuard(environment, properties),
                coordinator);
    }

    private CapturedServletException captured(Throwable failure) {
        return new CapturedServletException(
                failure,
                new ServletIncidentContext(
                        RequestContext.none(),
                        new SpringRuntimeContext(
                                "sample",
                                java.util.List.of(),
                                "com.example",
                                "4.1.0",
                                "7.0.8",
                                SpringRuntimeContext.TransactionState.UNKNOWN)));
    }

    private IllegalStateException failure(String message) {
        IllegalStateException failure = new IllegalStateException(message);
        failure.setStackTrace(
                new StackTraceElement[] {
                    new StackTraceElement(
                            "com.example.SampleService", "run", "SampleService.java", 42)
                });
        return failure;
    }

    private static final class RecordingRenderer implements ConsoleRenderer {
        private int fullRenders;
        private int duplicateRenders;
        private DeterministicResult lastResult;
        private DeduplicationResult lastDuplicate;

        @Override
        public String render(DeterministicResult result, RenderOptions options) {
            fullRenders++;
            lastResult = result;
            return "quick";
        }

        @Override
        public String renderDuplicate(
                DeterministicResult result, DeduplicationResult duplicate, RenderOptions options) {
            duplicateRenders++;
            lastResult = result;
            lastDuplicate = duplicate;
            return "duplicate";
        }

        @Override
        public String renderSevereJvmFailure(
                ExceptionFingerprint fingerprint, String failureType, RenderOptions options) {
            return "severe";
        }
    }
}
