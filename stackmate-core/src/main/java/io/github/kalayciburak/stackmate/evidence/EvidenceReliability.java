package io.github.kalayciburak.stackmate.evidence;

/** Reliability assigned from the deterministic source of a fact, never from model confidence. */
public enum EvidenceReliability {
    /** The source provides weak or indirect deterministic support. */
    LOW,

    /** The source provides useful deterministic support. */
    MEDIUM,

    /** The source provides direct, structured deterministic support. */
    HIGH
}
