package io.github.kalayciburak.stackmate.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.evidence.EvidenceSensitivity;
import io.github.kalayciburak.stackmate.evidence.EvidenceType;
import io.github.kalayciburak.stackmate.throwable.ThrowableNode;
import org.junit.jupiter.api.Test;

class BuiltInAnalyzerTest {

    private final DiagnosisAggregator aggregator = DefaultDiagnosticAnalyzers.aggregator();

    @Test
    void diagnosesGeneralJavaFailureFromExactClass() {
        Diagnosis diagnosis =
                aggregator.diagnose(
                        IncidentFixtures.incident(
                                IncidentFixtures.node(
                                        "java.lang.NullPointerException", "sanitized", 0)));

        assertThat(diagnosis.causes())
                .anySatisfy(
                        cause -> {
                            assertThat(cause.id()).isEqualTo("cause.java.null-reference");
                            assertThat(cause.category()).isEqualTo(DiagnosticCategory.GENERAL_JAVA);
                            assertThat(cause.strength()).isEqualTo(DiagnosticStrength.HIGH);
                        });
    }

    @Test
    void diagnosesUnsupportedTimeoutAndRejectedExecutionBranches() {
        Diagnosis diagnosis =
                aggregator.diagnose(
                        IncidentFixtures.incident(
                                IncidentFixtures.node(
                                        "java.lang.UnsupportedOperationException", "sanitized", 0),
                                IncidentFixtures.node(
                                        "java.util.concurrent.TimeoutException", "sanitized", 1),
                                IncidentFixtures.node(
                                        "java.util.concurrent.RejectedExecutionException",
                                        "sanitized",
                                        2)));

        assertThat(diagnosis.causes())
                .extracting(DiagnosedCause::id)
                .contains(
                        "cause.java.unsupported-operation",
                        "cause.java.concurrent-timeout",
                        "cause.java.execution-rejected");
        assertThat(diagnosis.causes())
                .filteredOn(cause -> cause.id().equals("cause.java.concurrent-timeout"))
                .extracting(DiagnosedCause::category)
                .containsExactly(DiagnosticCategory.TIMEOUT);
        assertThat(diagnosis.causes())
                .filteredOn(cause -> cause.id().equals("cause.java.execution-rejected"))
                .extracting(DiagnosedCause::category)
                .containsExactly(DiagnosticCategory.RESOURCE_EXHAUSTION);
    }

    @Test
    void diagnosesSpringDependencyFailureByNameWithoutSpringDependency() {
        Diagnosis diagnosis =
                aggregator.diagnose(
                        IncidentFixtures.incident(
                                IncidentFixtures.node(
                                        "org.springframework.beans.factory.NoSuchBeanDefinitionException",
                                        "sanitized",
                                        0)));

        assertThat(diagnosis.causes())
                .anySatisfy(
                        cause -> {
                            assertThat(cause.id()).isEqualTo("cause.spring.missing-bean");
                            assertThat(cause.category())
                                    .isEqualTo(DiagnosticCategory.SPRING_DEPENDENCY_INJECTION);
                            assertThat(cause.strength()).isEqualTo(DiagnosticStrength.HIGH);
                        });
    }

    @Test
    void keepsSpringMessageOnlyDiagnosisLowAndDoesNotCopyRawMessage() {
        String sanitizedMessage =
                "Parameter 0 required a bean of type '[REDACTED]' that could not be found";
        Diagnosis diagnosis =
                aggregator.diagnose(
                        IncidentFixtures.incident(
                                IncidentFixtures.node(
                                        "java.lang.RuntimeException", sanitizedMessage, 0)));

        assertThat(diagnosis.causes())
                .anySatisfy(
                        cause -> {
                            assertThat(cause.id()).isEqualTo("cause.spring.missing-bean-message");
                            assertThat(cause.strength()).isEqualTo(DiagnosticStrength.LOW);
                        });
        assertThat(diagnosis.evidence())
                .filteredOn(item -> item.type() == EvidenceType.MESSAGE_PATTERN)
                .extracting(item -> item.value())
                .contains("required-bean-type")
                .doesNotContain(sanitizedMessage);
    }

    @Test
    void diagnosesSpringConversionInvalidPropertyAndConditionBranches() {
        Diagnosis diagnosis =
                aggregator.diagnose(
                        IncidentFixtures.incident(
                                IncidentFixtures.node(
                                        "org.springframework.core.convert.ConversionFailedException",
                                        "sanitized",
                                        0),
                                IncidentFixtures.node(
                                        "org.springframework.beans.NotWritablePropertyException",
                                        "sanitized",
                                        1),
                                IncidentFixtures.node(
                                        "java.lang.RuntimeException",
                                        "Error processing condition on example.Config",
                                        2)));

        assertThat(diagnosis.causes())
                .extracting(DiagnosedCause::id)
                .contains(
                        "cause.spring.configuration-conversion",
                        "cause.spring.property-not-writable",
                        "cause.spring.condition-evaluation-message");
        assertThat(diagnosis.causes())
                .filteredOn(cause -> cause.id().endsWith("condition-evaluation-message"))
                .extracting(DiagnosedCause::strength)
                .containsExactly(DiagnosticStrength.LOW);
    }

    @Test
    void diagnosesMvcValidationAndSerializationNames() {
        Diagnosis validation =
                aggregator.diagnose(
                        IncidentFixtures.incident(
                                IncidentFixtures.node(
                                        "org.springframework.web.bind.MethodArgumentNotValidException",
                                        "sanitized",
                                        0)));
        Diagnosis serialization =
                aggregator.diagnose(
                        IncidentFixtures.incident(
                                IncidentFixtures.node(
                                        "com.fasterxml.jackson.databind.exc.MismatchedInputException",
                                        "sanitized",
                                        0)));

        assertThat(validation.causes())
                .extracting(DiagnosedCause::category)
                .contains(DiagnosticCategory.VALIDATION);
        assertThat(serialization.causes())
                .extracting(DiagnosedCause::category)
                .contains(DiagnosticCategory.SERIALIZATION);
    }

    @Test
    void diagnosesMissingParameterResponseSerializationAndInvalidEnum() {
        Diagnosis diagnosis =
                aggregator.diagnose(
                        IncidentFixtures.incident(
                                IncidentFixtures.node(
                                        "org.springframework.web.bind.MissingServletRequestParameterException",
                                        "sanitized",
                                        0),
                                IncidentFixtures.node(
                                        "org.springframework.http.converter.HttpMessageNotWritableException",
                                        "sanitized",
                                        1),
                                IncidentFixtures.node(
                                        "java.lang.IllegalArgumentException",
                                        "No enum constant example.Status.UNKNOWN",
                                        2)));

        assertThat(diagnosis.causes())
                .extracting(DiagnosedCause::id)
                .contains(
                        "cause.web.missing-request-parameter",
                        "cause.web.response-body-unwritable",
                        "cause.web.enum-conversion-message");
        assertThat(diagnosis.causes())
                .filteredOn(cause -> cause.id().equals("cause.web.enum-conversion-message"))
                .extracting(DiagnosedCause::strength)
                .containsExactly(DiagnosticStrength.LOW);
    }

    @Test
    void coversWebMessagePatternAlternativesWithoutCopyingMessages() {
        Diagnosis diagnosis =
                aggregator.diagnose(
                        IncidentFixtures.incident(
                                IncidentFixtures.node(
                                        "java.lang.RuntimeException", "JSON parse error", 0),
                                IncidentFixtures.node(
                                        "java.lang.RuntimeException",
                                        "Validation failed for argument [0]",
                                        1),
                                IncidentFixtures.node(
                                        "java.lang.RuntimeException",
                                        "Failed to convert value to enum example.Status",
                                        2)));

        assertThat(diagnosis.causes())
                .extracting(DiagnosedCause::id)
                .contains(
                        "cause.web.json-parse-message",
                        "cause.web.validation-message",
                        "cause.web.enum-conversion-message");
        assertThat(diagnosis.evidence())
                .filteredOn(item -> item.type() == EvidenceType.MESSAGE_PATTERN)
                .allSatisfy(
                        item ->
                                assertThat(item.sensitivity())
                                        .isEqualTo(EvidenceSensitivity.PUBLIC));
    }

    @Test
    void traversesSqlCauseAndCapturesStructuredAndIdentifierEvidence() {
        ThrowableNode outer =
                IncidentFixtures.wrapper(
                        "java.util.concurrent.CompletionException", "sanitized wrapper", 0);
        ThrowableNode sql =
                IncidentFixtures.sqlNode(
                        "org.postgresql.util.PSQLException",
                        "duplicate key violates constraint \"uk_orders_ref\" on table \"orders\" column \"external_ref\"",
                        "23505",
                        0,
                        1);

        Diagnosis diagnosis = aggregator.diagnose(IncidentFixtures.incident(outer, sql));

        assertThat(diagnosis.causes())
                .anySatisfy(
                        cause -> {
                            assertThat(cause.id()).isEqualTo("cause.database.unique-constraint");
                            assertThat(cause.category())
                                    .isEqualTo(DiagnosticCategory.DATABASE_CONSTRAINT);
                            assertThat(cause.strength()).isEqualTo(DiagnosticStrength.HIGH);
                        });
        assertThat(diagnosis.evidence())
                .extracting(item -> item.type())
                .contains(
                        EvidenceType.SQL_STATE,
                        EvidenceType.DATABASE_CONSTRAINT,
                        EvidenceType.DATABASE_TABLE,
                        EvidenceType.DATABASE_COLUMN);
        assertThat(diagnosis.evidence())
                .filteredOn(item -> item.type() == EvidenceType.DATABASE_CONSTRAINT)
                .allSatisfy(
                        item ->
                                assertThat(item.sensitivity())
                                        .isEqualTo(EvidenceSensitivity.SENSITIVE));
    }

    @Test
    void capturesVendorCodeWithoutInventingVendorSpecificMeaning() {
        Diagnosis diagnosis =
                aggregator.diagnose(
                        IncidentFixtures.incident(
                                IncidentFixtures.sqlNode(
                                        "java.sql.SQLException",
                                        "sanitized database error",
                                        "",
                                        1062,
                                        0)));

        assertThat(diagnosis.evidence())
                .filteredOn(item -> item.type() == EvidenceType.DATABASE_VENDOR_CODE)
                .extracting(item -> item.value())
                .containsExactly("1062");
        assertThat(diagnosis.causes())
                .extracting(DiagnosedCause::id)
                .contains("cause.database.driver-error")
                .doesNotContain("cause.database.unique-constraint");
    }

    @Test
    void distinguishesSchemaLockingConnectionAndQuerySqlStates() {
        assertSqlState(
                "23503",
                "cause.database.foreign-key-constraint",
                DiagnosticCategory.DATABASE_CONSTRAINT);
        assertSqlState(
                "23502",
                "cause.database.not-null-constraint",
                DiagnosticCategory.DATABASE_CONSTRAINT);
        assertSqlState("42P01", "cause.database.missing-table", DiagnosticCategory.DATABASE_SCHEMA);
        assertSqlState(
                "42703", "cause.database.missing-column", DiagnosticCategory.DATABASE_SCHEMA);
        assertSqlState("42601", "cause.database.sql-syntax", DiagnosticCategory.DATABASE_SCHEMA);
        assertSqlState("40P01", "cause.database.deadlock", DiagnosticCategory.DATABASE_LOCKING);
        assertSqlState(
                "55P03", "cause.database.lock-not-available", DiagnosticCategory.DATABASE_LOCKING);
        assertSqlState(
                "08006",
                "cause.database.connection-exception",
                DiagnosticCategory.DATABASE_CONNECTION);
        assertSqlState("57014", "cause.database.query-canceled", DiagnosticCategory.TIMEOUT);
        assertSqlState("22000", "cause.database.data-exception", DiagnosticCategory.DATABASE);
        assertSqlState(
                "23000",
                "cause.database.integrity-constraint",
                DiagnosticCategory.DATABASE_CONSTRAINT);
        assertSqlState(
                "28000", "cause.database.authorization-specification", DiagnosticCategory.DATABASE);
        assertSqlState(
                "40001",
                "cause.database.transaction-rollback",
                DiagnosticCategory.DATABASE_TRANSACTION);
        assertSqlState(
                "42000", "cause.database.sql-syntax-or-access", DiagnosticCategory.DATABASE_SCHEMA);
        assertSqlState("HY000", "cause.database.sql-state", DiagnosticCategory.DATABASE);
    }

    @Test
    void diagnosesPersistenceClassNameFamiliesWithoutFrameworkImports() {
        assertCauseForType(
                "org.springframework.jdbc.BadSqlGrammarException",
                "cause.database.bad-sql-grammar");
        assertCauseForType(
                "com.zaxxer.hikari.pool.HikariPool$PoolInitializationException",
                "cause.database.pool-initialization");
        assertCauseForType(
                "jakarta.persistence.OptimisticLockException", "cause.database.optimistic-lock");
        assertCauseForType(
                "org.hibernate.exception.LockAcquisitionException",
                "cause.database.pessimistic-lock");
        assertCauseForType(
                "jakarta.persistence.TransactionRequiredException",
                "cause.database.transaction-required");
        assertCauseForType(
                "org.hibernate.LazyInitializationException", "cause.database.lazy-initialization");
        assertCauseForType(
                "jakarta.persistence.QueryTimeoutException", "cause.database.query-timeout");
    }

    @Test
    void keepsDatabaseMessagePatternsBoundedAndLowWithoutStructuredState() {
        assertMessageCause(
                "relation \"orders\" does not exist", "cause.database.missing-table-message");
        assertMessageCause(
                "column \"missing_col\" does not exist", "cause.database.missing-column-message");
        assertMessageCause("syntax error at or near select", "cause.database.sql-syntax-message");
        assertMessageCause("connection refused", "cause.database.connection-refused-message");
        assertMessageCause(
                "connection is not available, request timed out",
                "cause.database.pool-exhaustion-message");
        assertMessageCause("deadlock detected", "cause.database.deadlock-message");
        assertMessageCause("lock wait timeout exceeded", "cause.database.lock-unavailable-message");
        assertMessageCause(
                "no transaction is in progress", "cause.database.transaction-required-message");
        assertMessageCause(
                "could not initialize proxy - no session",
                "cause.database.lazy-initialization-message");
        assertMessageCause("query timed out", "cause.database.query-timeout-message");
        assertMessageCause(
                "unique constraint uk_order violated", "cause.database.unique-constraint-message");
        assertMessageCause(
                "table \"orders\" does not exist", "cause.database.missing-table-message");
        assertMessageCause("connection timed out", "cause.database.connection-timeout-message");
        assertMessageCause("connect timed out", "cause.database.connection-timeout-message");
        assertMessageCause("connection pool exhausted", "cause.database.pool-exhaustion-message");
        assertMessageCause(
                "connection pool acquisition timeout", "cause.database.pool-exhaustion-message");
        assertMessageCause(
                "transaction is required", "cause.database.transaction-required-message");
        assertMessageCause(
                "failed to lazily initialize collection",
                "cause.database.lazy-initialization-message");
        assertMessageCause("statement timeout", "cause.database.query-timeout-message");
    }

    @Test
    void diagnosesSpringSecurityName() {
        Diagnosis diagnosis =
                aggregator.diagnose(
                        IncidentFixtures.incident(
                                IncidentFixtures.node(
                                        "org.springframework.security.access.AccessDeniedException",
                                        "sanitized",
                                        0)));

        assertThat(diagnosis.causes())
                .anySatisfy(
                        cause -> {
                            assertThat(cause.id()).isEqualTo("cause.security.access-denied");
                            assertThat(cause.category())
                                    .isEqualTo(DiagnosticCategory.AUTHORIZATION);
                            assertThat(cause.strength()).isEqualTo(DiagnosticStrength.HIGH);
                        });
    }

    @Test
    void diagnosesExpiredAndMalformedTokenFamilies() {
        Diagnosis exact =
                aggregator.diagnose(
                        IncidentFixtures.incident(
                                IncidentFixtures.node(
                                        "io.jsonwebtoken.ExpiredJwtException", "sanitized", 0)));
        Diagnosis messageOnly =
                aggregator.diagnose(
                        IncidentFixtures.incident(
                                IncidentFixtures.node(
                                        "java.lang.RuntimeException",
                                        "JWT strings must contain exactly 2 period characters",
                                        0)));

        assertThat(exact.causes())
                .extracting(DiagnosedCause::id)
                .contains("cause.security.token-expired");
        assertThat(messageOnly.causes())
                .filteredOn(cause -> cause.id().equals("cause.security.malformed-token-message"))
                .extracting(DiagnosedCause::strength)
                .containsExactly(DiagnosticStrength.LOW);
    }

    @Test
    void wrapperDoesNotHideInfrastructureRootCause() {
        ThrowableNode wrapper =
                IncidentFixtures.wrapper(
                        "java.util.concurrent.CompletionException", "sanitized", 0);
        ThrowableNode root =
                IncidentFixtures.node("java.net.UnknownHostException", "api.invalid", 1);

        Diagnosis diagnosis = aggregator.diagnose(IncidentFixtures.incident(wrapper, root));

        assertThat(diagnosis.fallback()).isFalse();
        assertThat(diagnosis.causes())
                .anySatisfy(
                        cause -> {
                            assertThat(cause.id()).isEqualTo("cause.infrastructure.dns-resolution");
                            assertThat(cause.category()).isEqualTo(DiagnosticCategory.DNS);
                            assertThat(cause.strength()).isEqualTo(DiagnosticStrength.HIGH);
                        });
        assertThat(diagnosis.causes())
                .allSatisfy(
                        cause -> assertThat(cause.title()).doesNotContain("CompletionException"));
        assertThat(diagnosis.evidence())
                .extracting(item -> item.type())
                .contains(EvidenceType.WRAPPER_CLASS);
    }

    private void assertSqlState(
            String sqlState, String expectedCauseId, DiagnosticCategory expectedCategory) {
        Diagnosis diagnosis =
                aggregator.diagnose(
                        IncidentFixtures.incident(
                                IncidentFixtures.sqlNode(
                                        "java.sql.SQLException", "sanitized", sqlState, 0, 0)));
        assertThat(diagnosis.causes())
                .filteredOn(cause -> cause.id().equals(expectedCauseId))
                .singleElement()
                .satisfies(
                        cause -> {
                            assertThat(cause.category()).isEqualTo(expectedCategory);
                            assertThat(cause.strength()).isEqualTo(DiagnosticStrength.HIGH);
                        });
    }

    private void assertCauseForType(String type, String expectedCauseId) {
        Diagnosis diagnosis =
                aggregator.diagnose(
                        IncidentFixtures.incident(IncidentFixtures.node(type, "sanitized", 0)));
        assertThat(diagnosis.causes()).extracting(DiagnosedCause::id).contains(expectedCauseId);
    }

    private void assertMessageCause(String message, String expectedCauseId) {
        Diagnosis diagnosis =
                aggregator.diagnose(
                        IncidentFixtures.incident(
                                IncidentFixtures.sqlNode(
                                        "java.sql.SQLException", message, "", 0, 0)));
        assertThat(diagnosis.causes())
                .filteredOn(cause -> cause.id().equals(expectedCauseId))
                .singleElement()
                .satisfies(
                        cause ->
                                assertThat(cause.strength()).isNotEqualTo(DiagnosticStrength.HIGH));
        assertThat(diagnosis.evidence())
                .filteredOn(item -> item.type() == EvidenceType.MESSAGE_PATTERN)
                .allSatisfy(item -> assertThat(item.value()).doesNotContain(message));
    }
}
