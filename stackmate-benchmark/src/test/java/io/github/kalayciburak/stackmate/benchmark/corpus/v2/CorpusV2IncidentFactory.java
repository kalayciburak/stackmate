package io.github.kalayciburak.stackmate.benchmark.corpus.v2;

import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.incident.StackFrameSnapshot;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import io.github.kalayciburak.stackmate.throwable.SqlExceptionDetails;
import io.github.kalayciburak.stackmate.throwable.ThrowableNode;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshot;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Builds frozen V2 incidents for deterministic and local-model evaluation. */
public final class CorpusV2IncidentFactory {

    private static final Instant CAPTURE_TIME = Instant.parse("2026-08-01T00:00:00Z");
    private final CorpusV2RuntimeRecipeTest runtimeRecipes = new CorpusV2RuntimeRecipeTest();

    public IncidentSnapshot create(CorpusV2Case benchmarkCase) throws Exception {
        StackFrameSnapshot frame = applicationFrame(benchmarkCase);
        ThrowableSnapshot throwable;
        if (benchmarkCase.realExecution()) {
            Throwable failure = runtimeRecipes.execute(benchmarkCase.recipe());
            Throwable root = deepest(failure);
            root.setStackTrace(
                    new StackTraceElement[] {
                        new StackTraceElement(
                                frame.className(),
                                frame.methodName(),
                                frame.fileName(),
                                frame.lineNumber())
                    });
            throwable = ThrowableSnapshotter.defaults().capture(failure);
        } else {
            throwable = syntheticThrowable(benchmarkCase, frame);
        }
        return IncidentSnapshot.of(
                "V2-" + benchmarkCase.caseId(), CAPTURE_TIME, throwable, List.of(frame));
    }

    private ThrowableSnapshot syntheticThrowable(
            CorpusV2Case benchmarkCase, StackFrameSnapshot frame) {
        SyntheticSpec spec = syntheticSpec(benchmarkCase.recipe());
        List<ThrowableNode> nodes = new ArrayList<>();
        for (int index = 0; index < spec.types().size(); index++) {
            String type = spec.types().get(index);
            boolean root = index == spec.types().size() - 1;
            nodes.add(
                    new ThrowableNode(
                            type,
                            DefaultSanitizer.defaults()
                                    .sanitize(
                                            "exceptionMessage",
                                            root ? spec.rootMessage() : "wrapper for " + type),
                            root ? List.of(frame.withCausalDepth(index)) : List.of(),
                            List.of(),
                            root && !spec.sqlState().isBlank()
                                    ? new SqlExceptionDetails(spec.sqlState(), spec.vendorCode())
                                    : SqlExceptionDetails.none(),
                            index,
                            !root,
                            false,
                            false,
                            false,
                            false,
                            type.equals("unknown.throwable")
                                    ? List.of("throwable.type.unavailable")
                                    : List.of()));
        }
        if (!nodes.getLast().type().equals(benchmarkCase.expectedRootType())) {
            throw new IllegalStateException(
                    "synthetic recipe root does not match frozen corpus: "
                            + benchmarkCase.caseId());
        }
        return new ThrowableSnapshot(nodes, nodes.size(), false);
    }

    private SyntheticSpec syntheticSpec(String recipe) {
        return switch (recipe) {
            case "java.hostile-throwable" -> spec("unknown.throwable", "throwable capture failed");
            case "java.unknown-runtime" ->
                    spec("com.acme.runtime.UnmappedFailure", "unexpected runtime condition");
            case "spring.unsatisfied-wrapper" ->
                    spec(
                            List.of(
                                    "org.springframework.beans.factory.UnsatisfiedDependencyException",
                                    "org.springframework.beans.factory.NoSuchBeanDefinitionException"),
                            "No qualifying bean of type 'com.example.OrderRepository' available");
            case "spring.bind-wrapper" ->
                    spec(
                            List.of(
                                    "org.springframework.boot.context.properties.bind.ConfigurationPropertiesBindException",
                                    "org.springframework.boot.context.properties.bind.BindException"),
                            "Failed to bind properties under 'billing.retry' to java.lang.Integer");
            case "spring.condition-wrapper" ->
                    spec(
                            "java.lang.IllegalStateException",
                            "Error processing condition on com.example.FeatureConfiguration");
            case "spring.unknown-wrapper" ->
                    spec(
                            "com.example.spring.UnknownBootstrapFailure",
                            "framework bootstrap stopped without recognized evidence");
            case "mvc.handler-method-validation" ->
                    spec(
                            "org.springframework.web.method.annotation.HandlerMethodValidationException",
                            "validation failed for create order");
            case "mvc.constraint-violation" ->
                    spec(
                            "jakarta.validation.ConstraintViolationException",
                            "create.quantity must be greater than zero");
            case "mvc.serialization-failure" ->
                    spec(
                            "com.fasterxml.jackson.databind.JsonMappingException",
                            "failed to serialize response property total");
            case "mvc.unknown-resolved" ->
                    spec(
                            "com.example.web.CustomResolvedException",
                            "custom handler resolved this exception");
            case "postgres.unique-localized" ->
                    sql(
                            "java.sql.SQLException",
                            "23505",
                            "duplicate key value violates unique constraint uk_account_email");
            case "postgres.foreign-key" ->
                    sql(
                            "java.sql.SQLException",
                            "23503",
                            "insert or update violates foreign key constraint fk_order_customer");
            case "postgres.missing-table" ->
                    sql(
                            "java.sql.SQLException",
                            "42P01",
                            "relation account_archive does not exist");
            case "postgres.missing-column" ->
                    sql("java.sql.SQLException", "42703", "column legacy_code does not exist");
            case "postgres.deadlock" -> sql("java.sql.SQLException", "40P01", "deadlock detected");
            case "jdbc.connection-refused" ->
                    spec(
                            List.of("java.sql.SQLException", "java.net.ConnectException"),
                            "connection refused");
            case "pool.timeout-classname" ->
                    spec(
                            "java.sql.SQLTransientConnectionException",
                            "connection is not available, request timed out after 30000ms");
            case "persistence.optimistic-lock" ->
                    spec(
                            "jakarta.persistence.OptimisticLockException",
                            "row was updated by another transaction");
            case "hibernate.lazy-initialization" ->
                    spec(
                            "org.hibernate.LazyInitializationException",
                            "could not initialize proxy - no Session");
            case "database.unknown-vendor" ->
                    spec(
                            "com.vendor.jdbc.StrangeDatabaseException",
                            "vendor-specific condition without SQLState");
            case "spring-security.access-denied" ->
                    spec(
                            "org.springframework.security.access.AccessDeniedException",
                            "access denied");
            case "spring-security.bad-credentials" ->
                    spec(
                            "org.springframework.security.authentication.BadCredentialsException",
                            "bad credentials");
            case "security.expired-token-message" ->
                    spec(
                            "com.example.security.TokenValidationException",
                            "token has expired at 2026-08-01T00:00:00Z");
            case "security.unknown" ->
                    spec(
                            "com.example.security.PolicyEvaluationException",
                            "policy result could not be evaluated");
            case "network.malformed-url" ->
                    spec("java.net.MalformedURLException", "no protocol in internal-service");
            case "network.connection-reset" ->
                    spec("java.net.SocketException", "connection reset by peer");
            default -> throw new IllegalArgumentException("unknown synthetic recipe: " + recipe);
        };
    }

    private SyntheticSpec spec(String type, String message) {
        return spec(List.of(type), message);
    }

    private SyntheticSpec spec(List<String> types, String message) {
        return new SyntheticSpec(types, message, "", 0);
    }

    private SyntheticSpec sql(String type, String sqlState, String message) {
        return new SyntheticSpec(List.of(type), message, sqlState, 0);
    }

    private StackFrameSnapshot applicationFrame(CorpusV2Case benchmarkCase) {
        String domain = benchmarkCase.domain().name().toLowerCase(Locale.ROOT).replace('_', '.');
        return StackFrameSnapshot.from(
                new StackTraceElement(
                        "com.example." + domain + ".ScenarioService",
                        "run",
                        "ScenarioService.java",
                        40 + Math.floorMod(benchmarkCase.caseId().hashCode(), 60)),
                0);
    }

    private Throwable deepest(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (current.getCause() != null && visited.add(current)) {
            current = current.getCause();
        }
        return current;
    }

    private record SyntheticSpec(
            List<String> types, String rootMessage, String sqlState, int vendorCode) {
        private SyntheticSpec {
            types = List.copyOf(types);
        }
    }
}
