package io.github.kalayciburak.stackmate.runtime;

import io.github.kalayciburak.stackmate.analysis.DeterministicResult;

/** Optional boundary that schedules local inference after deterministic output is rendered. */
public interface AsyncLocalInferenceCoordinator {

    /**
     * Schedules one reserved inference attempt without blocking the request thread.
     *
     * <p>The callback must be invoked when the attempt cannot be queued or cannot reach the engine.
     */
    void submit(DeterministicResult result, Runnable releaseReservation);
}
