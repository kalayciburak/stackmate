package io.github.kalayciburak.stackmate.inference;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/** Process-local counters for classified inference failures. */
public final class LocalInferenceMetrics {

    public enum FailureReason {
        UNAVAILABLE,
        QUEUE_REJECTED,
        BREAKER_OPEN,
        TIMED_OUT,
        INTERRUPTED,
        INVALID_OUTPUT,
        ENGINE_ERROR,
        RENDER_ERROR,
        SHUTDOWN
    }

    private final EnumMap<FailureReason, LongAdder> failures = new EnumMap<>(FailureReason.class);

    public LocalInferenceMetrics() {
        for (FailureReason reason : FailureReason.values()) {
            failures.put(reason, new LongAdder());
        }
    }

    public void recordFailure(FailureReason reason) {
        failures.get(reason).increment();
    }

    public long failureCount(FailureReason reason) {
        return failures.get(reason).sum();
    }

    public long totalFailures() {
        return failures.values().stream().mapToLong(LongAdder::sum).sum();
    }

    public Map<FailureReason, Long> snapshot() {
        EnumMap<FailureReason, Long> snapshot = new EnumMap<>(FailureReason.class);
        failures.forEach((reason, counter) -> snapshot.put(reason, counter.sum()));
        return Map.copyOf(snapshot);
    }
}
