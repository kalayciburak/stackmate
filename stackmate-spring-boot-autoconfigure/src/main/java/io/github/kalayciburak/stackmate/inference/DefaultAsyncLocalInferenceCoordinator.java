package io.github.kalayciburak.stackmate.inference;

import io.github.kalayciburak.stackmate.analysis.DeterministicResult;
import io.github.kalayciburak.stackmate.configuration.InferenceProperties;
import io.github.kalayciburak.stackmate.configuration.StackMateProperties;
import io.github.kalayciburak.stackmate.output.RenderLanguage;
import io.github.kalayciburak.stackmate.output.RenderOptions;
import io.github.kalayciburak.stackmate.output.RenderStyle;
import io.github.kalayciburak.stackmate.runtime.AsyncLocalInferenceCoordinator;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.ObjectProvider;

/** Dedicated bounded executor boundary for optional local JVM inference. */
public final class DefaultAsyncLocalInferenceCoordinator
        implements AsyncLocalInferenceCoordinator, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("STACKMATE");
    private static final Marker MARKER = MarkerFactory.getMarker("STACKMATE");
    private static final Duration SHUTDOWN_GRACE = Duration.ofMillis(500);

    private final ObjectProvider<LocalInferenceEngine> engineProvider;
    private final InferenceOutputValidator outputValidator;
    private final InferenceCircuitBreaker circuitBreaker;
    private final DeterministicInferenceRequestFactory requestFactory;
    private final LocalExplanationRenderer renderer;
    private final LocalInferenceMetrics metrics;
    private final StackMateProperties properties;
    private final ThreadPoolExecutor executor;
    private final ScheduledThreadPoolExecutor timeoutExecutor;
    private final AtomicBoolean unavailableWarningLogged = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    public DefaultAsyncLocalInferenceCoordinator(
            ObjectProvider<LocalInferenceEngine> engineProvider,
            InferenceOutputValidator outputValidator,
            InferenceCircuitBreaker circuitBreaker,
            DeterministicInferenceRequestFactory requestFactory,
            LocalExplanationRenderer renderer,
            LocalInferenceMetrics metrics,
            StackMateProperties properties) {
        this.engineProvider = Objects.requireNonNull(engineProvider, "engineProvider");
        this.outputValidator = Objects.requireNonNull(outputValidator, "outputValidator");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
        this.requestFactory = Objects.requireNonNull(requestFactory, "requestFactory");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.properties = Objects.requireNonNull(properties, "properties");
        InferenceProperties.ExecutorProperties executorProperties =
                properties.getInference().getExecutor();
        this.executor =
                new ThreadPoolExecutor(
                        executorProperties.getThreads(),
                        executorProperties.getThreads(),
                        0,
                        TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(executorProperties.getQueueCapacity()),
                        daemonFactory("stackmate-local-inference-"),
                        new ThreadPoolExecutor.AbortPolicy());
        this.timeoutExecutor =
                new ScheduledThreadPoolExecutor(1, daemonFactory("stackmate-local-timeout-"));
        this.timeoutExecutor.setRemoveOnCancelPolicy(true);
        this.timeoutExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    @Override
    public void submit(DeterministicResult result, Runnable releaseReservation) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(releaseReservation, "releaseReservation");
        if (closed.get()) {
            metrics.recordFailure(LocalInferenceMetrics.FailureReason.SHUTDOWN);
            release(releaseReservation);
            return;
        }
        try {
            executor.execute(() -> attempt(result, releaseReservation));
        } catch (RejectedExecutionException rejected) {
            metrics.recordFailure(LocalInferenceMetrics.FailureReason.QUEUE_REJECTED);
            release(releaseReservation);
            debug(LocalInferenceMetrics.FailureReason.QUEUE_REJECTED);
        } catch (RuntimeException submissionFailure) {
            metrics.recordFailure(LocalInferenceMetrics.FailureReason.QUEUE_REJECTED);
            release(releaseReservation);
            debug(LocalInferenceMetrics.FailureReason.QUEUE_REJECTED);
        }
    }

    private void attempt(DeterministicResult result, Runnable releaseReservation) {
        LocalInferenceEngine engine;
        ModelDescriptor model;
        InferenceRequest request;
        try {
            engine = engineProvider.getIfAvailable();
            if (engine == null || !engine.availability().canInfer()) {
                startFailure(releaseReservation);
                return;
            }
            model = engine.model();
            InferenceLanguage language = language();
            if (!model.capabilities().structuredOutput()
                    || !model.capabilities().supports(language)) {
                startFailure(releaseReservation);
                return;
            }
            int maximumInput =
                    Math.min(
                            properties.getInference().getMaximumInputCharacters(),
                            model.capabilities().maximumInputCharacters());
            int maximumOutput =
                    Math.min(
                            outputCharacters(properties.getInference().getMaximumOutputTokens()),
                            model.capabilities().maximumOutputCharacters());
            request = requestFactory.create(result, language, maximumInput, maximumOutput);
        } catch (Throwable setupFailure) {
            preserveInterrupt(setupFailure);
            startFailure(releaseReservation);
            return;
        }

        InferenceCircuitBreaker.Permit permit = circuitBreaker.tryAcquire();
        if (!permit.granted()) {
            metrics.recordFailure(LocalInferenceMetrics.FailureReason.BREAKER_OPEN);
            debug(LocalInferenceMetrics.FailureReason.BREAKER_OPEN);
            return;
        }
        runWithTimeout(engine, model, request, permit);
    }

    private void runWithTimeout(
            LocalInferenceEngine engine,
            ModelDescriptor model,
            InferenceRequest request,
            InferenceCircuitBreaker.Permit permit) {
        AtomicBoolean completed = new AtomicBoolean();
        Thread worker = Thread.currentThread();
        ScheduledFuture<?> timeout;
        try {
            timeout =
                    timeoutExecutor.schedule(
                            () -> timeout(completed, permit, worker),
                            properties.getInference().getTimeout().toNanos(),
                            TimeUnit.NANOSECONDS);
        } catch (RejectedExecutionException shutdown) {
            fail(permit, LocalInferenceMetrics.FailureReason.SHUTDOWN);
            return;
        }
        try {
            InferenceResult result = engine.infer(request);
            if (completed.compareAndSet(false, true)) {
                handleResult(result, model, request, permit);
            }
        } catch (InterruptedException interrupted) {
            if (completed.compareAndSet(false, true)) {
                fail(permit, LocalInferenceMetrics.FailureReason.INTERRUPTED);
            }
            Thread.currentThread().interrupt();
        } catch (Throwable engineFailure) {
            preserveInterrupt(engineFailure);
            if (completed.compareAndSet(false, true)) {
                fail(permit, LocalInferenceMetrics.FailureReason.ENGINE_ERROR);
            }
        } finally {
            timeout.cancel(false);
        }
    }

    private void handleResult(
            InferenceResult result,
            ModelDescriptor model,
            InferenceRequest request,
            InferenceCircuitBreaker.Permit permit) {
        if (result instanceof InferenceResult.Failed failed) {
            fail(permit, classify(failed.failure().kind()));
            return;
        }
        InferenceResult.Success success = (InferenceResult.Success) result;
        OutputValidationResult validation = outputValidator.validate(request, success.output());
        if (validation instanceof OutputValidationResult.Invalid) {
            fail(permit, LocalInferenceMetrics.FailureReason.INVALID_OUTPUT);
            return;
        }
        circuitBreaker.recordSuccess(permit);
        if (!properties.getConsole().isEnabled()) {
            return;
        }
        try {
            ValidatedInferenceOutput output = ((OutputValidationResult.Valid) validation).output();
            String message = renderer.render(output, model, success.elapsed(), renderOptions());
            LOGGER.error(MARKER, "{}", System.lineSeparator() + message);
        } catch (Throwable renderFailure) {
            preserveInterrupt(renderFailure);
            metrics.recordFailure(LocalInferenceMetrics.FailureReason.RENDER_ERROR);
            debug(LocalInferenceMetrics.FailureReason.RENDER_ERROR);
        }
    }

    private void timeout(
            AtomicBoolean completed, InferenceCircuitBreaker.Permit permit, Thread worker) {
        if (completed.compareAndSet(false, true)) {
            fail(permit, LocalInferenceMetrics.FailureReason.TIMED_OUT);
            worker.interrupt();
        }
    }

    private void fail(
            InferenceCircuitBreaker.Permit permit, LocalInferenceMetrics.FailureReason reason) {
        circuitBreaker.recordFailure(permit);
        metrics.recordFailure(reason);
        debug(reason);
    }

    private void startFailure(Runnable releaseReservation) {
        metrics.recordFailure(LocalInferenceMetrics.FailureReason.UNAVAILABLE);
        release(releaseReservation);
        warnUnavailableOnce();
        debug(LocalInferenceMetrics.FailureReason.UNAVAILABLE);
    }

    private void warnUnavailableOnce() {
        if (unavailableWarningLogged.compareAndSet(false, true)) {
            LOGGER.warn(
                    MARKER,
                    "StackMate local inference is unavailable; configure a local JVM model. "
                            + "Deterministic diagnosis remains active.");
        }
    }

    private void debug(LocalInferenceMetrics.FailureReason reason) {
        if (properties.getConsole().isDebugLogging()) {
            LOGGER.debug(MARKER, "StackMate local inference skipped safely: {}", reason);
        }
    }

    private RenderOptions renderOptions() {
        return new RenderOptions(
                properties.getLanguage() == StackMateProperties.Language.TR
                        ? RenderLanguage.TURKISH
                        : RenderLanguage.ENGLISH,
                properties.getConsole().getStyle()
                                == io.github.kalayciburak.stackmate.configuration.ConsoleProperties
                                        .Style.COMPACT
                        ? RenderStyle.COMPACT
                        : RenderStyle.BOX,
                properties.getConsole().getMaximumWidth(),
                false,
                false,
                false);
    }

    private InferenceLanguage language() {
        return properties.getLanguage() == StackMateProperties.Language.TR
                ? InferenceLanguage.TURKISH
                : InferenceLanguage.ENGLISH;
    }

    private static int outputCharacters(int maximumOutputTokens) {
        long characters = (long) maximumOutputTokens * 4L;
        return (int) Math.min(characters, InferenceRequest.MAXIMUM_OUTPUT_CHARACTERS);
    }

    private static LocalInferenceMetrics.FailureReason classify(InferenceFailure.Kind kind) {
        return switch (kind) {
            case TIMED_OUT -> LocalInferenceMetrics.FailureReason.TIMED_OUT;
            case INTERRUPTED -> LocalInferenceMetrics.FailureReason.INTERRUPTED;
            case INVALID_OUTPUT -> LocalInferenceMetrics.FailureReason.INVALID_OUTPUT;
            case UNAVAILABLE, REJECTED, ENGINE_ERROR ->
                    LocalInferenceMetrics.FailureReason.ENGINE_ERROR;
        };
    }

    private static void release(Runnable releaseReservation) {
        try {
            releaseReservation.run();
        } catch (RuntimeException ignored) {
            // A release failure must not escape into the application request path.
        }
    }

    private static void preserveInterrupt(Throwable failure) {
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        executor.shutdown();
        timeoutExecutor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
            if (!timeoutExecutor.awaitTermination(
                    SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                timeoutExecutor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            timeoutExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
