package io.github.kalayciburak.stackmate.inference;

import java.util.Objects;
import java.util.function.Supplier;

/** Fail-open lazy holder that defers local model validation and process setup until first use. */
public final class LazyLocalInferenceEngine implements LocalInferenceEngine {

    private final Supplier<LocalInferenceEngine> factory;
    private LocalInferenceEngine delegate;
    private boolean initializationFailed;
    private boolean closed;

    public LazyLocalInferenceEngine(Supplier<LocalInferenceEngine> factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    /** Initializes once while containing setup failures inside the optional inference boundary. */
    public synchronized void initialize() {
        delegate();
    }

    @Override
    public synchronized InferenceAvailability availability() {
        LocalInferenceEngine engine = delegate();
        if (engine == null) {
            return new InferenceAvailability(
                    InferenceAvailability.Status.UNAVAILABLE,
                    closed ? "local inference engine is closed" : "local model setup failed");
        }
        try {
            return engine.availability();
        } catch (RuntimeException failure) {
            return new InferenceAvailability(
                    InferenceAvailability.Status.UNAVAILABLE, "local model is unavailable");
        }
    }

    @Override
    public synchronized ModelDescriptor model() {
        LocalInferenceEngine engine = delegate();
        if (engine == null) {
            throw new IllegalStateException("local model is unavailable");
        }
        return engine.model();
    }

    @Override
    public synchronized InferenceResult infer(InferenceRequest request)
            throws InterruptedException {
        LocalInferenceEngine engine = delegate();
        if (engine == null) {
            return new InferenceResult.Failed(
                    new InferenceFailure(
                            InferenceFailure.Kind.UNAVAILABLE,
                            "local model is unavailable",
                            false));
        }
        return engine.infer(request);
    }

    private LocalInferenceEngine delegate() {
        if (closed || initializationFailed) {
            return null;
        }
        if (delegate == null) {
            try {
                delegate = Objects.requireNonNull(factory.get(), "local inference engine factory");
            } catch (RuntimeException | LinkageError setupFailure) {
                initializationFailed = true;
            }
        }
        return delegate;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (delegate != null) {
            delegate.close();
        }
    }
}
