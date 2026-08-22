package io.github.kalayciburak.stackmate.benchmark.corpus.realworld;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;

/**
 * Opt-in PostgreSQL fixtures for the G53 real-world corpus.
 *
 * <p>This class deliberately owns neither a PostgreSQL container nor credentials. Callers supply
 * connections and decide which families and variants to execute.
 */
public final class RealWorldPostgresFixtures {

    private static final String DEFAULT_CATALOG = null;
    private static final int JDBC_QUERY_TIMEOUT_SECONDS = 4;

    private static final Map<String, String> EXPECTED_STATES =
            Map.ofEntries(
                    Map.entry("G53-R-PG-001", "23514"),
                    Map.entry("G53-R-PG-002", "22001"),
                    Map.entry("G53-R-PG-003", "22003"),
                    Map.entry("G53-R-PG-004", "22012"),
                    Map.entry("G53-R-PG-005", "22P02"),
                    Map.entry("G53-R-PG-006", "22008"),
                    Map.entry("G53-R-PG-007", "2201B"),
                    Map.entry("G53-R-PG-008", "21000"),
                    Map.entry("G53-R-PG-009", "42501"),
                    Map.entry("G53-R-PG-010", "42P07"),
                    Map.entry("G53-R-PG-011", "42701"),
                    Map.entry("G53-R-PG-012", "42883"),
                    Map.entry("G53-R-PG-013", "42804"),
                    Map.entry("G53-R-PG-014", "42803"),
                    Map.entry("G53-R-PG-015", "42702"),
                    Map.entry("G53-R-PG-016", "25006"),
                    Map.entry("G53-R-PG-017", "25P02"),
                    Map.entry("G53-R-PG-018", "3B001"),
                    Map.entry("G53-R-PG-019", "55P03"),
                    Map.entry("G53-R-PG-020", "57014"),
                    Map.entry("G53-R-PG-021", "2BP01"),
                    Map.entry("G53-R-PG-022", "3D000"),
                    Map.entry("G53-R-PG-023", "3F000"),
                    Map.entry("G53-R-PG-024", "42P05"),
                    Map.entry("G53-R-PG-025", "42P03"),
                    Map.entry("G53-R-PG-026", "34000"),
                    Map.entry("G53-R-PG-027", "22023"),
                    Map.entry("G53-R-PG-028", "22021"),
                    Map.entry("G53-R-PG-029", "428C9"),
                    Map.entry("G53-R-PG-030", "2200H"));

    private RealWorldPostgresFixtures() {}

    public static boolean supports(String familyId) {
        return familyId != null && EXPECTED_STATES.containsKey(familyId);
    }

    /**
     * Executes a fixture against the data source's configured database.
     *
     * <p>Family G53-R-PG-022 needs {@link ConnectionFactory} because JDBC {@link DataSource} has no
     * portable operation for opening the same server credentials against another database.
     */
    public static Throwable execute(String familyId, int variant, DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        return execute(
                familyId,
                variant,
                catalog -> {
                    if (catalog != null) {
                        throw new HarnessException(
                                familyId
                                        + " requires ConnectionFactory.open(catalog); a DataSource "
                                        + "cannot portably retarget a PostgreSQL catalog");
                    }
                    return dataSource.getConnection();
                });
    }

    /**
     * Executes one explicitly selected family and variant.
     *
     * <p>The factory receives {@code null} for its configured/default database. G53-R-PG-022 also
     * receives a deterministic absent database name and must attempt a fresh connection to that
     * database. Returned connections are closed by this fixture; the factory/container lifecycle
     * remains caller-owned.
     */
    public static Throwable execute(
            String familyId, int variant, ConnectionFactory connectionFactory) {
        Objects.requireNonNull(familyId, "familyId");
        String expectedState = EXPECTED_STATES.get(familyId);
        if (expectedState == null) {
            throw new IllegalArgumentException("unsupported PostgreSQL family: " + familyId);
        }
        if (variant < 0) {
            throw new IllegalArgumentException("variant must be non-negative: " + variant);
        }
        Objects.requireNonNull(connectionFactory, "connectionFactory");

        String schema = schemaName(familyId, variant);
        SQLException result = null;
        RuntimeException primaryFailure = null;
        try {
            prepareSchema(connectionFactory, familyId, schema);
            result = executeResolved(familyId, expectedState, schema, connectionFactory);
            return result;
        } catch (RuntimeException failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            try {
                cleanupSchema(connectionFactory, familyId, schema);
            } catch (RuntimeException cleanupFailure) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure);
                } else {
                    if (result != null) {
                        cleanupFailure.addSuppressed(result);
                    }
                    throw cleanupFailure;
                }
            }
        }
    }

    private static SQLException executeResolved(
            String familyId,
            String expectedState,
            String schema,
            ConnectionFactory connectionFactory) {
        return switch (familyId) {
            case "G53-R-PG-019" ->
                    rowLockNowait(familyId, expectedState, schema, connectionFactory);
            case "G53-R-PG-022" ->
                    invalidCatalog(familyId, expectedState, schema, connectionFactory);
            default ->
                    withDefaultConnection(
                            familyId,
                            schema,
                            connectionFactory,
                            connection ->
                                    executeSingleConnection(
                                            familyId, expectedState, schema, connection));
        };
    }

    private static SQLException executeSingleConnection(
            String familyId, String expectedState, String schema, Connection connection) {
        String records = qualified(schema, "records");
        return switch (familyId) {
            case "G53-R-PG-001" -> {
                setup(
                        familyId,
                        connection,
                        "CREATE TABLE "
                                + records
                                + " (value integer CONSTRAINT "
                                + quoteIdentifier("positive_value")
                                + " CHECK (value > 0))");
                yield expectFailure(
                        familyId,
                        expectedState,
                        () -> executeUpdate(connection, "INSERT INTO " + records + " VALUES (-1)"));
            }
            case "G53-R-PG-002" -> {
                setup(familyId, connection, "CREATE TABLE " + records + " (value varchar(4))");
                yield expectFailure(
                        familyId,
                        expectedState,
                        () ->
                                executePrepared(
                                        connection,
                                        "INSERT INTO " + records + " VALUES (?)",
                                        "alpha"));
            }
            case "G53-R-PG-003" ->
                    expectFailure(
                            familyId,
                            expectedState,
                            () -> executeQuery(connection, "SELECT CAST(1000 AS numeric(2, 0))"));
            case "G53-R-PG-004" ->
                    expectFailure(
                            familyId,
                            expectedState,
                            () -> executeQuery(connection, "SELECT 8 / 0"));
            case "G53-R-PG-005" ->
                    expectFailure(
                            familyId,
                            expectedState,
                            () ->
                                    executePrepared(
                                            connection, "SELECT CAST(? AS integer)", "sevenx"));
            case "G53-R-PG-006" -> {
                setup(familyId, connection, "SET DateStyle TO ISO");
                yield expectFailure(
                        familyId,
                        expectedState,
                        () -> executePrepared(connection, "SELECT CAST(? AS date)", "2026-02-30"));
            }
            case "G53-R-PG-007" ->
                    expectFailure(
                            familyId,
                            expectedState,
                            () -> executePrepared(connection, "SELECT ? ~ ?", "marker", "["));
            case "G53-R-PG-008" -> {
                setup(
                        familyId,
                        connection,
                        "CREATE TABLE " + records + " (marker integer)",
                        "INSERT INTO " + records + " VALUES (1), (2)");
                yield expectFailure(
                        familyId,
                        expectedState,
                        () ->
                                executeQuery(
                                        connection, "SELECT (SELECT marker FROM " + records + ")"));
            }
            case "G53-R-PG-009" -> {
                String role = schema + "_role";
                setup(
                        familyId,
                        connection,
                        "CREATE TABLE " + records + " (marker integer)",
                        "INSERT INTO " + records + " VALUES (1)");
                beginTransaction(familyId, connection);
                setup(
                        familyId,
                        connection,
                        "CREATE ROLE " + quoteIdentifier(role) + " NOLOGIN",
                        "GRANT " + quoteIdentifier(role) + " TO CURRENT_USER",
                        "GRANT USAGE ON SCHEMA "
                                + quoteIdentifier(schema)
                                + " TO "
                                + quoteIdentifier(role),
                        "SET LOCAL ROLE " + quoteIdentifier(role));
                yield expectFailure(
                        familyId,
                        expectedState,
                        () -> executeQuery(connection, "SELECT marker FROM " + records));
            }
            case "G53-R-PG-010" -> {
                String sql = "CREATE TABLE " + records + " (marker integer)";
                setup(familyId, connection, sql);
                yield expectFailure(familyId, expectedState, () -> executeUpdate(connection, sql));
            }
            case "G53-R-PG-011" -> {
                setup(familyId, connection, "CREATE TABLE " + records + " (marker integer)");
                yield expectFailure(
                        familyId,
                        expectedState,
                        () ->
                                executeUpdate(
                                        connection,
                                        "ALTER TABLE "
                                                + records
                                                + " ADD COLUMN "
                                                + quoteIdentifier("marker")
                                                + " integer"));
            }
            case "G53-R-PG-012" ->
                    expectFailure(
                            familyId,
                            expectedState,
                            () ->
                                    executeQuery(
                                            connection,
                                            "SELECT "
                                                    + qualified(schema, "absent_function")
                                                    + "(1)"));
            case "G53-R-PG-013" -> {
                setup(familyId, connection, "CREATE TABLE " + records + " (created_at timestamp)");
                yield expectFailure(
                        familyId,
                        expectedState,
                        () ->
                                executeUpdate(
                                        connection, "INSERT INTO " + records + " VALUES (TRUE)"));
            }
            case "G53-R-PG-014" -> {
                setup(
                        familyId,
                        connection,
                        "CREATE TABLE " + records + " (group_key integer, value_text text)");
                yield expectFailure(
                        familyId,
                        expectedState,
                        () ->
                                executeQuery(
                                        connection,
                                        "SELECT value_text, count(*) FROM "
                                                + records
                                                + " GROUP BY group_key"));
            }
            case "G53-R-PG-015" -> {
                String left = qualified(schema, "left_rows");
                String right = qualified(schema, "right_rows");
                setup(
                        familyId,
                        connection,
                        "CREATE TABLE " + left + " (id integer)",
                        "CREATE TABLE " + right + " (id integer)");
                yield expectFailure(
                        familyId,
                        expectedState,
                        () ->
                                executeQuery(
                                        connection,
                                        "SELECT id FROM " + left + " JOIN " + right + " ON TRUE"));
            }
            case "G53-R-PG-016" -> {
                setup(familyId, connection, "CREATE TABLE " + records + " (marker integer)");
                beginTransaction(familyId, connection);
                setup(familyId, connection, "SET TRANSACTION READ ONLY");
                yield expectFailure(
                        familyId,
                        expectedState,
                        () -> executeUpdate(connection, "INSERT INTO " + records + " VALUES (1)"));
            }
            case "G53-R-PG-017" -> {
                beginTransaction(familyId, connection);
                expectFailure(familyId, "22012", () -> executeQuery(connection, "SELECT 8 / 0"));
                yield expectFailure(
                        familyId, expectedState, () -> executeQuery(connection, "SELECT 1"));
            }
            case "G53-R-PG-018" -> {
                beginTransaction(familyId, connection);
                yield expectFailure(
                        familyId,
                        expectedState,
                        () ->
                                executeUpdate(
                                        connection,
                                        "ROLLBACK TO SAVEPOINT "
                                                + quoteIdentifier("reserved_savepoint")));
            }
            case "G53-R-PG-020" -> {
                beginTransaction(familyId, connection);
                setup(familyId, connection, "SET LOCAL statement_timeout = '100ms'");
                yield expectFailure(
                        familyId,
                        expectedState,
                        () -> executeQuery(connection, "SELECT pg_sleep(1.0)"));
            }
            case "G53-R-PG-021" -> {
                String source = qualified(schema, "source_rows");
                String view = qualified(schema, "source_view");
                setup(
                        familyId,
                        connection,
                        "CREATE TABLE " + source + " (marker integer)",
                        "CREATE VIEW " + view + " AS SELECT marker FROM " + source);
                yield expectFailure(
                        familyId,
                        expectedState,
                        () -> executeUpdate(connection, "DROP TABLE " + source));
            }
            case "G53-R-PG-023" -> {
                String absentSchema = schema + "_absent";
                assertSchemaAbsent(familyId, connection, absentSchema);
                yield expectFailure(
                        familyId,
                        expectedState,
                        () ->
                                executeUpdate(
                                        connection,
                                        "CREATE TABLE "
                                                + qualified(absentSchema, "records")
                                                + " (marker integer)"));
            }
            case "G53-R-PG-024" -> {
                String statementName = quoteIdentifier("reserved_statement");
                String sql = "PREPARE " + statementName + " AS SELECT 1";
                setup(familyId, connection, sql);
                yield expectFailure(familyId, expectedState, () -> executeUpdate(connection, sql));
            }
            case "G53-R-PG-025" -> {
                beginTransaction(familyId, connection);
                String cursor = quoteIdentifier("reserved_cursor");
                String sql = "DECLARE " + cursor + " CURSOR FOR SELECT 1";
                setup(familyId, connection, sql);
                yield expectFailure(familyId, expectedState, () -> executeUpdate(connection, sql));
            }
            case "G53-R-PG-026" -> {
                beginTransaction(familyId, connection);
                yield expectFailure(
                        familyId,
                        expectedState,
                        () ->
                                executeQuery(
                                        connection,
                                        "FETCH ALL FROM " + quoteIdentifier("absent_cursor")));
            }
            case "G53-R-PG-027" ->
                    expectFailure(
                            familyId,
                            expectedState,
                            () -> executeUpdate(connection, "SET statement_timeout TO 'neverms'"));
            case "G53-R-PG-028" ->
                    expectFailure(
                            familyId,
                            expectedState,
                            () ->
                                    executeQuery(
                                            connection,
                                            "SELECT convert_from(decode('80', 'hex'), 'UTF8')"));
            case "G53-R-PG-029" -> {
                setup(
                        familyId,
                        connection,
                        "CREATE TABLE "
                                + records
                                + " (id bigint GENERATED ALWAYS AS IDENTITY, marker text)");
                yield expectFailure(
                        familyId,
                        expectedState,
                        () ->
                                executeUpdate(
                                        connection,
                                        "INSERT INTO "
                                                + records
                                                + " (id, marker) VALUES (7, 'controlled')"));
            }
            case "G53-R-PG-030" -> {
                String sequence = qualified(schema, "bounded_sequence");
                setup(
                        familyId,
                        connection,
                        "CREATE SEQUENCE " + sequence + " START WITH 1 MAXVALUE 1 NO CYCLE");
                executePreparedSetup(
                        familyId, connection, "SELECT nextval(CAST(? AS regclass))", sequence);
                yield expectFailure(
                        familyId,
                        expectedState,
                        () ->
                                executePrepared(
                                        connection,
                                        "SELECT nextval(CAST(? AS regclass))",
                                        sequence));
            }
            default ->
                    throw new IllegalArgumentException(
                            "unsupported PostgreSQL family: " + familyId);
        };
    }

    private static SQLException rowLockNowait(
            String familyId,
            String expectedState,
            String schema,
            ConnectionFactory connectionFactory) {
        String records = qualified(schema, "records");
        try (Connection locker =
                        requireConnection(connectionFactory.open(DEFAULT_CATALOG), familyId);
                Connection contender =
                        requireConnection(connectionFactory.open(DEFAULT_CATALOG), familyId)) {
            SQLException result = null;
            RuntimeException primaryFailure = null;
            try {
                configure(familyId, locker, schema);
                configure(familyId, contender, schema);
                setup(
                        familyId,
                        locker,
                        "CREATE TABLE " + records + " (id integer PRIMARY KEY)",
                        "INSERT INTO " + records + " VALUES (1)");
                beginTransaction(familyId, locker);
                beginTransaction(familyId, contender);
                executeQuerySetup(
                        familyId, locker, "SELECT id FROM " + records + " WHERE id = 1 FOR UPDATE");
                result =
                        expectFailure(
                                familyId,
                                expectedState,
                                () ->
                                        executeQuery(
                                                contender,
                                                "SELECT id FROM "
                                                        + records
                                                        + " WHERE id = 1 FOR UPDATE NOWAIT"));
                return result;
            } catch (RuntimeException failure) {
                primaryFailure = failure;
                throw failure;
            } finally {
                rollbackTransactions(familyId, result, primaryFailure, contender, locker);
            }
        } catch (SQLException failure) {
            throw harnessFailure(familyId, "coordinating the two lock connections", failure);
        }
    }

    private static SQLException invalidCatalog(
            String familyId,
            String expectedState,
            String schema,
            ConnectionFactory connectionFactory) {
        String absentCatalog = schema + "_absent_catalog";
        return expectFailure(
                familyId,
                expectedState,
                () -> {
                    try (Connection unexpected =
                            requireConnection(connectionFactory.open(absentCatalog), familyId)) {
                        // A successful connection means the reserved database was not absent.
                    }
                });
    }

    private static SQLException withDefaultConnection(
            String familyId,
            String schema,
            ConnectionFactory connectionFactory,
            ConnectionRecipe recipe) {
        try (Connection connection =
                requireConnection(connectionFactory.open(DEFAULT_CATALOG), familyId)) {
            configure(familyId, connection, schema);
            SQLException result = null;
            RuntimeException primaryFailure = null;
            try {
                result = recipe.execute(connection);
                return result;
            } catch (RuntimeException failure) {
                primaryFailure = failure;
                throw failure;
            } finally {
                rollbackTransactions(familyId, result, primaryFailure, connection);
            }
        } catch (SQLException failure) {
            throw harnessFailure(familyId, "using the default PostgreSQL connection", failure);
        }
    }

    private static void prepareSchema(
            ConnectionFactory connectionFactory, String familyId, String schema) {
        try (Connection connection =
                requireConnection(connectionFactory.open(DEFAULT_CATALOG), familyId)) {
            configureTimeouts(connection);
            executeUpdate(
                    connection, "DROP SCHEMA IF EXISTS " + quoteIdentifier(schema) + " CASCADE");
            executeUpdate(connection, "CREATE SCHEMA " + quoteIdentifier(schema));
        } catch (SQLException failure) {
            throw harnessFailure(familyId, "preparing schema " + schema, failure);
        }
    }

    private static void cleanupSchema(
            ConnectionFactory connectionFactory, String familyId, String schema) {
        try (Connection connection =
                requireConnection(connectionFactory.open(DEFAULT_CATALOG), familyId)) {
            configureTimeouts(connection);
            executeUpdate(
                    connection, "DROP SCHEMA IF EXISTS " + quoteIdentifier(schema) + " CASCADE");
        } catch (SQLException failure) {
            throw harnessFailure(familyId, "cleaning schema " + schema, failure);
        }
    }

    private static void configure(String familyId, Connection connection, String schema) {
        try {
            configureTimeouts(connection);
            executeUpdate(
                    connection, "SET search_path TO " + quoteIdentifier(schema) + ", pg_catalog");
        } catch (SQLException failure) {
            throw harnessFailure(familyId, "configuring the PostgreSQL session", failure);
        }
    }

    private static void configureTimeouts(Connection connection) throws SQLException {
        executeUpdate(connection, "SET statement_timeout = '3000ms'");
        executeUpdate(connection, "SET lock_timeout = '750ms'");
        executeUpdate(connection, "SET idle_in_transaction_session_timeout = '5000ms'");
    }

    private static void beginTransaction(String familyId, Connection connection) {
        try {
            connection.setAutoCommit(false);
        } catch (SQLException failure) {
            throw harnessFailure(familyId, "beginning the controlled transaction", failure);
        }
    }

    private static void rollbackTransactions(
            String familyId,
            SQLException result,
            RuntimeException primaryFailure,
            Connection... connections) {
        HarnessException rollbackFailure = null;
        for (Connection connection : connections) {
            try {
                if (!connection.getAutoCommit()) {
                    connection.rollback();
                }
            } catch (SQLException failure) {
                HarnessException current =
                        harnessFailure(familyId, "rolling back a fixture transaction", failure);
                if (rollbackFailure == null) {
                    rollbackFailure = current;
                } else {
                    rollbackFailure.addSuppressed(current);
                }
            }
        }
        if (rollbackFailure == null) {
            return;
        }
        if (primaryFailure != null) {
            primaryFailure.addSuppressed(rollbackFailure);
            return;
        }
        if (result != null) {
            rollbackFailure.addSuppressed(result);
        }
        throw rollbackFailure;
    }

    private static void setup(String familyId, Connection connection, String... sqlStatements) {
        try {
            for (String sql : sqlStatements) {
                executeUpdate(connection, sql);
            }
        } catch (SQLException failure) {
            throw harnessFailure(familyId, "executing fixture setup", failure);
        }
    }

    private static void executePreparedSetup(
            String familyId, Connection connection, String sql, String value) {
        try {
            executePrepared(connection, sql, value);
        } catch (SQLException failure) {
            throw harnessFailure(familyId, "executing prepared fixture setup", failure);
        }
    }

    private static void executeQuerySetup(String familyId, Connection connection, String sql) {
        try {
            executeQuery(connection, sql);
        } catch (SQLException failure) {
            throw harnessFailure(familyId, "executing query fixture setup", failure);
        }
    }

    private static void assertSchemaAbsent(
            String familyId, Connection connection, String absentSchema) {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        "SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_namespace WHERE nspname = ?)")) {
            statement.setQueryTimeout(JDBC_QUERY_TIMEOUT_SECONDS);
            statement.setString(1, absentSchema);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getBoolean(1)) {
                    throw new HarnessException(
                            familyId + " reserved absent schema already exists: " + absentSchema);
                }
            }
        } catch (SQLException failure) {
            throw harnessFailure(familyId, "checking the reserved absent schema", failure);
        }
    }

    private static SQLException expectFailure(
            String familyId, String expectedState, SqlAction action) {
        try {
            action.run();
        } catch (SQLException failure) {
            List<String> observedStates = observedStates(failure);
            if (!observedStates.contains(expectedState)) {
                throw new HarnessException(
                        familyId
                                + " SQLSTATE drift: expected "
                                + expectedState
                                + " but observed "
                                + (observedStates.isEmpty() ? "<none>" : observedStates),
                        failure);
            }
            return failure;
        }
        throw new HarnessException(
                familyId + " did not fail; expected PostgreSQL SQLSTATE " + expectedState);
    }

    private static List<String> observedStates(SQLException failure) {
        List<String> states = new ArrayList<>();
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(failure);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (current instanceof SQLException sqlFailure) {
                if (sqlFailure.getSQLState() != null) {
                    states.add(sqlFailure.getSQLState());
                }
                if (sqlFailure.getNextException() != null) {
                    pending.addLast(sqlFailure.getNextException());
                }
            }
            if (current.getCause() != null) {
                pending.addLast(current.getCause());
            }
        }
        return List.copyOf(states);
    }

    private static void executeUpdate(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(JDBC_QUERY_TIMEOUT_SECONDS);
            statement.execute(sql);
        }
    }

    private static void executeQuery(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(JDBC_QUERY_TIMEOUT_SECONDS);
            try (ResultSet resultSet = statement.executeQuery(sql)) {
                while (resultSet.next()) {
                    // Consume the result so lock acquisition and server-side execution are
                    // complete.
                }
            }
        }
    }

    private static void executePrepared(Connection connection, String sql, String... values)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(JDBC_QUERY_TIMEOUT_SECONDS);
            for (int index = 0; index < values.length; index++) {
                statement.setString(index + 1, values[index]);
            }
            statement.execute();
        }
    }

    private static Connection requireConnection(Connection connection, String familyId) {
        if (connection == null) {
            throw new HarnessException(familyId + " connection factory returned null");
        }
        return connection;
    }

    private static HarnessException harnessFailure(
            String familyId, String operation, SQLException failure) {
        return new HarnessException(
                familyId
                        + " harness failure while "
                        + operation
                        + "; SQLSTATEs="
                        + observedStates(failure),
                failure);
    }

    private static String schemaName(String familyId, int variant) {
        return "stackmate_"
                + familyId.toLowerCase(java.util.Locale.ROOT).replace('-', '_')
                + "_v"
                + variant;
    }

    private static String qualified(String schema, String object) {
        return quoteIdentifier(schema) + "." + quoteIdentifier(object);
    }

    private static String quoteIdentifier(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        if (identifier.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("PostgreSQL identifiers cannot contain NUL");
        }
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    @FunctionalInterface
    public interface ConnectionFactory {
        Connection open(String catalog) throws SQLException;
    }

    public static final class HarnessException extends IllegalStateException {
        private HarnessException(String message) {
            super(message);
        }

        private HarnessException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }

    @FunctionalInterface
    private interface ConnectionRecipe {
        SQLException execute(Connection connection);
    }
}
