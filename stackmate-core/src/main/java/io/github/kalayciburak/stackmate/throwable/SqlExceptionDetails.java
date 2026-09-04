package io.github.kalayciburak.stackmate.throwable;

import java.util.Objects;

/**
 * Structured JDBC facts copied without parsing an exception message.
 *
 * @param sqlState JDBC SQLState, or an empty string when unavailable
 * @param vendorCode vendor-specific error code, or {@code 0} when unavailable
 */
public record SqlExceptionDetails(String sqlState, int vendorCode) {

    /**
     * Creates structured JDBC details.
     *
     * @param sqlState JDBC SQLState, or an empty string when unavailable
     * @param vendorCode vendor-specific error code, or {@code 0} when unavailable
     */
    public SqlExceptionDetails {
        sqlState = Objects.requireNonNull(sqlState, "sqlState");
    }

    /**
     * Creates JDBC details with no available structured facts.
     *
     * @return empty JDBC details
     */
    public static SqlExceptionDetails none() {
        return new SqlExceptionDetails("", 0);
    }

    /**
     * Tests whether capture found any structured JDBC fact.
     *
     * @return whether either a SQLState or vendor code is available
     */
    public boolean present() {
        return !sqlState.isEmpty() || vendorCode != 0;
    }
}
