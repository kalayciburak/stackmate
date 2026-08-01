package io.github.kalayciburak.stackmate.inference;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.analysis.DefaultDiagnosticAnalyzers;
import io.github.kalayciburak.stackmate.analysis.DeterministicDiagnosticEngine;
import io.github.kalayciburak.stackmate.analysis.DeterministicResult;
import io.github.kalayciburak.stackmate.configuration.StackMateProperties;
import io.github.kalayciburak.stackmate.fingerprint.IncidentFingerprinter;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.incident.StackFrameSnapshot;
import io.github.kalayciburak.stackmate.output.RenderOptions;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class DefaultAsyncLocalInferenceCoordinatorTest {

    private final List<DefaultAsyncLocalInferenceCoordinator> coordinators = new ArrayList<>();

    @AfterEach
    void closeCoordinators() {
        coordinators.forEach(DefaultAsyncLocalInferenceCoordinator::close);
    }

    @Test
    void runs_engine_and_renderer_on_dedicated_worker_thread() throws Exception {
        AtomicReference<String> engineThread = new AtomicReference<>();
        CountDownLatch rendered = new CountDownLatch(1);
        RecordingRenderer renderer = new RecordingRenderer(rendered);
        LocalInferenceEngine engine =
                engine(
                        request -> {
                            engineThread.set(Thread.currentThread().getName());
                            return success(request);
                        });
        DefaultAsyncLocalInferenceCoordinator coordinator =
                coordinator(engine, properties(), renderer);
        String requestThread = Thread.currentThread().getName();

        coordinator.submit(result(), () -> {});

        assertThat(rendered.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(engineThread.get()).startsWith("stackmate-local-inference-");
        assertThat(engineThread.get()).isNotEqualTo(requestThread);
        assertThat(renderer.thread.get()).startsWith("stackmate-local-inference-");
    }

    @Test
    void interrupts_timeout_and_keeps_untrusted_output_hidden() throws Exception {
        StackMateProperties properties = properties();
        properties.getInference().setTimeout(Duration.ofMillis(40));
        CountDownLatch interrupted = new CountDownLatch(1);
        LocalInferenceEngine engine =
                engine(
                        request -> {
                            try {
                                new CountDownLatch(1).await();
                                throw new AssertionError("unreachable");
                            } catch (InterruptedException failure) {
                                interrupted.countDown();
                                throw failure;
                            }
                        });
        RecordingRenderer renderer = new RecordingRenderer(new CountDownLatch(1));
        DefaultAsyncLocalInferenceCoordinator coordinator =
                coordinator(engine, properties, renderer);

        coordinator.submit(result(), () -> {});

        assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
        awaitFailure(coordinator, LocalInferenceMetrics.FailureReason.TIMED_OUT);
        assertThat(renderer.calls.get()).isZero();
    }

    @Test
    void rejects_unknown_output_ids_and_opens_breaker_after_engine_failure() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        LocalInferenceEngine invalidEngine =
                engine(
                        request -> {
                            calls.incrementAndGet();
                            return new InferenceResult.Success(
                                    new StructuredInferenceOutput(
                                            request.language(),
                                            "This local explanation uses supplied evidence.",
                                            List.of(
                                                    new StructuredCause(
                                                            "cause.not-supplied",
                                                            "This cause is not supplied.",
                                                            List.of(
                                                                    request.facts()
                                                                            .getFirst()
                                                                            .id()),
                                                            List.of())),
                                            List.of()),
                                    Duration.ofMillis(3));
                        });
        StackMateProperties properties = properties();
        properties.getInference().getCircuitBreaker().setFailureThreshold(1);
        RecordingRenderer renderer = new RecordingRenderer(new CountDownLatch(1));
        DefaultAsyncLocalInferenceCoordinator coordinator =
                coordinator(invalidEngine, properties, renderer);

        coordinator.submit(result(), () -> {});
        awaitFailure(coordinator, LocalInferenceMetrics.FailureReason.INVALID_OUTPUT);
        coordinator.submit(result(), () -> {});
        awaitFailure(coordinator, LocalInferenceMetrics.FailureReason.BREAKER_OPEN);

        assertThat(calls).hasValue(1);
        assertThat(renderer.calls).hasValue(0);
    }

    @Test
    void classifies_engine_exception_without_rendering_or_releasing_reservation() throws Exception {
        AtomicInteger releases = new AtomicInteger();
        RecordingRenderer renderer = new RecordingRenderer(new CountDownLatch(1));
        DefaultAsyncLocalInferenceCoordinator coordinator =
                coordinator(
                        engine(
                                request -> {
                                    throw new IllegalStateException("private engine detail");
                                }),
                        properties(),
                        renderer);

        coordinator.submit(result(), releases::incrementAndGet);

        awaitFailure(coordinator, LocalInferenceMetrics.FailureReason.ENGINE_ERROR);
        assertThat(releases).hasValue(0);
        assertThat(renderer.calls).hasValue(0);
    }

    @Test
    void releases_reservation_when_engine_is_missing_or_queue_rejects() throws Exception {
        StackMateProperties properties = properties();
        DefaultAsyncLocalInferenceCoordinator missing =
                coordinator(null, properties, new RecordingRenderer(new CountDownLatch(1)));
        CountDownLatch missingRelease = new CountDownLatch(1);

        missing.submit(result(), missingRelease::countDown);

        assertThat(missingRelease.await(2, TimeUnit.SECONDS)).isTrue();
        awaitFailure(missing, LocalInferenceMetrics.FailureReason.UNAVAILABLE);

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch unblock = new CountDownLatch(1);
        LocalInferenceEngine blocking =
                engine(
                        request -> {
                            firstStarted.countDown();
                            unblock.await();
                            return success(request);
                        });
        AtomicInteger releases = new AtomicInteger();
        DefaultAsyncLocalInferenceCoordinator queued =
                coordinator(blocking, properties, new RecordingRenderer(new CountDownLatch(2)));
        queued.submit(result(), releases::incrementAndGet);
        assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
        queued.submit(result(), releases::incrementAndGet);
        queued.submit(result(), releases::incrementAndGet);

        awaitFailure(queued, LocalInferenceMetrics.FailureReason.QUEUE_REJECTED);
        assertThat(releases).hasValue(1);
        unblock.countDown();
    }

    @Test
    void releases_new_work_after_shutdown() {
        DefaultAsyncLocalInferenceCoordinator coordinator =
                coordinator(
                        engine(DefaultAsyncLocalInferenceCoordinatorTest::success),
                        properties(),
                        new RecordingRenderer(new CountDownLatch(1)));
        AtomicInteger releases = new AtomicInteger();

        coordinator.close();
        coordinator.submit(result(), releases::incrementAndGet);

        assertThat(releases).hasValue(1);
        assertThat(metrics(coordinator).failureCount(LocalInferenceMetrics.FailureReason.SHUTDOWN))
                .isOne();
    }

    private DefaultAsyncLocalInferenceCoordinator coordinator(
            LocalInferenceEngine engine,
            StackMateProperties properties,
            LocalExplanationRenderer renderer) {
        LocalInferenceMetrics metrics = new LocalInferenceMetrics();
        DefaultAsyncLocalInferenceCoordinator coordinator =
                new DefaultAsyncLocalInferenceCoordinator(
                        provider(engine),
                        new DefaultInferenceOutputValidator(),
                        new InMemoryInferenceCircuitBreaker(
                                properties.getInference().getCircuitBreaker().getFailureThreshold(),
                                properties.getInference().getCircuitBreaker().getCooldown()),
                        new DeterministicInferenceRequestFactory(DefaultSanitizer.defaults()),
                        renderer,
                        metrics,
                        properties);
        coordinatorMetrics.put(coordinator, metrics);
        coordinators.add(coordinator);
        return coordinator;
    }

    private final java.util.Map<DefaultAsyncLocalInferenceCoordinator, LocalInferenceMetrics>
            coordinatorMetrics = new java.util.IdentityHashMap<>();

    private LocalInferenceMetrics metrics(DefaultAsyncLocalInferenceCoordinator coordinator) {
        return coordinatorMetrics.get(coordinator);
    }

    private void awaitFailure(
            DefaultAsyncLocalInferenceCoordinator coordinator,
            LocalInferenceMetrics.FailureReason reason)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (metrics(coordinator).failureCount(reason) == 0 && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertThat(metrics(coordinator).failureCount(reason)).isGreaterThan(0);
    }

    private static ObjectProvider<LocalInferenceEngine> provider(LocalInferenceEngine engine) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (engine != null) {
            factory.addBean("engine", engine);
        }
        return factory.getBeanProvider(LocalInferenceEngine.class);
    }

    private static LocalInferenceEngine engine(InferenceOperation operation) {
        return new LocalInferenceEngine() {
            @Override
            public InferenceAvailability availability() {
                return InferenceAvailability.available();
            }

            @Override
            public ModelDescriptor model() {
                return new ModelDescriptor(
                        "test/local-model",
                        "Test Local Model",
                        "1",
                        new ModelCapabilities(true, true, true, 16_000, 8_192));
            }

            @Override
            public InferenceResult infer(InferenceRequest request) throws InterruptedException {
                return operation.infer(request);
            }

            @Override
            public void close() {}
        };
    }

    private static InferenceResult success(InferenceRequest request) {
        return new InferenceResult.Success(
                new StructuredInferenceOutput(
                        request.language(),
                        request.language() == InferenceLanguage.TURKISH
                                ? "Bu hata için yerel açıklama üretildi."
                                : "This local explanation uses supplied evidence.",
                        List.of(),
                        List.of()),
                Duration.ofMillis(5));
    }

    private static StackMateProperties properties() {
        StackMateProperties properties = new StackMateProperties();
        properties.setMode(StackMateProperties.Mode.HYBRID);
        properties.getInference().getExecutor().setThreads(1);
        properties.getInference().getExecutor().setQueueCapacity(1);
        return properties;
    }

    private static DeterministicResult result() {
        NullPointerException failure = new NullPointerException("password=hidden");
        failure.setStackTrace(
                new StackTraceElement[] {
                    new StackTraceElement("example.OrderService", "create", "OrderService.java", 42)
                });
        var throwable = ThrowableSnapshotter.defaults().capture(failure);
        StackFrameSnapshot frame = throwable.root().frames().getFirst();
        IncidentSnapshot incident =
                IncidentSnapshot.of("capture-1", Instant.now(), throwable, List.of(frame));
        return new DeterministicDiagnosticEngine(
                        DefaultDiagnosticAnalyzers.aggregator(), new IncidentFingerprinter())
                .analyze(incident);
    }

    @FunctionalInterface
    private interface InferenceOperation {
        InferenceResult infer(InferenceRequest request) throws InterruptedException;
    }

    private static final class RecordingRenderer implements LocalExplanationRenderer {
        private final CountDownLatch rendered;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<String> thread = new AtomicReference<>();

        private RecordingRenderer(CountDownLatch rendered) {
            this.rendered = rendered;
        }

        @Override
        public String render(
                ValidatedInferenceOutput output,
                ModelDescriptor model,
                Duration elapsed,
                RenderOptions options) {
            calls.incrementAndGet();
            thread.set(Thread.currentThread().getName());
            rendered.countDown();
            return "validated local output";
        }
    }
}
