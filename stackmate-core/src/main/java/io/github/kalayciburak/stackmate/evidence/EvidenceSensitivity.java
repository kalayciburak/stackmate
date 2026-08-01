package io.github.kalayciburak.stackmate.evidence;

/** Describes how carefully an evidence value must be handled at later output boundaries. */
public enum EvidenceSensitivity {
    PUBLIC,
    POTENTIALLY_SENSITIVE,
    SENSITIVE
}
