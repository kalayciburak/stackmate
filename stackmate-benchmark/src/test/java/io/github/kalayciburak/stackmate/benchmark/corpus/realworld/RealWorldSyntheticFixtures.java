package io.github.kalayciburak.stackmate.benchmark.corpus.realworld;

import io.github.kalayciburak.stackmate.analysis.DiagnosticCategory;
import io.github.kalayciburak.stackmate.analysis.DiagnosticStrength;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.incident.JvmRuntimeContext;
import io.github.kalayciburak.stackmate.incident.RequestContext;
import io.github.kalayciburak.stackmate.incident.SpringRuntimeContext;
import io.github.kalayciburak.stackmate.incident.StackFrameSnapshot;
import io.github.kalayciburak.stackmate.inference.InferenceCauseCandidate;
import io.github.kalayciburak.stackmate.inference.InferenceCheckCandidate;
import io.github.kalayciburak.stackmate.inference.InferenceFact;
import io.github.kalayciburak.stackmate.inference.InferenceIncident;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import io.github.kalayciburak.stackmate.throwable.SqlExceptionDetails;
import io.github.kalayciburak.stackmate.throwable.ThrowableNode;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class RealWorldSyntheticFixtures {

    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private static final String PROBE_TYPE = "com.example.gatec.StructuralProbeException";
    private static final String PRIMARY_FACT = "fact.probe.primary";
    private static final String CLEANUP_FACT = "fact.probe.cleanup";

    private RealWorldSyntheticFixtures() {}

    static boolean supports(String familyId) {
        if (familyId == null) {
            return false;
        }
        return switch (familyId) {
            case "G53-S-TOP-001",
                    "G53-S-TOP-002",
                    "G53-S-TOP-003",
                    "G53-S-TOP-004",
                    "G53-S-TOP-005",
                    "G53-S-TOP-006",
                    "G53-S-TOP-007",
                    "G53-S-TOP-008",
                    "G53-S-TOP-009",
                    "G53-S-TOP-010",
                    "G53-S-CON-001",
                    "G53-S-CON-002",
                    "G53-S-CON-003",
                    "G53-S-CON-004",
                    "G53-S-CON-005",
                    "G53-S-CON-006",
                    "G53-S-CON-007",
                    "G53-S-CON-008",
                    "G53-S-CON-009",
                    "G53-S-CON-010",
                    "G53-S-SEL-001",
                    "G53-S-SEL-002",
                    "G53-S-SEL-003",
                    "G53-S-SEL-004",
                    "G53-S-SEL-005",
                    "G53-S-SEL-006",
                    "G53-S-SEL-007",
                    "G53-S-SEL-008",
                    "G53-S-SEL-009",
                    "G53-S-SEL-010" ->
                    true;
            default -> false;
        };
    }

    static Fixture create(String familyId, int variant) {
        if (variant != 0 && variant != 1) {
            throw new IllegalArgumentException("variant must be 0 or 1: " + variant);
        }
        if (!supports(familyId)) {
            throw new IllegalArgumentException("unsupported synthetic family: " + familyId);
        }

        if (familyId.startsWith("G53-S-TOP-")) {
            return new Fixture(topologyIncident(familyId, variant), null);
        }
        if (familyId.startsWith("G53-S-CON-")) {
            return new Fixture(conflictIncident(familyId, variant), null);
        }
        return selectionFixture(familyId, variant);
    }

    private static IncidentSnapshot topologyIncident(String familyId, int variant) {
        List<ThrowableNode> chain =
                switch (familyId) {
                    case "G53-S-TOP-001" ->
                            List.of(
                                    node(
                                            "com.example.gatec.OpaqueException",
                                            message("opaque primary failure", variant),
                                            0,
                                            false,
                                            List.of(
                                                    sqlNode(
                                                            "controlled numeric value out of range",
                                                            "22003",
                                                            1,
                                                            variant))));
                    case "G53-S-TOP-002" ->
                            List.of(
                                    node(
                                            "java.lang.UnsupportedOperationException",
                                            message("controlled immutable operation", variant),
                                            0,
                                            false,
                                            List.of(
                                                    node(
                                                            "java.net.SocketTimeoutException",
                                                            message(
                                                                    "controlled cleanup timeout",
                                                                    variant),
                                                            1,
                                                            false,
                                                            List.of()))));
                    case "G53-S-TOP-003" ->
                            List.of(
                                    node(
                                            "com.example.gatec.OpaqueException",
                                            message("opaque lock envelope", variant),
                                            0,
                                            false,
                                            List.of(
                                                    sqlNode(
                                                            "controlled lock not available",
                                                            "55P03",
                                                            1,
                                                            variant),
                                                    sqlNode(
                                                            "controlled query canceled",
                                                            "57014",
                                                            1,
                                                            variant))));
                    case "G53-S-TOP-004" ->
                            List.of(
                                    node(
                                            "java.lang.ClassNotFoundException",
                                            message("controlled outer class lookup", variant),
                                            0,
                                            false,
                                            List.of()),
                                    node(
                                            "java.lang.ClassNotFoundException",
                                            message("controlled root class lookup", variant),
                                            1,
                                            false,
                                            List.of()));
                    case "G53-S-TOP-005" ->
                            List.of(
                                    node(
                                            "com.example.gatec.DomainEnvelopeException",
                                            message("controlled domain envelope", variant),
                                            0,
                                            false,
                                            List.of()),
                                    node(
                                            "java.lang.StackOverflowError",
                                            message("controlled recursion exhausted", variant),
                                            1,
                                            false,
                                            List.of()));
                    case "G53-S-TOP-006" ->
                            List.of(
                                    node(
                                            "java.lang.reflect.UndeclaredThrowableException",
                                            message("controlled proxy wrapper", variant),
                                            0,
                                            true,
                                            List.of()),
                                    node(
                                            "java.lang.UnsupportedOperationException",
                                            message("controlled unsupported target", variant),
                                            1,
                                            false,
                                            List.of()));
                    case "G53-S-TOP-007" ->
                            List.of(
                                    node(
                                            "com.example.gatec.OpaqueException",
                                            message("opaque traversal envelope", variant),
                                            0,
                                            false,
                                            List.of(
                                                    node(
                                                            "java.util.ConcurrentModificationException",
                                                            message(
                                                                    "controlled suppressed collection mutation",
                                                                    variant),
                                                            1,
                                                            false,
                                                            List.of()))));
                    case "G53-S-TOP-008" ->
                            List.of(
                                    node(
                                            "com.example.gatec.PolicyEnvelopeException",
                                            message("controlled policy envelope", variant),
                                            0,
                                            false,
                                            List.of(
                                                    node(
                                                            "org.springframework.security.authentication.LockedException",
                                                            message(
                                                                    "controlled locked account",
                                                                    variant),
                                                            1,
                                                            false,
                                                            List.of()))));
                    case "G53-S-TOP-009" ->
                            List.of(
                                    node(
                                            "java.io.UncheckedIOException",
                                            message("controlled unchecked I/O wrapper", variant),
                                            0,
                                            true,
                                            List.of()),
                                    nodeWithoutFrames(
                                            "java.io.EOFException",
                                            message("controlled end of stream", variant),
                                            1,
                                            false,
                                            List.of()));
                    case "G53-S-TOP-010" ->
                            List.of(
                                    node(
                                            "java.lang.reflect.InvocationTargetException",
                                            message("controlled invocation wrapper", variant),
                                            0,
                                            true,
                                            List.of()),
                                    nodeWithoutFrames(
                                            "java.lang.UnsupportedOperationException",
                                            message("controlled frame-free root", variant),
                                            1,
                                            false,
                                            List.of()));
                    default -> throw new AssertionError("validated topology family: " + familyId);
                };
        return incident(familyId, variant, chain);
    }

    private static IncidentSnapshot conflictIncident(String familyId, int variant) {
        ThrowableNode root =
                switch (familyId) {
                    case "G53-S-CON-001" ->
                            sqlNode(
                                    "duplicate key value conflicts with a controlled unique index",
                                    "23514",
                                    0,
                                    variant);
                    case "G53-S-CON-002" ->
                            sqlNode(
                                    "connection pool capacity exhausted after controlled wait",
                                    "57014",
                                    0,
                                    variant);
                    case "G53-S-CON-003" ->
                            node(
                                    "java.lang.ClassNotFoundException",
                                    message("operation is unsupported by this collection", variant),
                                    0,
                                    false,
                                    List.of());
                    case "G53-S-CON-004" ->
                            node(
                                    "org.springframework.security.authentication.LockedException",
                                    message("database connection is unavailable", variant),
                                    0,
                                    false,
                                    List.of());
                    case "G53-S-CON-005" ->
                            node(
                                    "org.springframework.beans.factory.support.BeanDefinitionOverrideException",
                                    message("required dependency is missing", variant),
                                    0,
                                    false,
                                    List.of());
                    case "G53-S-CON-006" ->
                            node(
                                    "org.springframework.web.bind.MissingRequestAttributeException",
                                    message("JSON parse failed for controlled payload", variant),
                                    0,
                                    false,
                                    List.of());
                    case "G53-S-CON-007" ->
                            node(
                                    "java.lang.UnsupportedOperationException",
                                    message("index is outside the controlled range", variant),
                                    0,
                                    false,
                                    List.of());
                    case "G53-S-CON-008" ->
                            node(
                                    "org.springframework.web.servlet.resource.NoResourceFoundException",
                                    message("expired bearer token for controlled request", variant),
                                    0,
                                    false,
                                    List.of());
                    case "G53-S-CON-009" ->
                            node(
                                    "java.util.ConcurrentModificationException",
                                    message("connection refused by controlled endpoint", variant),
                                    0,
                                    false,
                                    List.of());
                    case "G53-S-CON-010" ->
                            sqlNode(
                                    "deadlock detected while updating controlled records",
                                    "42501",
                                    0,
                                    variant);
                    default -> throw new AssertionError("validated conflict family: " + familyId);
                };
        return incident(familyId, variant, List.of(root));
    }

    private static Fixture selectionFixture(String familyId, int variant) {
        IncidentSnapshot incident =
                incident(
                        familyId,
                        variant,
                        List.of(
                                node(
                                        PROBE_TYPE,
                                        message("controlled selection probe", variant),
                                        0,
                                        false,
                                        List.of())));
        SelectionInput input = selectionInput(familyId, variant);
        InferenceRequest request =
                new InferenceRequest(
                        InferenceRequest.SCHEMA_VERSION,
                        InferenceRequest.PROMPT_VERSION,
                        familyId.toLowerCase(Locale.ROOT) + "-v" + variant,
                        new InferenceIncident(input.category(), DiagnosticStrength.HIGH),
                        input.facts(),
                        input.causes(),
                        input.checks(),
                        List.of(),
                        2_048);
        return new Fixture(incident, request);
    }

    private static SelectionInput selectionInput(String familyId, int variant) {
        List<InferenceFact> primaryAndCleanup =
                List.of(
                        fact(PRIMARY_FACT, "THROWABLE_NODE", "primary throwable node"),
                        fact(CLEANUP_FACT, "SUPPRESSED_NODE", "cleanup throwable node"));
        return switch (familyId) {
            case "G53-S-SEL-001" ->
                    selection(
                            DiagnosticCategory.GENERAL_JAVA,
                            primaryAndCleanup,
                            List.of(cause("cause.probe.primary", PRIMARY_FACT)),
                            List.of(check("check.probe.cleanup", CLEANUP_FACT)));
            case "G53-S-SEL-002" ->
                    selection(
                            DiagnosticCategory.GENERAL_JAVA,
                            primaryAndCleanup,
                            List.of(cause("cause.probe.cleanup", CLEANUP_FACT)),
                            List.of(check("check.probe.primary", PRIMARY_FACT)));
            case "G53-S-SEL-003" -> {
                List<InferenceFact> facts =
                        List.of(
                                fact("fact.probe.sql-a", "SQL_STATE", "55P03"),
                                fact("fact.probe.sql-b", "SQL_STATE", "57014"));
                yield selection(
                        DiagnosticCategory.DATABASE,
                        facts,
                        List.of(
                                cause("cause.probe.primary-a", "fact.probe.sql-a"),
                                cause("cause.probe.primary-b", "fact.probe.sql-b")),
                        List.of(
                                check(
                                        "check.probe.shared",
                                        "fact.probe.sql-a",
                                        "fact.probe.sql-b")));
            }
            case "G53-S-SEL-004" ->
                    selection(
                            DiagnosticCategory.SPRING_CONFIGURATION,
                            List.of(
                                    fact(
                                            PRIMARY_FACT,
                                            "CONFIGURATION_SIGNAL",
                                            "controlled configuration registration")),
                            List.of(cause("cause.probe.primary", PRIMARY_FACT)),
                            List.of(
                                    check("check.probe.configuration", PRIMARY_FACT),
                                    check("check.probe.registration", PRIMARY_FACT)));
            case "G53-S-SEL-005" -> {
                List<InferenceFact> facts =
                        List.of(
                                fact("fact.probe.primary-a", "EXCEPTION_CLASS", PROBE_TYPE),
                                fact(
                                        "fact.probe.primary-b",
                                        "APPLICATION_LOCATION",
                                        "ControlledFixture.run"));
                yield selection(
                        DiagnosticCategory.GENERAL_JAVA,
                        facts,
                        List.of(
                                cause(
                                        "cause.probe.corroborated",
                                        "fact.probe.primary-a",
                                        "fact.probe.primary-b")),
                        List.of(
                                check(
                                        "check.probe.corroborated",
                                        "fact.probe.primary-a",
                                        "fact.probe.primary-b")));
            }
            case "G53-S-SEL-006" ->
                    selection(
                            DiagnosticCategory.UNKNOWN,
                            primaryAndCleanup,
                            List.of(
                                    causeWithText(
                                            "cause.probe.primary",
                                            "Inspect the controlled probe branch",
                                            PRIMARY_FACT),
                                    causeWithText(
                                            "cause.probe.cleanup",
                                            "Inspect the controlled probe branch",
                                            CLEANUP_FACT)),
                            List.of(
                                    checkWithText(
                                            "check.probe.primary",
                                            "Inspect the controlled probe branch",
                                            PRIMARY_FACT),
                                    checkWithText(
                                            "check.probe.cleanup",
                                            "Inspect the controlled probe branch",
                                            CLEANUP_FACT)));
            case "G53-S-SEL-007" ->
                    selection(
                            DiagnosticCategory.GENERAL_JAVA,
                            primaryAndCleanup,
                            List.of(
                                    cause("cause.probe.branch-a", PRIMARY_FACT),
                                    cause("cause.probe.branch-b", CLEANUP_FACT)),
                            List.of(
                                    check("check.probe.branch-a", PRIMARY_FACT),
                                    check("check.probe.branch-b", CLEANUP_FACT)));
            case "G53-S-SEL-008" ->
                    selection(
                            DiagnosticCategory.UNKNOWN,
                            primaryAndCleanup,
                            List.of(cause("probe.shared", PRIMARY_FACT)),
                            List.of(check("probe.shared", CLEANUP_FACT)));
            case "G53-S-SEL-009" ->
                    selection(
                            DiagnosticCategory.GENERAL_JAVA,
                            primaryAndCleanup,
                            maximumCauses(variant),
                            List.of(check("check.probe.primary", PRIMARY_FACT)));
            case "G53-S-SEL-010" ->
                    selection(
                            DiagnosticCategory.GENERAL_JAVA,
                            primaryAndCleanup,
                            List.of(cause("cause.probe.primary", PRIMARY_FACT)),
                            maximumChecks(variant));
            default -> throw new AssertionError("validated selection family: " + familyId);
        };
    }

    private static List<InferenceCauseCandidate> maximumCauses(int variant) {
        List<InferenceCauseCandidate> candidates = new ArrayList<>(InferenceRequest.MAXIMUM_CAUSES);
        candidates.add(cause("cause.probe.primary", PRIMARY_FACT));
        for (int index = 1; index < InferenceRequest.MAXIMUM_CAUSES; index++) {
            candidates.add(cause("cause.probe.cleanup-%02d".formatted(index), CLEANUP_FACT));
        }
        varyOrder(candidates, variant);
        return List.copyOf(candidates);
    }

    private static List<InferenceCheckCandidate> maximumChecks(int variant) {
        List<InferenceCheckCandidate> candidates = new ArrayList<>(InferenceRequest.MAXIMUM_CHECKS);
        candidates.add(check("check.probe.primary", PRIMARY_FACT));
        for (int index = 1; index < InferenceRequest.MAXIMUM_CHECKS; index++) {
            candidates.add(check("check.probe.cleanup-%02d".formatted(index), CLEANUP_FACT));
        }
        varyOrder(candidates, variant);
        return List.copyOf(candidates);
    }

    private static void varyOrder(List<?> candidates, int variant) {
        if (variant == 1) {
            Collections.rotate(candidates, -1);
        }
    }

    private static SelectionInput selection(
            DiagnosticCategory category,
            List<InferenceFact> facts,
            List<InferenceCauseCandidate> causes,
            List<InferenceCheckCandidate> checks) {
        return new SelectionInput(category, facts, causes, checks);
    }

    private static InferenceFact fact(String id, String type, String value) {
        return new InferenceFact(id, type, value);
    }

    private static InferenceCauseCandidate cause(String id, String... evidenceIds) {
        return causeWithText(id, "Investigate " + id, evidenceIds);
    }

    private static InferenceCauseCandidate causeWithText(
            String id, String text, String... evidenceIds) {
        return new InferenceCauseCandidate(id, text, List.of(evidenceIds));
    }

    private static InferenceCheckCandidate check(String id, String... evidenceIds) {
        return checkWithText(id, "Verify " + id, evidenceIds);
    }

    private static InferenceCheckCandidate checkWithText(
            String id, String text, String... evidenceIds) {
        return new InferenceCheckCandidate(id, text, List.of(evidenceIds));
    }

    private static IncidentSnapshot incident(
            String familyId, int variant, List<ThrowableNode> causeChain) {
        ThrowableSnapshot throwable =
                new ThrowableSnapshot(causeChain, countNodes(causeChain), false);
        List<StackFrameSnapshot> applicationFrames =
                causeChain.stream().flatMap(node -> node.frames().stream()).limit(1).toList();
        return new IncidentSnapshot(
                familyId.toLowerCase(Locale.ROOT) + "-v" + variant,
                FIXED_TIME.plusSeconds(variant),
                throwable,
                applicationFrames,
                RequestContext.none(),
                SpringRuntimeContext.unknown(),
                new JvmRuntimeContext(
                        "controlled-java",
                        "controlled-vm",
                        "controlled-vendor",
                        "controlled-os",
                        "controlled-arch",
                        1));
    }

    private static int countNodes(List<ThrowableNode> causeChain) {
        return causeChain.stream().mapToInt(RealWorldSyntheticFixtures::countNode).sum();
    }

    private static int countNode(ThrowableNode node) {
        return 1 + node.suppressed().stream().mapToInt(RealWorldSyntheticFixtures::countNode).sum();
    }

    private static ThrowableNode sqlNode(String text, String sqlState, int depth, int variant) {
        return node(
                "java.sql.SQLException",
                message(text, variant),
                depth,
                false,
                new SqlExceptionDetails(sqlState, 0),
                List.of(),
                true);
    }

    private static ThrowableNode node(
            String type, String text, int depth, boolean wrapper, List<ThrowableNode> suppressed) {
        return node(type, text, depth, wrapper, SqlExceptionDetails.none(), suppressed, true);
    }

    private static ThrowableNode nodeWithoutFrames(
            String type, String text, int depth, boolean wrapper, List<ThrowableNode> suppressed) {
        return node(type, text, depth, wrapper, SqlExceptionDetails.none(), suppressed, false);
    }

    private static ThrowableNode node(
            String type,
            String text,
            int depth,
            boolean wrapper,
            SqlExceptionDetails sql,
            List<ThrowableNode> suppressed,
            boolean includeFrame) {
        return new ThrowableNode(
                type,
                text,
                includeFrame ? List.of(frame(depth)) : List.of(),
                suppressed,
                sql,
                depth,
                wrapper,
                false,
                false,
                false,
                false,
                List.of());
    }

    private static StackFrameSnapshot frame(int depth) {
        return new StackFrameSnapshot(
                "com.example.gatec.ControlledFixture",
                "run",
                "ControlledFixture.java",
                40 + depth,
                false,
                "",
                "",
                "app",
                depth);
    }

    private static String message(String text, int variant) {
        return text + " [variant-" + variant + ']';
    }

    record Fixture(IncidentSnapshot incident, InferenceRequest requestOverride) {}

    private record SelectionInput(
            DiagnosticCategory category,
            List<InferenceFact> facts,
            List<InferenceCauseCandidate> causes,
            List<InferenceCheckCandidate> checks) {}
}
