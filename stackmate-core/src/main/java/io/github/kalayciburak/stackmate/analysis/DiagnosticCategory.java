package io.github.kalayciburak.stackmate.analysis;

/** Broad, framework-neutral categories used to group deterministic diagnoses. */
public enum DiagnosticCategory {
    GENERAL_JAVA,
    CONCURRENCY,
    RESOURCE_EXHAUSTION,
    SPRING_DEPENDENCY_INJECTION,
    SPRING_CONFIGURATION,
    WEB_REQUEST,
    VALIDATION,
    SERIALIZATION,
    DATABASE,
    DATABASE_CONSTRAINT,
    DATABASE_SCHEMA,
    DATABASE_CONNECTION,
    DATABASE_TRANSACTION,
    DATABASE_LOCKING,
    PERSISTENCE_STATE,
    AUTHENTICATION,
    AUTHORIZATION,
    DNS,
    TLS,
    NETWORK_CONNECTION,
    TIMEOUT,
    UNKNOWN
}
