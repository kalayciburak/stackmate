package io.github.kalayciburak.stackmate.analysis;

import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.incident.StackFrameSnapshot;
import io.github.kalayciburak.stackmate.throwable.SqlExceptionDetails;
import io.github.kalayciburak.stackmate.throwable.ThrowableNode;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshot;
import java.time.Instant;
import java.util.List;

final class IncidentFixtures {

    private IncidentFixtures() {}

    static IncidentSnapshot incident(ThrowableNode... causeChain) {
        StackFrameSnapshot applicationFrame =
                new StackFrameSnapshot(
                        "example.OrderService",
                        "create",
                        "OrderService.java",
                        42,
                        false,
                        "",
                        "",
                        "app",
                        causeChain.length - 1);
        return IncidentSnapshot.of(
                "incident-test",
                Instant.parse("2026-08-01T12:00:00Z"),
                new ThrowableSnapshot(List.of(causeChain), causeChain.length, false),
                List.of(applicationFrame));
    }

    static ThrowableNode node(String type, String message, int depth) {
        return node(type, message, depth, false, SqlExceptionDetails.none());
    }

    static ThrowableNode wrapper(String type, String message, int depth) {
        return node(type, message, depth, true, SqlExceptionDetails.none());
    }

    static ThrowableNode sqlNode(
            String type, String message, String sqlState, int vendorCode, int depth) {
        return node(type, message, depth, false, new SqlExceptionDetails(sqlState, vendorCode));
    }

    private static ThrowableNode node(
            String type, String message, int depth, boolean wrapper, SqlExceptionDetails sql) {
        return new ThrowableNode(
                type, message, List.of(), List.of(), sql, depth, wrapper, false, false, false,
                false, List.of());
    }
}
