package io.github.kalayciburak.stackmate.inference;

import java.util.Objects;

/** A safe request-relative reason why model output was rejected. */
public record OutputValidationIssue(Code code, String path, String message) {

    /** Stable output validation failure categories. */
    public enum Code {
        UNKNOWN_SCHEMA_VERSION,
        UNKNOWN_FACT_ID,
        UNKNOWN_CAUSE_ID,
        UNKNOWN_CHECK_ID,
        UNSUPPORTED_REFERENCE,
        MISSING_LIMITATION,
        CONTRADICTORY_SELECTION,
        EXCESSIVE_LENGTH,
        CONTROL_CHARACTER,
        SENSITIVE_VALUE,
        FABRICATED_SOURCE_REFERENCE,
        FABRICATED_IDENTIFIER,
        DESTRUCTIVE_COMMAND,
        MARKDOWN_FENCE,
        FORBIDDEN_CLAIM,
        OUTPUT_NOT_ENGLISH,
        DUPLICATE_SELECTION
    }

    public OutputValidationIssue {
        code = Objects.requireNonNull(code, "code");
        path = Objects.requireNonNull(path, "path");
        message = Objects.requireNonNull(message, "message");
    }
}
