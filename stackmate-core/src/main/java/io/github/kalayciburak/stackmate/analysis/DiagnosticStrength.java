package io.github.kalayciburak.stackmate.analysis;

/** Deterministic strength computed from supplied evidence. */
public enum DiagnosticStrength {
    /** Strength cannot be established from available evidence. */
    UNKNOWN,

    /** One weak or unstructured fact supports the diagnosis. */
    LOW,

    /** Multiple usable or independent facts support the diagnosis. */
    MEDIUM,

    /** A high-reliability structured fact supports the diagnosis. */
    HIGH
}
