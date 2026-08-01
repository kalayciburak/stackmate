package io.github.kalayciburak.stackmate.inference;

import java.util.Objects;

/** A bounded, safe description of a failed local inference attempt. */
public record InferenceFailure(Kind kind, String message, boolean retryable) {

    /** Stable failure categories independent of a particular engine. */
    public enum Kind {
        UNAVAILABLE,
        REJECTED,
        TIMED_OUT,
        INTERRUPTED,
        INVALID_OUTPUT,
        ENGINE_ERROR
    }

    public InferenceFailure {
        kind = Objects.requireNonNull(kind, "kind");
        message = Objects.requireNonNull(message, "message");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (message.length() > 512) {
            throw new IllegalArgumentException("message must not exceed 512 characters");
        }
    }
}
