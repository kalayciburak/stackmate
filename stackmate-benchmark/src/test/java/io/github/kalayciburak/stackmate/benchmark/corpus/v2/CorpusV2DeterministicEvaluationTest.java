package io.github.kalayciburak.stackmate.benchmark.corpus.v2;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kalayciburak.stackmate.analysis.DefaultDiagnosticAnalyzers;
import io.github.kalayciburak.stackmate.analysis.DeterministicDiagnosticEngine;
import io.github.kalayciburak.stackmate.analysis.DeterministicResult;
import io.github.kalayciburak.stackmate.analysis.DiagnosticCategory;
import io.github.kalayciburak.stackmate.analysis.DiagnosticStrength;
import io.github.kalayciburak.stackmate.evidence.EvidenceItem;
import io.github.kalayciburak.stackmate.evidence.EvidenceType;
import io.github.kalayciburak.stackmate.fingerprint.IncidentFingerprinter;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.incident.StackFrameSnapshot;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import io.github.kalayciburak.stackmate.throwable.SqlExceptionDetails;
import io.github.kalayciburak.stackmate.throwable.ThrowableNode;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshot;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CorpusV2DeterministicEvaluationTest {

    private static final Instant CAPTURE_TIME = Instant.parse("2026-08-01T00:00:00Z");
    private static final Set<String> SCORED_ACTUAL_FACT_TYPES =
            Set.of("EXCEPTION_CLASS", "MESSAGE_PATTERN", "SQL_STATE", "WRAPPER_TYPE");
    private static final Set<String> FACT_EXPECTATION_TYPES =
            Set.of(
                    "EXCEPTION_CLASS",
                    "EXPECTED_TYPE",
                    "HANDLER_METHOD",
                    "MESSAGE_PATTERN",
                    "PROPERTY_NAME",
                    "REQUEST_PATH",
                    "SQL_STATE",
                    "WRAPPER_TYPE");
    private static final List<String> CONTROLLED_SECRETS =
            List.of("benchmark-secret-do-not-leak", "abc.def.ghi");

    private final DeterministicDiagnosticEngine engine =
            new DeterministicDiagnosticEngine(
                    DefaultDiagnosticAnalyzers.aggregator(), new IncidentFingerprinter());
    private final CorpusV2RuntimeRecipeTest runtimeRecipes = new CorpusV2RuntimeRecipeTest();

    @Test
    void evaluatesFrozenCorpusWithoutChangingQualityGroundTruth() throws Exception {
        CorpusV2Manifest manifest = CorpusV2Manifest.load();
        List<CaseResult> results = new ArrayList<>();
        List<Long> latencies = new ArrayList<>();

        for (CorpusV2Case benchmarkCase : manifest.cases()) {
            IncidentSnapshot incident = incident(benchmarkCase);
            for (int warmup = 0; warmup < 10; warmup++) {
                engine.analyze(incident);
            }
            DeterministicResult result = null;
            for (int sample = 0; sample < 25; sample++) {
                long started = System.nanoTime();
                result = engine.analyze(incident);
                latencies.add(System.nanoTime() - started);
            }
            results.add(evaluateCase(benchmarkCase, result, incident));
        }

        Summary summary = summarize(manifest, results, latencies);
        writeReports(summary);

        assertThat(summary.rootType().matched()).isEqualTo(80);
        assertThat(summary.secretLeakages()).isZero();
        assertThat(summary.sanitizationFailures()).isZero();
        assertThat(summary.fingerprintStability().matched()).isEqualTo(80);
        assertThat(summary.conflictingFingerprintCollisions()).isZero();
    }

    private IncidentSnapshot incident(CorpusV2Case benchmarkCase) throws Exception {
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

    private CaseResult evaluateCase(
            CorpusV2Case benchmarkCase, DeterministicResult result, IncidentSnapshot incident) {
        DiagnosticCategory expected = DiagnosticCategory.valueOf(benchmarkCase.category());
        DiagnosticCategory actual = result.diagnosis().causes().getFirst().category();
        DiagnosticStrength strength = result.diagnosis().causes().getFirst().strength();
        List<Fact> expectedFacts =
                benchmarkCase.expectedFacts().stream()
                        .filter(fact -> FACT_EXPECTATION_TYPES.contains(fact.type()))
                        .map(fact -> new Fact(fact.type(), fact.value()))
                        .toList();
        List<Fact> actualFacts =
                result.diagnosis().evidence().stream()
                        .map(this::fact)
                        .filter(fact -> SCORED_ACTUAL_FACT_TYPES.contains(fact.type()))
                        .distinct()
                        .toList();
        int matchedFacts =
                (int)
                        expectedFacts.stream()
                                .filter(
                                        expectedFact ->
                                                matches(expectedFact, actualFacts, incident))
                                .count();
        int falsePositiveFacts =
                (int)
                        actualFacts.stream()
                                .filter(
                                        actualFact ->
                                                expectedFacts.stream()
                                                        .anyMatch(
                                                                expectedFact ->
                                                                        expectedFact
                                                                                .type()
                                                                                .equals(
                                                                                        actualFact
                                                                                                .type())))
                                .filter(
                                        actualFact ->
                                                !matchesActual(actualFact, expectedFacts, incident))
                                .count();
        boolean restraintExpected =
                benchmarkCase.expectedDisposition() == CorpusV2Case.Disposition.RESTRAIN;
        boolean restraintObserved =
                actual == DiagnosticCategory.UNKNOWN
                        && (strength == DiagnosticStrength.UNKNOWN
                                || strength == DiagnosticStrength.LOW)
                        && result.diagnosis().fallback();
        boolean applicationFrameMatched =
                result.incident()
                        .applicationFrames()
                        .getFirst()
                        .equals(applicationFrame(benchmarkCase));
        int secretLeakages = containsAny(render(result), CONTROLLED_SECRETS) ? 1 : 0;
        int sanitizationFailures =
                secretLeakages > 0 || render(result).indexOf('\u001B') >= 0 ? 1 : 0;
        String repeatedFingerprint =
                engine.analyze(
                                new IncidentSnapshot(
                                        incident.incidentId() + "-REPEAT",
                                        incident.timestamp().plusSeconds(30),
                                        volatileVariant(benchmarkCase, incident.throwable()),
                                        incident.applicationFrames(),
                                        incident.requestContext(),
                                        incident.springRuntimeContext(),
                                        incident.jvmRuntimeContext()))
                        .fingerprint()
                        .value();
        boolean fingerprintStable = result.fingerprint().value().equals(repeatedFingerprint);
        String failureClass =
                classifyFailure(
                        benchmarkCase,
                        expected,
                        actual,
                        matchedFacts,
                        expectedFacts.size(),
                        restraintObserved,
                        secretLeakages);
        return new CaseResult(
                benchmarkCase.caseId(),
                benchmarkCase.split().name(),
                benchmarkCase.provenance().name(),
                expected.name(),
                actual.name(),
                expected == actual,
                benchmarkCase.expectedRootType(),
                incident.throwable().root().type(),
                benchmarkCase.expectedRootType().equals(incident.throwable().root().type()),
                expectedFacts.size(),
                actualFacts.size(),
                matchedFacts,
                falsePositiveFacts,
                applicationFrameMatched,
                restraintExpected,
                restraintObserved,
                strength.name(),
                secretLeakages,
                sanitizationFailures,
                fingerprintStable,
                result.fingerprint().value(),
                failureClass);
    }

    private Summary summarize(
            CorpusV2Manifest manifest, List<CaseResult> results, List<Long> latencies) {
        List<CaseResult> development =
                results.stream().filter(result -> result.split().equals("DEVELOPMENT")).toList();
        List<CaseResult> holdout =
                results.stream().filter(result -> result.split().equals("HOLDOUT")).toList();
        int expectedFacts = results.stream().mapToInt(CaseResult::expectedFacts).sum();
        int actualFacts = results.stream().mapToInt(CaseResult::actualFacts).sum();
        int matchedFacts = results.stream().mapToInt(CaseResult::matchedFacts).sum();
        int falsePositiveFacts = results.stream().mapToInt(CaseResult::falsePositiveFacts).sum();
        List<Long> ordered = latencies.stream().sorted().toList();
        CollisionResult collisions = collisions(results);
        return new Summary(
                manifest.corpusId(),
                manifest.schemaVersion(),
                results.size(),
                score(results, CaseResult::categoryMatched),
                score(development, CaseResult::categoryMatched),
                score(holdout, CaseResult::categoryMatched),
                score(results, CaseResult::rootTypeMatched),
                score(results, CaseResult::applicationFrameMatched),
                new Score(matchedFacts, expectedFacts),
                new Score(matchedFacts, matchedFacts + falsePositiveFacts),
                actualFacts,
                score(
                        results.stream().filter(CaseResult::restraintExpected).toList(),
                        CaseResult::restraintObserved),
                (int)
                        results.stream()
                                .filter(
                                        result ->
                                                result.restraintExpected()
                                                        && !result.restraintObserved())
                                .count(),
                results.stream().mapToInt(CaseResult::secretLeakages).sum(),
                results.stream().mapToInt(CaseResult::sanitizationFailures).sum(),
                score(results, CaseResult::fingerprintStable),
                collisions.groups(),
                collisions.conflicting(),
                nanosToMicros(percentile(ordered, 0.50)),
                nanosToMicros(percentile(ordered, 0.95)),
                ordered.size(),
                results);
    }

    private CollisionResult collisions(List<CaseResult> results) {
        Map<String, List<CaseResult>> grouped = new HashMap<>();
        results.forEach(
                result ->
                        grouped.computeIfAbsent(result.fingerprint(), ignored -> new ArrayList<>())
                                .add(result));
        List<List<CaseResult>> collisions =
                grouped.values().stream().filter(group -> group.size() > 1).toList();
        int conflicting =
                (int)
                        collisions.stream()
                                .filter(
                                        group ->
                                                group.stream()
                                                                .map(CaseResult::expectedCategory)
                                                                .distinct()
                                                                .count()
                                                        > 1)
                                .count();
        return new CollisionResult(collisions.size(), conflicting);
    }

    private void writeReports(Summary summary) throws Exception {
        Path target = Path.of("target");
        Files.createDirectories(target);
        ObjectMapper mapper = new ObjectMapper();
        Files.writeString(
                target.resolve("stackmate-benchmark-v2-report.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary)
                        + System.lineSeparator(),
                StandardCharsets.UTF_8);
        Files.writeString(
                target.resolve("stackmate-benchmark-v2-report.md"),
                markdown(summary),
                StandardCharsets.UTF_8);
    }

    private String markdown(Summary summary) {
        StringBuilder text = new StringBuilder("# StackMate deterministic benchmark V2\n\n");
        text.append("- Corpus: `").append(summary.corpusId()).append("`\n");
        text.append("- Cases: ").append(summary.caseCount()).append("\n");
        text.append("- Category accuracy: ")
                .append(summary.categoryAccuracy().display())
                .append("\n");
        text.append("- Development category accuracy: ")
                .append(summary.developmentCategoryAccuracy().display())
                .append("\n");
        text.append("- Holdout category accuracy: ")
                .append(summary.holdoutCategoryAccuracy().display())
                .append("\n");
        text.append("- Root-type accuracy: ").append(summary.rootType().display()).append("\n");
        text.append("- Fact precision: ").append(summary.factPrecision().display()).append("\n");
        text.append("- Fact recall: ").append(summary.factRecall().display()).append("\n");
        text.append("- Unknown restraint: ")
                .append(summary.unknownRestraint().display())
                .append("\n");
        text.append("- Secret leakages: ").append(summary.secretLeakages()).append("\n");
        text.append("- Sanitization failures: ")
                .append(summary.sanitizationFailures())
                .append("\n");
        text.append("- Fingerprint stability: ")
                .append(summary.fingerprintStability().display())
                .append("\n");
        text.append("- Cross-case collision groups: ")
                .append(summary.fingerprintCollisionGroups())
                .append(" (conflicting: ")
                .append(summary.conflictingFingerprintCollisions())
                .append(")\n");
        text.append("- Observed analysis median: ")
                .append(summary.medianAnalysisMicros())
                .append(" us\n");
        text.append("- Observed analysis P95: ")
                .append(summary.p95AnalysisMicros())
                .append(" us\n\n");
        text.append("Latency is an in-test observation over ")
                .append(summary.latencySamples())
                .append(" warm in-process samples, not a JMH claim.\n\n");
        text.append("## Non-passing cases\n\n");
        text.append("| Case | Split | Expected | Actual | Classification |\n");
        text.append("|---|---|---|---|---|\n");
        summary.cases().stream()
                .filter(result -> !result.failureClass().equals("PASS"))
                .forEach(
                        result ->
                                text.append('|')
                                        .append(result.caseId())
                                        .append('|')
                                        .append(result.split())
                                        .append('|')
                                        .append(result.expectedCategory())
                                        .append('|')
                                        .append(result.actualCategory())
                                        .append('|')
                                        .append(result.failureClass())
                                        .append("|\n"));
        return text.toString();
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
        assertThat(nodes.getLast().type()).isEqualTo(benchmarkCase.expectedRootType());
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

    private ThrowableSnapshot volatileVariant(
            CorpusV2Case benchmarkCase, ThrowableSnapshot throwable) {
        if (!benchmarkCase.caseId().equals("ADV-VOLATILE-005")) {
            return throwable;
        }
        List<ThrowableNode> nodes = new ArrayList<>(throwable.causeChain());
        ThrowableNode root = nodes.getLast();
        nodes.set(
                nodes.size() - 1,
                new ThrowableNode(
                        root.type(),
                        "request 11111111-2222-3333-4444-555555555555 order 123456",
                        root.frames(),
                        root.suppressed(),
                        root.sql(),
                        root.depth(),
                        root.wrapper(),
                        root.cycleDetected(),
                        root.depthTruncated(),
                        root.framesTruncated(),
                        root.suppressedTruncated(),
                        root.captureFailures()));
        return new ThrowableSnapshot(nodes, throwable.nodeCount(), throwable.truncated());
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
        Set<Throwable> visited =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        Throwable current = failure;
        while (current.getCause() != null && visited.add(current)) {
            current = current.getCause();
        }
        return current;
    }

    private Fact fact(EvidenceItem item) {
        String type =
                item.type() == EvidenceType.WRAPPER_CLASS ? "WRAPPER_TYPE" : item.type().name();
        return new Fact(type, item.value());
    }

    private boolean matches(Fact expected, List<Fact> actual, IncidentSnapshot incident) {
        if (expected.type().equals("MESSAGE_PATTERN")) {
            boolean messageContains =
                    incident.throwable().causeChain().stream()
                            .anyMatch(
                                    node ->
                                            node.message()
                                                    .toLowerCase(Locale.ROOT)
                                                    .contains(
                                                            expected.value()
                                                                    .toLowerCase(Locale.ROOT)));
            return messageContains
                    && actual.stream().anyMatch(fact -> fact.type().equals("MESSAGE_PATTERN"));
        }
        return actual.contains(expected);
    }

    private boolean matchesActual(Fact actual, List<Fact> expected, IncidentSnapshot incident) {
        return expected.stream()
                .anyMatch(expectation -> matches(expectation, List.of(actual), incident));
    }

    private String classifyFailure(
            CorpusV2Case benchmarkCase,
            DiagnosticCategory expected,
            DiagnosticCategory actual,
            int matchedFacts,
            int expectedFacts,
            boolean restraintObserved,
            int secretLeakages) {
        if (secretLeakages > 0) {
            return "SANITIZATION_ISSUE";
        }
        if (benchmarkCase.expectedDisposition() == CorpusV2Case.Disposition.RESTRAIN
                && !restraintObserved) {
            return "FALSE_CERTAINTY";
        }
        if (expected != actual) {
            return actual == DiagnosticCategory.UNKNOWN
                    ? "UNSUPPORTED_CATEGORY"
                    : "INCORRECT_CLASSIFICATION";
        }
        if (matchedFacts < expectedFacts) {
            return "MISSING_EVIDENCE_EXTRACTION";
        }
        return "PASS";
    }

    private String render(DeterministicResult result) {
        StringBuilder text = new StringBuilder();
        result.diagnosis().evidence().forEach(item -> text.append(item.value()).append('\n'));
        result.diagnosis()
                .causes()
                .forEach(
                        cause ->
                                text.append(cause.title())
                                        .append('\n')
                                        .append(cause.explanation())
                                        .append('\n'));
        result.diagnosis().checks().forEach(check -> text.append(check.instruction()).append('\n'));
        return text.toString();
    }

    private boolean containsAny(String value, List<String> needles) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return needles.stream()
                .map(needle -> needle.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }

    private Score score(
            List<CaseResult> cases, java.util.function.Predicate<CaseResult> predicate) {
        return new Score((int) cases.stream().filter(predicate).count(), cases.size());
    }

    private long percentile(List<Long> values, double percentile) {
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        return values.get(Math.max(0, Math.min(index, values.size() - 1)));
    }

    private long nanosToMicros(long nanos) {
        return TimeUnit.NANOSECONDS.toMicros(nanos);
    }

    private record Fact(String type, String value) {}

    private record SyntheticSpec(
            List<String> types, String rootMessage, String sqlState, int vendorCode) {
        private SyntheticSpec {
            types = List.copyOf(types);
        }
    }

    private record CollisionResult(int groups, int conflicting) {}

    private record Score(int matched, int total) {
        private String display() {
            double percent = total == 0 ? 0.0 : matched * 100.0 / total;
            return "%d/%d (%.2f%%)".formatted(matched, total, percent);
        }
    }

    private record CaseResult(
            String caseId,
            String split,
            String provenance,
            String expectedCategory,
            String actualCategory,
            boolean categoryMatched,
            String expectedRootType,
            String actualRootType,
            boolean rootTypeMatched,
            int expectedFacts,
            int actualFacts,
            int matchedFacts,
            int falsePositiveFacts,
            boolean applicationFrameMatched,
            boolean restraintExpected,
            boolean restraintObserved,
            String strength,
            int secretLeakages,
            int sanitizationFailures,
            boolean fingerprintStable,
            String fingerprint,
            String failureClass) {}

    private record Summary(
            String corpusId,
            String schemaVersion,
            int caseCount,
            Score categoryAccuracy,
            Score developmentCategoryAccuracy,
            Score holdoutCategoryAccuracy,
            Score rootType,
            Score applicationFrameAccuracy,
            Score factRecall,
            Score factPrecision,
            int scoredActualFacts,
            Score unknownRestraint,
            int falseCertaintyCount,
            int secretLeakages,
            int sanitizationFailures,
            Score fingerprintStability,
            int fingerprintCollisionGroups,
            int conflictingFingerprintCollisions,
            long medianAnalysisMicros,
            long p95AnalysisMicros,
            int latencySamples,
            List<CaseResult> cases) {
        private Summary {
            cases = List.copyOf(cases);
        }
    }
}
