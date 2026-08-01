package io.github.kalayciburak.stackmate.benchmark.microbenchmark;

import io.github.kalayciburak.stackmate.analysis.DefaultDiagnosticAnalyzers;
import io.github.kalayciburak.stackmate.analysis.DeterministicDiagnosticEngine;
import io.github.kalayciburak.stackmate.analysis.DeterministicResult;
import io.github.kalayciburak.stackmate.fingerprint.IncidentFingerprinter;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.incident.JvmRuntimeContext;
import io.github.kalayciburak.stackmate.incident.RequestContext;
import io.github.kalayciburak.stackmate.incident.SpringRuntimeContext;
import io.github.kalayciburak.stackmate.incident.SpringRuntimeContext.TransactionState;
import io.github.kalayciburak.stackmate.incident.StackFrameSnapshot;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import io.github.kalayciburak.stackmate.throwable.ApplicationFrameSelector;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshot;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotter;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

final class BenchmarkFixtures {

    private static final List<String> APPLICATION_PACKAGES = List.of("com.acme.orders");

    private BenchmarkFixtures() {}

    static Throwable representativeThrowable() {
        SQLException databaseFailure =
                new SQLException(
                        "duplicate key violates constraint orders_reference_key", "23505", 0);
        databaseFailure.setStackTrace(
                new StackTraceElement[] {
                    frame(
                            "org.postgresql.core.v3.QueryExecutorImpl",
                            "receiveErrorResponse",
                            2_723),
                    frame("com.acme.orders.persistence.OrderRepository", "insert", 118),
                    frame("com.acme.orders.OrderService", "placeOrder", 74)
                });

        IllegalStateException serviceFailure =
                new IllegalStateException(
                        "Order creation failed for customer 100042", databaseFailure);
        serviceFailure.setStackTrace(
                new StackTraceElement[] {
                    frame("com.acme.orders.OrderService", "placeOrder", 79),
                    frame("com.acme.orders.OrderController", "create", 46),
                    frame("org.springframework.web.servlet.DispatcherServlet", "doDispatch", 1_082)
                });

        IllegalArgumentException auditFailure =
                new IllegalArgumentException("audit metadata was unavailable");
        auditFailure.setStackTrace(
                new StackTraceElement[] {
                    frame("com.acme.orders.audit.OrderAuditWriter", "write", 61),
                    frame("com.acme.orders.OrderService", "placeOrder", 81)
                });
        serviceFailure.addSuppressed(auditFailure);

        RuntimeException outer =
                new RuntimeException("HTTP request processing failed", serviceFailure);
        outer.setStackTrace(
                new StackTraceElement[] {
                    frame("com.acme.orders.OrderController", "create", 49),
                    frame("org.springframework.web.servlet.FrameworkServlet", "doPost", 914),
                    frame("jakarta.servlet.http.HttpServlet", "service", 590)
                });
        return outer;
    }

    static ThrowableSnapshot throwableSnapshot() {
        return ThrowableSnapshotter.defaults().capture(representativeThrowable());
    }

    static IncidentSnapshot incident() {
        ThrowableSnapshot throwable = throwableSnapshot();
        List<StackFrameSnapshot> applicationFrames =
                new ApplicationFrameSelector(APPLICATION_PACKAGES, 8).select(throwable);
        return new IncidentSnapshot(
                "benchmark-incident-0001",
                Instant.parse("2026-08-01T12:00:00Z"),
                throwable,
                applicationFrames,
                new RequestContext(
                        "POST",
                        "/api/orders",
                        "com.acme.orders.OrderController",
                        "create",
                        500,
                        Map.of("traceId", "benchmark-trace-0001")),
                new SpringRuntimeContext(
                        "orders-service",
                        List.of("benchmark"),
                        "com.acme.orders",
                        "4.1.0",
                        "7.0.0",
                        TransactionState.ACTIVE),
                new JvmRuntimeContext(
                        "21.0.8", "OpenJDK 64-Bit Server VM", "OpenJDK", "Linux", "amd64", 8));
    }

    static DeterministicResult deterministicResult() {
        DeterministicDiagnosticEngine engine =
                new DeterministicDiagnosticEngine(
                        DefaultDiagnosticAnalyzers.aggregator(), new IncidentFingerprinter());
        return engine.analyze(incident());
    }

    static DefaultSanitizer sanitizer() {
        return DefaultSanitizer.defaults();
    }

    private static StackTraceElement frame(String className, String methodName, int lineNumber) {
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        return new StackTraceElement(className, methodName, simpleName + ".java", lineNumber);
    }
}
