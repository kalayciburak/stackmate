package io.github.kalayciburak.stackmate.configuration;

/** Controls when deterministic evidence may be sent to an optional local model. */
public enum InferenceTriggerPolicy {
    NEVER,
    ALWAYS,
    WHEN_ENHANCEMENT_IS_USEFUL
}
