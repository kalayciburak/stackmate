package io.github.kalayciburak.stackmate.inference;

import java.util.Objects;

/** A point-in-time engine availability observation. */
public record InferenceAvailability(Status status, String detail) {

    /** Engine availability states. */
    public enum Status {
        AVAILABLE,
        UNAVAILABLE,
        DEGRADED
    }

    public InferenceAvailability {
        status = Objects.requireNonNull(status, "status");
        detail = Objects.requireNonNull(detail, "detail");
    }

    /** Creates an available observation with no additional detail. */
    public static InferenceAvailability available() {
        return new InferenceAvailability(Status.AVAILABLE, "");
    }

    /** Returns whether new inference work may currently be attempted. */
    public boolean canInfer() {
        return status != Status.UNAVAILABLE;
    }
}
