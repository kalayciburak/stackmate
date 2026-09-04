package io.github.kalayciburak.stackmate.analysis;

/** Broad, framework-neutral categories used to group deterministic diagnoses. */
public enum DiagnosticCategory {
    /** General Java language or runtime failure. */
    GENERAL_JAVA,
    /** Concurrent access or mutation failure. */
    CONCURRENCY,
    /** Memory, stack, thread, or executor capacity exhaustion. */
    RESOURCE_EXHAUSTION,
    /** Spring dependency-resolution or injection failure. */
    SPRING_DEPENDENCY_INJECTION,
    /** Spring application configuration failure. */
    SPRING_CONFIGURATION,
    /** HTTP request handling failure. */
    WEB_REQUEST,
    /** Input or bean validation failure. */
    VALIDATION,
    /** Serialization or deserialization failure. */
    SERIALIZATION,
    /** General database failure without a narrower classification. */
    DATABASE,
    /** Database integrity-constraint violation. */
    DATABASE_CONSTRAINT,
    /** Database schema, SQL grammar, table, or column mismatch. */
    DATABASE_SCHEMA,
    /** Database connectivity or connection-pool failure. */
    DATABASE_CONNECTION,
    /** Database transaction lifecycle failure. */
    DATABASE_TRANSACTION,
    /** Database lock acquisition or deadlock failure. */
    DATABASE_LOCKING,
    /** Persistence entity state or optimistic-locking failure. */
    PERSISTENCE_STATE,
    /** Authentication failure. */
    AUTHENTICATION,
    /** Authorization or access-control failure. */
    AUTHORIZATION,
    /** Domain name resolution failure. */
    DNS,
    /** TLS negotiation or certificate failure. */
    TLS,
    /** Network connection establishment or reset failure. */
    NETWORK_CONNECTION,
    /** Operation deadline or timeout failure. */
    TIMEOUT,
    /** Failure could not be classified deterministically. */
    UNKNOWN
}
