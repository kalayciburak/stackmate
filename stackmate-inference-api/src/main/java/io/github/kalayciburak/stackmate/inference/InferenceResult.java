package io.github.kalayciburak.stackmate.inference;

import java.time.Duration;
import java.util.Objects;

/** The engine-level outcome of one local inference attempt. */
public sealed interface InferenceResult permits InferenceResult.Success, InferenceResult.Failed {

    /** Successful parsing into an untrusted structured output candidate. */
    record Success(StructuredInferenceOutput output, Duration elapsed) implements InferenceResult {
        public Success {
            output = Objects.requireNonNull(output, "output");
            elapsed = Objects.requireNonNull(elapsed, "elapsed");
            if (elapsed.isNegative()) {
                throw new IllegalArgumentException("elapsed must not be negative");
            }
        }
    }

    /** A failed inference attempt. */
    record Failed(InferenceFailure failure) implements InferenceResult {
        public Failed {
            failure = Objects.requireNonNull(failure, "failure");
        }
    }
}
