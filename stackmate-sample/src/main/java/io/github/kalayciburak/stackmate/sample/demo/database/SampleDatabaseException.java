package io.github.kalayciburak.stackmate.sample.demo.database;

import java.sql.SQLException;

public final class SampleDatabaseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private SampleDatabaseException(String message, SQLException cause) {
        super(message, cause);
    }

    public static SampleDatabaseException duplicateKey() {
        return create(
                "Controlled duplicate-key database failure",
                "duplicate key value violates unique constraint \"uk_sample_reference\"",
                "23505",
                1);
    }

    public static SampleDatabaseException notNullViolation() {
        return create(
                "Controlled not-null database failure",
                "null value in column \"display_name\" violates not-null constraint",
                "23502",
                2);
    }

    public static SampleDatabaseException foreignKeyViolation() {
        return create(
                "Controlled foreign-key database failure",
                "insert or update on table \"sample_child\" violates foreign key constraint \"fk_sample_parent\"",
                "23503",
                3);
    }

    public static SampleDatabaseException missingColumn() {
        return create(
                "Controlled missing-column database failure",
                "column \"missing_sample_column\" does not exist",
                "42703",
                4);
    }

    private static SampleDatabaseException create(
            String wrapperMessage, String databaseMessage, String sqlState, int vendorCode) {
        SQLException cause = new SQLException(databaseMessage, sqlState, vendorCode);
        return new SampleDatabaseException(wrapperMessage, cause);
    }
}
