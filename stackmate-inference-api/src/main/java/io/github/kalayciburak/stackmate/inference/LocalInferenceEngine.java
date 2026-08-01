package io.github.kalayciburak.stackmate.inference;

/** An engine-neutral boundary for bounded local model inference. */
public interface LocalInferenceEngine extends AutoCloseable {

    /** Returns a point-in-time availability observation without loading model output. */
    InferenceAvailability availability();

    /** Returns the configured model descriptor. */
    ModelDescriptor model();

    /** Performs one bounded inference attempt. */
    InferenceResult infer(InferenceRequest request) throws InterruptedException;

    /** Releases engine resources without throwing checked exceptions. */
    @Override
    void close();
}
