package io.github.kalayciburak.stackmate.inference;

import java.util.List;
import java.util.Objects;

/** The explicit trust-boundary result of validating structured model output. */
public sealed interface OutputValidationResult
        permits OutputValidationResult.Valid, OutputValidationResult.Invalid {

    /** A trusted output that passed every request-scoped validation rule. */
    record Valid(ValidatedInferenceOutput output) implements OutputValidationResult {
        public Valid {
            output = Objects.requireNonNull(output, "output");
        }
    }

    /** An invalid output with one or more safe rejection reasons. */
    record Invalid(List<OutputValidationIssue> issues) implements OutputValidationResult {
        public Invalid {
            issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
            if (issues.isEmpty()) {
                throw new IllegalArgumentException("issues must not be empty");
            }
        }
    }
}
