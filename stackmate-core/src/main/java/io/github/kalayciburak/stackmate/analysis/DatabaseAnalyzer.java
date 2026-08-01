package io.github.kalayciburak.stackmate.analysis;

import io.github.kalayciburak.stackmate.evidence.EvidenceReliability;
import io.github.kalayciburak.stackmate.evidence.EvidenceSensitivity;
import io.github.kalayciburak.stackmate.evidence.EvidenceType;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.throwable.SqlExceptionDetails;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Diagnoses JDBC/database failures from captured SQLException facts and bounded message patterns.
 */
public final class DatabaseAnalyzer implements DiagnosticAnalyzer {

    public static final String ID = "database";

    private static final Pattern SQL_STATE = Pattern.compile("[A-Za-z0-9]{2,5}");
    private static final Pattern CONSTRAINT =
            Pattern.compile(
                    "(?i)(?:constraint|for key)\\s+[\\\"'`]?([A-Za-z0-9_.$-]{1,128})[\\\"'`]?");
    private static final Pattern TABLE =
            Pattern.compile("(?i)(?:relation|table)\\s+[\\\"'`]?([A-Za-z0-9_.$-]{1,128})[\\\"'`]?");
    private static final Pattern COLUMN =
            Pattern.compile("(?i)column\\s+[\\\"'`]?([A-Za-z0-9_.$-]{1,128})[\\\"'`]?");

    private static final List<PersistenceRule> PERSISTENCE_RULES =
            List.of(
                    persistenceRule(
                            "BadSqlGrammarException",
                            "bad-sql-grammar",
                            DiagnosticCategory.DATABASE_SCHEMA,
                            "The database rejected SQL grammar",
                            "The captured Spring JDBC exception type reports invalid SQL grammar.",
                            "Check generated SQL, identifiers, schema migrations, and database dialect."),
                    persistenceRule(
                            "SQLGrammarException",
                            "sql-grammar",
                            DiagnosticCategory.DATABASE_SCHEMA,
                            "The database rejected SQL grammar or an identifier",
                            "The captured Hibernate exception type reports SQL grammar failure.",
                            "Check generated SQL, table and column names, schema migrations, and dialect."),
                    persistenceRule(
                            "CannotGetJdbcConnectionException",
                            "jdbc-connection",
                            DiagnosticCategory.DATABASE_CONNECTION,
                            "A JDBC connection could not be obtained",
                            "The captured Spring JDBC exception type reports connection acquisition failure.",
                            "Check database reachability, pool state, credentials, and connection limits."),
                    persistenceRule(
                            "SQLTransientConnectionException",
                            "transient-connection",
                            DiagnosticCategory.DATABASE_CONNECTION,
                            "A transient JDBC connection failure occurred",
                            "The captured JDBC exception type reports a transient connection failure.",
                            "Check pool capacity, acquisition timeout, database limits, and downstream availability."),
                    persistenceRule(
                            "PoolInitializationException",
                            "pool-initialization",
                            DiagnosticCategory.DATABASE_CONNECTION,
                            "The database connection pool failed to initialize",
                            "The captured pool exception type reports initialization failure.",
                            "Check JDBC URL, driver, credentials, connectivity, and pool configuration."),
                    persistenceRule(
                            "OptimisticLockException",
                            "optimistic-lock",
                            DiagnosticCategory.DATABASE_LOCKING,
                            "An optimistic locking check failed",
                            "The captured persistence exception type reports optimistic lock failure.",
                            "Check the entity version and concurrent updates before retrying the operation."),
                    persistenceRule(
                            "OptimisticLockingFailureException",
                            "optimistic-lock",
                            DiagnosticCategory.DATABASE_LOCKING,
                            "An optimistic locking check failed",
                            "The captured persistence exception type reports optimistic lock failure.",
                            "Check the entity version and concurrent updates before retrying the operation."),
                    persistenceRule(
                            "ObjectOptimisticLockingFailureException",
                            "optimistic-lock",
                            DiagnosticCategory.DATABASE_LOCKING,
                            "An optimistic locking check failed",
                            "The captured persistence exception type reports optimistic lock failure.",
                            "Check the entity version and concurrent updates before retrying the operation."),
                    persistenceRule(
                            "StaleObjectStateException",
                            "optimistic-lock",
                            DiagnosticCategory.DATABASE_LOCKING,
                            "An optimistic locking check failed",
                            "The captured persistence exception type reports optimistic lock failure.",
                            "Check the entity version and concurrent updates before retrying the operation."),
                    persistenceRule(
                            "PessimisticLockException",
                            "pessimistic-lock",
                            DiagnosticCategory.DATABASE_LOCKING,
                            "A pessimistic database lock could not be obtained",
                            "The captured persistence exception type reports pessimistic lock failure.",
                            "Check lock ordering, lock timeout, transaction duration, and competing transactions."),
                    persistenceRule(
                            "PessimisticLockingFailureException",
                            "pessimistic-lock",
                            DiagnosticCategory.DATABASE_LOCKING,
                            "A pessimistic database lock could not be obtained",
                            "The captured persistence exception type reports pessimistic lock failure.",
                            "Check lock ordering, lock timeout, transaction duration, and competing transactions."),
                    persistenceRule(
                            "CannotAcquireLockException",
                            "pessimistic-lock",
                            DiagnosticCategory.DATABASE_LOCKING,
                            "A pessimistic database lock could not be obtained",
                            "The captured persistence exception type reports pessimistic lock failure.",
                            "Check lock ordering, lock timeout, transaction duration, and competing transactions."),
                    persistenceRule(
                            "LockAcquisitionException",
                            "pessimistic-lock",
                            DiagnosticCategory.DATABASE_LOCKING,
                            "A pessimistic database lock could not be obtained",
                            "The captured persistence exception type reports pessimistic lock failure.",
                            "Check lock ordering, lock timeout, transaction duration, and competing transactions."),
                    persistenceRule(
                            "TransactionRequiredException",
                            "transaction-required",
                            DiagnosticCategory.DATABASE_TRANSACTION,
                            "The persistence operation required an active transaction",
                            "The captured persistence exception type reports that a transaction is required.",
                            "Check transaction boundaries, propagation, proxy invocation, and thread handoff."),
                    persistenceRule(
                            "LazyInitializationException",
                            "lazy-initialization",
                            DiagnosticCategory.PERSISTENCE_STATE,
                            "Lazy state was accessed without an active persistence session",
                            "The captured Hibernate exception type reports lazy initialization failure.",
                            "Load the required data within the transaction or map it before leaving the persistence boundary."),
                    persistenceRule(
                            "QueryTimeoutException",
                            "query-timeout",
                            DiagnosticCategory.TIMEOUT,
                            "A database query exceeded its allowed time",
                            "The captured persistence exception type reports a query timeout.",
                            "Check the query plan, blocking locks, result size, and configured query timeout."));

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int order() {
        return 400;
    }

    @Override
    public AnalysisContribution analyze(IncidentSnapshot incident) {
        ContributionBuilder contribution = new ContributionBuilder(ID);
        for (AnalyzerSupport.NodeFact fact : AnalyzerSupport.nodes(incident.throwable())) {
            PersistenceRule persistenceRule = persistenceRule(fact.node().type());
            if (persistenceRule != null) {
                addPersistenceDiagnosis(contribution, fact, persistenceRule);
            }
            boolean databaseException = isDatabaseException(fact);
            if (!databaseException && persistenceRule == null) {
                continue;
            }
            List<String> identifiers =
                    databaseException ? addIdentifierEvidence(contribution, fact) : List.of();
            addMessageOnlyDiagnosis(contribution, fact, identifiers);
            if (!databaseException) {
                continue;
            }
            String classEvidenceId = contribution.addClassEvidence("jdbc", fact);
            SqlExceptionDetails sql = fact.node().sql();
            List<String> generalEvidence = new ArrayList<>();
            generalEvidence.add(classEvidenceId);
            generalEvidence.addAll(identifiers);
            if (isUsableSqlState(sql.sqlState())) {
                String stateEvidenceId =
                        contribution.addEvidence(
                                "jdbc.sql-state." + fact.ordinal(),
                                EvidenceType.SQL_STATE,
                                fact.path() + ".sql.sqlState",
                                sql.sqlState().toUpperCase(Locale.ROOT),
                                EvidenceSensitivity.PUBLIC,
                                EvidenceReliability.HIGH);
                generalEvidence.add(stateEvidenceId);
                addSqlStateDiagnosis(
                        contribution,
                        sql.sqlState().toUpperCase(Locale.ROOT),
                        withFirst(stateEvidenceId, identifiers));
            }
            if (sql.vendorCode() != 0) {
                generalEvidence.add(
                        contribution.addEvidence(
                                "jdbc.vendor-code." + fact.ordinal(),
                                EvidenceType.DATABASE_VENDOR_CODE,
                                fact.path() + ".sql.vendorCode",
                                Integer.toString(sql.vendorCode()),
                                EvidenceSensitivity.PUBLIC,
                                EvidenceReliability.HIGH));
            }
            contribution.addCause(
                    "cause.database.driver-error",
                    DiagnosticCategory.DATABASE,
                    "The database driver reported an error",
                    "A captured SQLException or structured SQL error was present in the cause chain.",
                    generalEvidence.toArray(String[]::new));
            contribution.addCheck(
                    "check.database.driver-error",
                    "Check the SQL state, vendor code, database logs, and the operation at the first application frame.",
                    generalEvidence.toArray(String[]::new));
        }
        return contribution.build();
    }

    private boolean isDatabaseException(AnalyzerSupport.NodeFact fact) {
        String type = fact.node().type();
        return fact.node().sql().present()
                || type.equals("java.sql.SQLException")
                || type.startsWith("java.sql.SQL")
                || type.equals("org.postgresql.util.PSQLException")
                || type.startsWith("com.mysql.cj.jdbc.exceptions.")
                || type.startsWith("oracle.jdbc.") && type.endsWith("Exception");
    }

    private PersistenceRule persistenceRule(String type) {
        if (!isPersistenceNamespace(type)) {
            return null;
        }
        int separator = Math.max(type.lastIndexOf('.'), type.lastIndexOf('$'));
        String simpleType = type.substring(separator + 1);
        for (PersistenceRule rule : PERSISTENCE_RULES) {
            if (simpleType.equals(rule.simpleType())) {
                return rule;
            }
        }
        return null;
    }

    private boolean isPersistenceNamespace(String type) {
        return type.startsWith("java.sql.")
                || type.startsWith("jakarta.persistence.")
                || type.startsWith("javax.persistence.")
                || type.startsWith("org.hibernate.")
                || type.startsWith("org.springframework.dao.")
                || type.startsWith("org.springframework.jdbc.")
                || type.startsWith("org.springframework.orm.")
                || type.startsWith("com.zaxxer.hikari.");
    }

    private void addPersistenceDiagnosis(
            ContributionBuilder contribution, AnalyzerSupport.NodeFact fact, PersistenceRule rule) {
        String evidenceId = contribution.addClassEvidence("database", fact);
        contribution.addCause(
                "cause.database." + rule.slug(),
                rule.category(),
                rule.title(),
                rule.explanation(),
                evidenceId);
        contribution.addCheck("check.database." + rule.slug(), rule.check(), evidenceId);
    }

    private boolean isUsableSqlState(String sqlState) {
        return sqlState != null && SQL_STATE.matcher(sqlState).matches();
    }

    private List<String> addIdentifierEvidence(
            ContributionBuilder contribution, AnalyzerSupport.NodeFact fact) {
        List<String> ids = new ArrayList<>();
        addFirstMatch(
                        contribution,
                        fact,
                        CONSTRAINT,
                        "constraint",
                        EvidenceType.DATABASE_CONSTRAINT)
                .ifPresent(ids::add);
        addFirstMatch(contribution, fact, TABLE, "table", EvidenceType.DATABASE_TABLE)
                .ifPresent(ids::add);
        addFirstMatch(contribution, fact, COLUMN, "column", EvidenceType.DATABASE_COLUMN)
                .ifPresent(ids::add);
        return List.copyOf(ids);
    }

    private java.util.Optional<String> addFirstMatch(
            ContributionBuilder contribution,
            AnalyzerSupport.NodeFact fact,
            Pattern pattern,
            String slug,
            EvidenceType type) {
        Matcher matcher = pattern.matcher(fact.node().message());
        if (!matcher.find()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(
                contribution.addEvidence(
                        "jdbc." + slug + "." + fact.ordinal(),
                        type,
                        fact.path() + ".message",
                        matcher.group(1),
                        EvidenceSensitivity.SENSITIVE,
                        EvidenceReliability.MEDIUM));
    }

    private void addSqlStateDiagnosis(
            ContributionBuilder contribution, String sqlState, List<String> evidenceIds) {
        StateRule rule = stateRule(sqlState);
        contribution.addCause(
                "cause.database." + rule.slug(),
                categoryFor(rule.slug()),
                rule.title(),
                rule.explanation(),
                evidenceIds.toArray(String[]::new));
        contribution.addCheck(
                "check.database." + rule.slug(), rule.check(), evidenceIds.toArray(String[]::new));
    }

    private StateRule stateRule(String sqlState) {
        if (sqlState.equals("23505")) {
            return new StateRule(
                    "unique-constraint",
                    "A database unique constraint was violated",
                    "The captured SQLState identifies a unique constraint violation.",
                    "Check the conflicting key and whether the operation should insert or update.");
        }
        if (sqlState.equals("23503")) {
            return new StateRule(
                    "foreign-key-constraint",
                    "A database foreign-key constraint was violated",
                    "The captured SQLState identifies a foreign-key constraint violation.",
                    "Check referenced rows and the order of related inserts, updates, or deletes.");
        }
        if (sqlState.equals("23502")) {
            return new StateRule(
                    "not-null-constraint",
                    "A required database column received null",
                    "The captured SQLState identifies a not-null constraint violation.",
                    "Check the mapped field and the value supplied before persistence.");
        }
        if (sqlState.equals("42P01")) {
            return new StateRule(
                    "missing-table",
                    "A referenced database table was not found",
                    "The captured SQLState identifies an undefined table.",
                    "Check the table name, active schema, search path, and applied migrations.");
        }
        if (sqlState.equals("42703")) {
            return new StateRule(
                    "missing-column",
                    "A referenced database column was not found",
                    "The captured SQLState identifies an undefined column.",
                    "Check the column name, entity mapping, generated SQL, and applied migrations.");
        }
        if (sqlState.equals("42601")) {
            return new StateRule(
                    "sql-syntax",
                    "The database rejected SQL syntax",
                    "The captured SQLState identifies a SQL syntax error.",
                    "Check generated SQL, database dialect, and the syntax near the reported failure.");
        }
        if (sqlState.equals("40P01")) {
            return new StateRule(
                    "deadlock",
                    "The database detected a deadlock",
                    "The captured SQLState identifies a deadlock.",
                    "Check lock ordering and keep competing transactions short before applying a bounded retry.");
        }
        if (sqlState.equals("55P03")) {
            return new StateRule(
                    "lock-not-available",
                    "A requested database lock was not available",
                    "The captured SQLState identifies unavailable lock state.",
                    "Check the competing transaction, lock mode, and lock timeout.");
        }
        if (sqlState.equals("57014")) {
            return new StateRule(
                    "query-canceled",
                    "The database canceled the query",
                    "The captured SQLState identifies a canceled query.",
                    "Check statement timeout, explicit cancellation, blocking locks, and query cost.");
        }
        return switch (sqlState.substring(0, Math.min(2, sqlState.length()))) {
            case "08" ->
                    new StateRule(
                            "connection-exception",
                            "The database connection failed",
                            "The captured SQLState belongs to the connection-exception class.",
                            "Check database reachability, listener port, connection pool, and credentials.");
            case "22" ->
                    new StateRule(
                            "data-exception",
                            "The database rejected a data value",
                            "The captured SQLState belongs to the data-exception class.",
                            "Check value format, numeric ranges, encoding, and target column types.");
            case "23" ->
                    new StateRule(
                            "integrity-constraint",
                            "A database integrity constraint was violated",
                            "The captured SQLState belongs to the integrity-constraint class.",
                            "Check the named constraint and the row values being written.");
            case "28" ->
                    new StateRule(
                            "authorization-specification",
                            "The database rejected authorization",
                            "The captured SQLState belongs to the invalid-authorization class.",
                            "Check the database principal, credential source, and granted privileges.");
            case "40" ->
                    new StateRule(
                            "transaction-rollback",
                            "The database rolled back the transaction",
                            "The captured SQLState belongs to the transaction-rollback class.",
                            "Check for serialization failures, deadlocks, and the transaction retry policy.");
            case "42" ->
                    new StateRule(
                            "sql-syntax-or-access",
                            "SQL syntax or database access rules rejected the operation",
                            "The captured SQLState belongs to the syntax-or-access-rule class.",
                            "Check generated SQL, schema names, migrations, and database privileges.");
            default ->
                    new StateRule(
                            "sql-state",
                            "The database returned a structured SQL state",
                            "A structured SQLState was captured from SQLException.",
                            "Look up the SQL state in the documentation for the configured database vendor.");
        };
    }

    private DiagnosticCategory categoryFor(String slug) {
        if (slug.contains("constraint")) {
            return DiagnosticCategory.DATABASE_CONSTRAINT;
        }
        if (slug.equals("connection-exception")) {
            return DiagnosticCategory.DATABASE_CONNECTION;
        }
        if (slug.equals("transaction-rollback")) {
            return DiagnosticCategory.DATABASE_TRANSACTION;
        }
        if (slug.equals("deadlock") || slug.equals("lock-not-available")) {
            return DiagnosticCategory.DATABASE_LOCKING;
        }
        if (slug.equals("query-canceled")) {
            return DiagnosticCategory.TIMEOUT;
        }
        if (slug.equals("sql-syntax-or-access")
                || slug.equals("sql-syntax")
                || slug.equals("missing-table")
                || slug.equals("missing-column")) {
            return DiagnosticCategory.DATABASE_SCHEMA;
        }
        return DiagnosticCategory.DATABASE;
    }

    private void addMessageOnlyDiagnosis(
            ContributionBuilder contribution,
            AnalyzerSupport.NodeFact fact,
            List<String> identifierEvidence) {
        boolean noSqlState = fact.node().sql().sqlState().isEmpty();
        if (noSqlState
                && (AnalyzerSupport.messageContains(fact.node(), "duplicate key")
                        || AnalyzerSupport.messageContains(fact.node(), "unique constraint"))) {
            addPatternDiagnosis(
                    contribution,
                    fact,
                    identifierEvidence,
                    "unique-constraint",
                    DiagnosticCategory.DATABASE_CONSTRAINT,
                    "A database unique constraint may have been violated",
                    "The captured message matches a unique-constraint pattern without structured SQLState evidence.",
                    "Check the conflicting key and the database constraint named by the driver.");
        }
        if (noSqlState
                && AnalyzerSupport.messageContains(fact.node(), "does not exist")
                && (AnalyzerSupport.messageContains(fact.node(), "relation")
                        || AnalyzerSupport.messageContains(fact.node(), "table"))) {
            addPatternDiagnosis(
                    contribution,
                    fact,
                    identifierEvidence,
                    "missing-table",
                    DiagnosticCategory.DATABASE_SCHEMA,
                    "A referenced database table may be missing",
                    "The captured message matches a missing-table pattern without structured SQLState evidence.",
                    "Check the table name, active schema, search path, and applied migrations.");
        }
        if (noSqlState
                && AnalyzerSupport.messageContains(fact.node(), "column")
                && AnalyzerSupport.messageContains(fact.node(), "does not exist")) {
            addPatternDiagnosis(
                    contribution,
                    fact,
                    identifierEvidence,
                    "missing-column",
                    DiagnosticCategory.DATABASE_SCHEMA,
                    "A referenced database column may be missing",
                    "The captured message matches a missing-column pattern without structured SQLState evidence.",
                    "Check the column name, entity mapping, generated SQL, and applied migrations.");
        }
        if (noSqlState && AnalyzerSupport.messageContains(fact.node(), "syntax error")) {
            addPatternDiagnosis(
                    contribution,
                    fact,
                    identifierEvidence,
                    "sql-syntax",
                    DiagnosticCategory.DATABASE_SCHEMA,
                    "The database may have rejected SQL syntax",
                    "The captured message matches a SQL syntax-error pattern without structured SQLState evidence.",
                    "Check generated SQL, database dialect, and the syntax near the reported failure.");
        }
        if (noSqlState && AnalyzerSupport.messageContains(fact.node(), "connection refused")) {
            addPatternDiagnosis(
                    contribution,
                    fact,
                    identifierEvidence,
                    "connection-refused",
                    DiagnosticCategory.DATABASE_CONNECTION,
                    "The database endpoint may have refused the connection",
                    "The captured database message matches a connection-refused pattern.",
                    "Check the database host, port, listener, firewall, and service availability.");
        }
        if (noSqlState
                && (AnalyzerSupport.messageContains(fact.node(), "connection timed out")
                        || AnalyzerSupport.messageContains(fact.node(), "connect timed out"))) {
            addPatternDiagnosis(
                    contribution,
                    fact,
                    identifierEvidence,
                    "connection-timeout",
                    DiagnosticCategory.DATABASE_CONNECTION,
                    "Database connection establishment may have timed out",
                    "The captured database message matches a connection-timeout pattern.",
                    "Check network latency, listener availability, and the configured connect timeout.");
        }
        if (noSqlState
                && (AnalyzerSupport.messageContains(fact.node(), "connection is not available")
                        || AnalyzerSupport.messageContains(fact.node(), "connection pool")
                                && (AnalyzerSupport.messageContains(fact.node(), "exhausted")
                                        || AnalyzerSupport.messageContains(
                                                fact.node(), "timeout")))) {
            addPatternDiagnosis(
                    contribution,
                    fact,
                    identifierEvidence,
                    "pool-exhaustion",
                    DiagnosticCategory.DATABASE_CONNECTION,
                    "The database connection pool may be exhausted",
                    "The captured message matches a connection-pool exhaustion or acquisition-timeout pattern.",
                    "Check leaked connections, pool capacity, acquisition timeout, and database connection limits.");
        }
        if (noSqlState && AnalyzerSupport.messageContains(fact.node(), "deadlock")) {
            addPatternDiagnosis(
                    contribution,
                    fact,
                    identifierEvidence,
                    "deadlock",
                    DiagnosticCategory.DATABASE_LOCKING,
                    "The database may have detected a deadlock",
                    "The captured message matches a database deadlock pattern.",
                    "Check lock ordering and transaction duration before applying a bounded retry.");
        }
        if (noSqlState
                && (AnalyzerSupport.messageContains(fact.node(), "could not obtain lock")
                        || AnalyzerSupport.messageContains(fact.node(), "lock wait timeout"))) {
            addPatternDiagnosis(
                    contribution,
                    fact,
                    identifierEvidence,
                    "lock-unavailable",
                    DiagnosticCategory.DATABASE_LOCKING,
                    "A required database lock may have been unavailable",
                    "The captured message matches a lock acquisition or lock-timeout pattern.",
                    "Check competing transactions, lock ordering, transaction duration, and lock timeout.");
        }
        if (noSqlState
                && (AnalyzerSupport.messageContains(fact.node(), "transaction is required")
                        || AnalyzerSupport.messageContains(
                                fact.node(), "no transaction is in progress"))) {
            addPatternDiagnosis(
                    contribution,
                    fact,
                    identifierEvidence,
                    "transaction-required",
                    DiagnosticCategory.DATABASE_TRANSACTION,
                    "The persistence operation may require an active transaction",
                    "The captured message matches a transaction-required pattern.",
                    "Check transaction boundaries, propagation, proxy invocation, and thread handoff.");
        }
        if (noSqlState
                && (AnalyzerSupport.messageContains(fact.node(), "failed to lazily initialize")
                        || AnalyzerSupport.messageContains(
                                fact.node(), "could not initialize proxy - no session"))) {
            addPatternDiagnosis(
                    contribution,
                    fact,
                    identifierEvidence,
                    "lazy-initialization",
                    DiagnosticCategory.PERSISTENCE_STATE,
                    "Lazy state may have been accessed without an active session",
                    "The captured message matches a lazy-initialization pattern.",
                    "Load required data within the transaction or map it before leaving the persistence boundary.");
        }
        if (noSqlState
                && (AnalyzerSupport.messageContains(fact.node(), "query timed out")
                        || AnalyzerSupport.messageContains(fact.node(), "statement timeout"))) {
            addPatternDiagnosis(
                    contribution,
                    fact,
                    identifierEvidence,
                    "query-timeout",
                    DiagnosticCategory.TIMEOUT,
                    "The database query may have exceeded its allowed time",
                    "The captured message matches a query-timeout pattern.",
                    "Check the query plan, blocking locks, result size, and configured query timeout.");
        }
    }

    private void addPatternDiagnosis(
            ContributionBuilder contribution,
            AnalyzerSupport.NodeFact fact,
            List<String> identifierEvidence,
            String slug,
            DiagnosticCategory category,
            String title,
            String explanation,
            String check) {
        String patternId = contribution.addMessagePatternEvidence("jdbc", fact, slug);
        List<String> ids = withFirst(patternId, identifierEvidence);
        contribution.addCause(
                "cause.database." + slug + "-message",
                category,
                title,
                explanation,
                ids.toArray(String[]::new));
        contribution.addCheck(
                "check.database." + slug + "-message", check, ids.toArray(String[]::new));
    }

    private List<String> withFirst(String first, List<String> rest) {
        List<String> result = new ArrayList<>(rest.size() + 1);
        result.add(first);
        result.addAll(rest);
        return List.copyOf(result);
    }

    private static PersistenceRule persistenceRule(
            String simpleType,
            String slug,
            DiagnosticCategory category,
            String title,
            String explanation,
            String check) {
        return new PersistenceRule(simpleType, slug, category, title, explanation, check);
    }

    private record StateRule(String slug, String title, String explanation, String check) {}

    private record PersistenceRule(
            String simpleType,
            String slug,
            DiagnosticCategory category,
            String title,
            String explanation,
            String check) {}
}
