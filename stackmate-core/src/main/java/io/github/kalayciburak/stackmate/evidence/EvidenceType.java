package io.github.kalayciburak.stackmate.evidence;

/** Identifies the kind of captured fact without tying the core to a framework type. */
public enum EvidenceType {
    EXCEPTION_CLASS,
    MESSAGE_PATTERN,
    SQL_STATE,
    DATABASE_VENDOR_CODE,
    DATABASE_CONSTRAINT,
    DATABASE_TABLE,
    DATABASE_COLUMN,
    PROPERTY_NAME,
    EXPECTED_TYPE,
    RECEIVED_VALUE,
    HANDLER_METHOD,
    REQUEST_PATH,
    WRAPPER_CLASS,
    SANITIZED_MESSAGE,
    APPLICATION_FRAME,
    CAUSE_RELATIONSHIP,
    ANALYZER_RESULT
}
