package io.github.kalayciburak.stackmate.evidence;

/** Identifies the kind of captured fact without tying the core to a framework type. */
public enum EvidenceType {
    /** Fully qualified throwable class name. */
    EXCEPTION_CLASS,
    /** Sanitized message fragment matched by a deterministic rule. */
    MESSAGE_PATTERN,
    /** Structured JDBC SQLState value. */
    SQL_STATE,
    /** Structured JDBC vendor error code. */
    DATABASE_VENDOR_CODE,
    /** Database constraint identifier. */
    DATABASE_CONSTRAINT,
    /** Database table identifier. */
    DATABASE_TABLE,
    /** Database column identifier. */
    DATABASE_COLUMN,
    /** Configuration or object property name. */
    PROPERTY_NAME,
    /** Expected Java or serialized value type. */
    EXPECTED_TYPE,
    /** Sanitized representation of a received value. */
    RECEIVED_VALUE,
    /** Application handler method identifier. */
    HANDLER_METHOD,
    /** Request path without its query string. */
    REQUEST_PATH,
    /** Throwable class identified as a wrapper. */
    WRAPPER_CLASS,
    /** Sanitized throwable or diagnostic message. */
    SANITIZED_MESSAGE,
    /** Selected application-owned stack frame. */
    APPLICATION_FRAME,
    /** Relationship between adjacent throwable nodes. */
    CAUSE_RELATIONSHIP,
    /** Stable result emitted by a deterministic analyzer. */
    ANALYZER_RESULT
}
