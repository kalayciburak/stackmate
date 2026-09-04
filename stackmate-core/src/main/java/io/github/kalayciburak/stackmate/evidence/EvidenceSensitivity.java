package io.github.kalayciburak.stackmate.evidence;

/** Describes how carefully an evidence value must be handled at later output boundaries. */
public enum EvidenceSensitivity {
    /** The value is safe for normal diagnostic output. */
    PUBLIC,

    /** The value may contain identifying or environment-specific information. */
    POTENTIALLY_SENSITIVE,

    /** The value requires strict handling and redaction at output boundaries. */
    SENSITIVE
}
