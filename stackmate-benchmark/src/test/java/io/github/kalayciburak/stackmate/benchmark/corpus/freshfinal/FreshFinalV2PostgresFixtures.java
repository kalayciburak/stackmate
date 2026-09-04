package io.github.kalayciburak.stackmate.benchmark.corpus.freshfinal;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Deterministic, caller-provisioned PostgreSQL fixtures for the fresh-final v2 corpus. */
final class FreshFinalV2PostgresFixtures {

    private static final int JDBC_QUERY_TIMEOUT_SECONDS = 4;

    private static final Map<String, String> EXPECTED_STATES =
            Map.of(
                    "F543-R-PG-001", "23P01",
                    "F543-R-PG-002", "42501",
                    "F543-R-PG-003", "08P01",
                    "F543-R-PG-004", "42809",
                    "F543-R-PG-005", "42725",
                    "F543-R-PG-006", "40001");

    private FreshFinalV2PostgresFixtures() {}

    static boolean supports(String familyId) {
        return familyId != null && EXPECTED_STATES.containsKey(familyId);
    }

    static Throwable execute(String familyId, int variant, ConnectionProvider connectionProvider) {
        Objects.requireNonNull(familyId, "familyId");
        String expectedState = EXPECTED_STATES.get(familyId);
        if (expectedState == null) {
            throw new IllegalArgumentException("unsupported PostgreSQL family: " + familyId);
        }
        if (variant != 0 && variant != 1) {
            throw new IllegalArgumentException("variant must be 0 or 1: " + variant);
        }
        Objects.requireNonNull(connectionProvider, "connectionProvider");

        String schema = schemaName(familyId, variant);
        String role = familyId.equals("F543-R-PG-002") ? objectName(schema, "role", variant) : null;
        SQLException result = null;
        RuntimeException primaryFailure = null;
        try {
            prepareIsolation(familyId, schema, role, connectionProvider);
            result =
                    executeResolved(
                            familyId, expectedState, variant, schema, role, connectionProvider);
            return result;
        } catch (RuntimeException failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            try {
                cleanupIsolation(familyId, schema, role, connectionProvider);
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
            int variant,
            String schema,
            String role,
            ConnectionProvider connectionProvider) {
        if (familyId.equals("F543-R-PG-006")) {
            return serializableWriteConflict(
                    familyId, expectedState, variant, schema, connectionProvider);
        }
        return withConnection(
                familyId,
                schema,
                connectionProvider,
                connection ->
                        executeSingleConnection(
                                familyId, expectedState, variant, schema, role, connection));
    }

    private static SQLException executeSingleConnection(
            String familyId,
            String expectedState,
            int variant,
            String schema,
            String role,
            Connection connection) {
        return switch (familyId) {
            case "F543-R-PG-001" -> {
                String reservations = qualified(schema, objectName("reservations", variant));
                String exclusion = quoteIdentifier(objectName("no_overlap", variant));
                setup(
                        familyId,
                        connection,
                        "CREATE TABLE "
                                + reservations
                                + " (id bigint PRIMARY KEY, occupied_range int4range NOT NULL, "
                                + "CONSTRAINT "
                                + exclusion
                                + " EXCLUDE USING gist (occupied_range WITH &&))",
                        "INSERT INTO "
                                + reservations
                                + " (id, occupied_range) VALUES (1, int4range(10, 20, '[)'))");
                yield expectFailure(
                        familyId,
                        expectedState,
                        () ->
                                executeUpdate(
                                        connection,
                                        "INSERT INTO "
                                                + reservations
                                                + " (id, occupied_range) VALUES "
                                                + "(2, int4range(19, 30, '[)'))"));
            }
            case "F543-R-PG-002" -> {
                String records = qualified(schema, objectName("tenant_records", variant));
                String policy = quoteIdentifier(objectName("tenant_insert", variant));
                setup(
                        familyId,
                        connection,
                        "CREATE ROLE " + quoteIdentifier(role) + " NOLOGIN",
                        "GRANT " + quoteIdentifier(role) + " TO CURRENT_USER",
                        "CREATE TABLE "
                                + records
                                + " (id bigint PRIMARY KEY, tenant_id integer NOT NULL, "
                                + "payload text)",
                        "ALTER TABLE " + records + " ENABLE ROW LEVEL SECURITY",
                        "CREATE POLICY "
                                + policy
                                + " ON "
                                + records
                                + " FOR INSERT TO "
                                + quoteIdentifier(role)
                                + " WITH CHECK (tenant_id = 7)",
                        "GRANT USAGE ON SCHEMA "
                                + quoteIdentifier(schema)
                                + " TO "
                                + quoteIdentifier(role),
                        "GRANT INSERT ON " + records + " TO " + quoteIdentifier(role));
                beginTransaction(familyId, connection);
                setup(familyId, connection, "SET LOCAL ROLE " + quoteIdentifier(role));
                yield expectFailure(
                        familyId,
                        expectedState,
                        () ->
                                executeUpdate(
                                        connection,
                                        "INSERT INTO "
                                                + records
                                                + " (id, tenant_id, payload) "
                                                + "VALUES (1, 8, 'outside controlled tenant')"));
            }
            case "F543-R-PG-003" ->
                    expectFailure(
                            familyId,
                            expectedState,
                            () -> executeQuery(connection, "SELECT $1::integer"));
            case "F543-R-PG-004" -> {
                String view = qualified(schema, objectName("records_view", variant));
                setup(
                        familyId,
                        connection,
                        "CREATE VIEW " + view + " AS SELECT 1::integer AS marker");
                yield expectFailure(
                        familyId,
                        expectedState,
                        () -> executeUpdate(connection, "DROP TABLE " + view));
            }
            case "F543-R-PG-005" -> {
                String overloaded = qualified(schema, objectName("resolve_value", variant));
                setup(
                        familyId,
                        connection,
                        "CREATE FUNCTION "
                                + overloaded
                                + "(value integer) RETURNS integer LANGUAGE SQL IMMUTABLE "
                                + "AS 'SELECT $1'",
                        "CREATE FUNCTION "
                                + overloaded
                                + "(value bigint) RETURNS bigint LANGUAGE SQL IMMUTABLE "
                                + "AS 'SELECT $1'");
                yield expectFailure(
                        familyId,
                        expectedState,
                        () -> executeQuery(connection, "SELECT " + overloaded + "(NULL)"));
            }
            default ->
                    throw new IllegalArgumentException(
                            "unsupported single-connection PostgreSQL family: " + familyId);
        };
    }

    private static SQLException serializableWriteConflict(
            String familyId,
            String expectedState,
            int variant,
            String schema,
            ConnectionProvider connectionProvider) {
        String shifts = qualified(schema, objectName("on_call_shifts", variant));
        try (Connection first = requireConnection(connectionProvider.open(), familyId);
                Connection second = requireConnection(connectionProvider.open(), familyId)) {
            configure(familyId, first, schema);
            configure(familyId, second, schema);
            setup(
                    familyId,
                    first,
                    "CREATE TABLE "
                            + shifts
                            + " (doctor_id bigint PRIMARY KEY, on_call boolean NOT NULL)",
                    "INSERT INTO " + shifts + " (doctor_id, on_call) VALUES (1, TRUE), (2, TRUE)");
            beginSerializableTransaction(familyId, first);
            beginSerializableTransaction(familyId, second);

            SQLException result = null;
            RuntimeException primaryFailure = null;
            try {
                executeQuerySetup(
                        familyId, first, "SELECT on_call FROM " + shifts + " WHERE doctor_id = 1");
                executeQuerySetup(
                        familyId, second, "SELECT on_call FROM " + shifts + " WHERE doctor_id = 1");
                setup(
                        familyId,
                        first,
                        "UPDATE " + shifts + " SET on_call = FALSE WHERE doctor_id = 1");
                commit(familyId, first);
                result =
                        expectFailure(
                                familyId,
                                expectedState,
                                () ->
                                        executeUpdate(
                                                second,
                                                "UPDATE "
                                                        + shifts
                                                        + " SET on_call = FALSE "
                                                        + "WHERE doctor_id = 1"));
                return result;
            } catch (RuntimeException failure) {
                primaryFailure = failure;
                throw failure;
            } finally {
                rollbackTransactions(familyId, result, primaryFailure, second, first);
            }
        } catch (SQLException failure) {
            throw harnessFailure(familyId, "coordinating serializable transactions", failure);
        }
    }

    private static SQLException withConnection(
            String familyId,
            String schema,
            ConnectionProvider connectionProvider,
            ConnectionRecipe recipe) {
        try (Connection connection = requireConnection(connectionProvider.open(), familyId)) {
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
            throw harnessFailure(familyId, "using a PostgreSQL connection", failure);
        }
    }

    private static void prepareIsolation(
            String familyId, String schema, String role, ConnectionProvider connectionProvider) {
        try (Connection connection = requireConnection(connectionProvider.open(), familyId)) {
            configureTimeouts(connection);
            executeUpdate(
                    connection, "DROP SCHEMA IF EXISTS " + quoteIdentifier(schema) + " CASCADE");
            if (role != null) {
                executeUpdate(connection, "DROP ROLE IF EXISTS " + quoteIdentifier(role));
            }
            executeUpdate(connection, "CREATE SCHEMA " + quoteIdentifier(schema));
        } catch (SQLException failure) {
            throw harnessFailure(familyId, "preparing isolated PostgreSQL objects", failure);
        }
    }

    private static void cleanupIsolation(
            String familyId, String schema, String role, ConnectionProvider connectionProvider) {
        try (Connection connection = requireConnection(connectionProvider.open(), familyId)) {
            configureTimeouts(connection);
            executeUpdate(
                    connection, "DROP SCHEMA IF EXISTS " + quoteIdentifier(schema) + " CASCADE");
            if (role != null) {
                executeUpdate(connection, "DROP ROLE IF EXISTS " + quoteIdentifier(role));
            }
        } catch (SQLException failure) {
            throw harnessFailure(familyId, "cleaning isolated PostgreSQL objects", failure);
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
            throw harnessFailure(familyId, "beginning a controlled transaction", failure);
        }
    }

    private static void beginSerializableTransaction(String familyId, Connection connection) {
        try {
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            connection.setAutoCommit(false);
        } catch (SQLException failure) {
            throw harnessFailure(familyId, "beginning a serializable transaction", failure);
        }
    }

    private static void commit(String familyId, Connection connection) {
        try {
            connection.commit();
        } catch (SQLException failure) {
            throw harnessFailure(
                    familyId, "committing the first serializable transaction", failure);
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

    private static void executeQuerySetup(String familyId, Connection connection, String sql) {
        try {
            executeQuery(connection, sql);
        } catch (SQLException failure) {
            throw harnessFailure(familyId, "executing fixture query setup", failure);
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
                    // Consume all rows so server-side reads and predicate locks are complete.
                }
            }
        }
    }

    private static Connection requireConnection(Connection connection, String familyId) {
        if (connection == null) {
            throw new HarnessException(familyId + " connection provider returned null");
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
        return "stackmate_" + familyId.toLowerCase(Locale.ROOT).replace('-', '_') + "_v" + variant;
    }

    private static String objectName(String base, int variant) {
        return base + "_v" + variant;
    }

    private static String objectName(String schema, String base, int variant) {
        return schema + "_" + base + "_v" + variant;
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
    interface ConnectionProvider {
        Connection open() throws SQLException;
    }

    private static final class HarnessException extends IllegalStateException {

        private static final long serialVersionUID = 1L;

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
