package io.github.kalayciburak.stackmate.throwable;

import java.util.Objects;

/** Structured JDBC facts copied without parsing an exception message. */
public record SqlExceptionDetails(String sqlState, int vendorCode) {

    public SqlExceptionDetails {
        sqlState = Objects.requireNonNull(sqlState, "sqlState");
    }

    public static SqlExceptionDetails none() {
        return new SqlExceptionDetails("", 0);
    }

    public boolean present() {
        return !sqlState.isEmpty() || vendorCode != 0;
    }
}
