package io.github.kalayciburak.stackmate.analysis;

import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import java.util.Map;

/** Diagnoses common JDK failures and records wrapper nodes without treating them as root causes. */
public final class GeneralJavaAnalyzer implements DiagnosticAnalyzer {

    public static final String ID = "general-java";

    private static final Map<String, Rule> RULES =
            Map.ofEntries(
                    rule(
                            "java.lang.NullPointerException",
                            "cause.java.null-reference",
                            "Null reference was dereferenced",
                            "An exact NullPointerException was captured.",
                            "check.java.null-reference",
                            "Inspect the first application frame and the values dereferenced there."),
                    rule(
                            "java.lang.IllegalArgumentException",
                            "cause.java.invalid-argument",
                            "A method rejected an argument",
                            "An exact IllegalArgumentException was captured.",
                            "check.java.invalid-argument",
                            "Check the caller contract and the argument values at the first application frame."),
                    rule(
                            "java.lang.IllegalStateException",
                            "cause.java.invalid-state",
                            "An operation ran in an invalid state",
                            "An exact IllegalStateException was captured.",
                            "check.java.invalid-state",
                            "Check lifecycle and state transitions before the first application frame."),
                    rule(
                            "java.lang.UnsupportedOperationException",
                            "cause.java.unsupported-operation",
                            "The requested operation was not supported",
                            "An exact UnsupportedOperationException was captured.",
                            "check.java.unsupported-operation",
                            "Check the concrete implementation and whether the operation is optional or immutable."),
                    rule(
                            "java.lang.ClassCastException",
                            "cause.java.incompatible-runtime-type",
                            "A value had an incompatible runtime type",
                            "An exact ClassCastException was captured.",
                            "check.java.runtime-type",
                            "Inspect the producing expression and generic type boundary before the cast."),
                    rule(
                            "java.lang.IndexOutOfBoundsException",
                            "cause.java.index-out-of-bounds",
                            "An index was outside the available range",
                            "An exact IndexOutOfBoundsException was captured.",
                            "check.java.index-range",
                            "Check collection or array size and the computed index."),
                    rule(
                            "java.lang.NumberFormatException",
                            "cause.java.invalid-number",
                            "Text could not be parsed as the requested number",
                            "An exact NumberFormatException was captured.",
                            "check.java.number-input",
                            "Check the input format and locale assumptions at the parsing boundary."),
                    rule(
                            "java.lang.ClassNotFoundException",
                            "cause.java.class-not-found",
                            "A requested class was not available",
                            "An exact ClassNotFoundException was captured.",
                            "check.java.runtime-classpath",
                            "Check the runtime classpath, module path, and requested class name."),
                    rule(
                            "java.lang.NoClassDefFoundError",
                            "cause.java.class-definition-missing",
                            "A class definition was unavailable at runtime",
                            "An exact NoClassDefFoundError was captured.",
                            "check.java.runtime-classpath",
                            "Check the runtime classpath, module path, and requested class name."),
                    rule(
                            "java.lang.OutOfMemoryError",
                            "cause.java.out-of-memory",
                            "The JVM could not allocate required memory",
                            "An exact OutOfMemoryError was captured.",
                            "check.java.memory",
                            "Inspect the JVM memory pool, allocation pressure, and configured limits."),
                    rule(
                            "java.lang.StackOverflowError",
                            "cause.java.stack-overflow",
                            "The thread stack was exhausted",
                            "An exact StackOverflowError was captured.",
                            "check.java.recursion",
                            "Inspect repeated frames for unbounded recursion or cyclic callbacks."),
                    rule(
                            "java.util.ConcurrentModificationException",
                            "cause.java.concurrent-modification",
                            "A collection changed during an incompatible traversal",
                            "An exact ConcurrentModificationException was captured.",
                            "check.java.collection-mutation",
                            "Inspect collection mutation during iteration and cross-thread access."),
                    rule(
                            "java.util.concurrent.TimeoutException",
                            "cause.java.concurrent-timeout",
                            "An asynchronous operation exceeded its deadline",
                            "An exact TimeoutException was captured.",
                            "check.java.concurrent-timeout",
                            "Check the awaited operation, deadline, executor saturation, and downstream latency."),
                    rule(
                            "java.util.concurrent.RejectedExecutionException",
                            "cause.java.execution-rejected",
                            "An executor rejected submitted work",
                            "An exact RejectedExecutionException was captured.",
                            "check.java.executor-capacity",
                            "Check executor shutdown state, queue capacity, rejection policy, and task arrival rate."));

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public AnalysisContribution analyze(IncidentSnapshot incident) {
        ContributionBuilder contribution = new ContributionBuilder(ID);
        for (AnalyzerSupport.NodeFact fact : AnalyzerSupport.nodes(incident.throwable())) {
            if (fact.node().wrapper()) {
                contribution.addWrapperEvidence("java", fact);
                continue;
            }
            Rule rule = RULES.get(fact.node().type());
            if (rule == null) {
                continue;
            }
            String evidenceId = contribution.addClassEvidence("java", fact);
            contribution.addCause(
                    rule.causeId(),
                    categoryFor(rule.causeId()),
                    rule.title(),
                    rule.explanation(),
                    evidenceId);
            contribution.addCheck(rule.checkId(), rule.check(), evidenceId);
        }
        return contribution.build();
    }

    private static Map.Entry<String, Rule> rule(
            String type,
            String causeId,
            String title,
            String explanation,
            String checkId,
            String check) {
        return Map.entry(type, new Rule(causeId, title, explanation, checkId, check));
    }

    private static DiagnosticCategory categoryFor(String causeId) {
        if (causeId.equals("cause.java.out-of-memory")
                || causeId.equals("cause.java.stack-overflow")
                || causeId.equals("cause.java.execution-rejected")) {
            return DiagnosticCategory.RESOURCE_EXHAUSTION;
        }
        if (causeId.equals("cause.java.concurrent-modification")) {
            return DiagnosticCategory.CONCURRENCY;
        }
        if (causeId.equals("cause.java.concurrent-timeout")) {
            return DiagnosticCategory.TIMEOUT;
        }
        return DiagnosticCategory.GENERAL_JAVA;
    }

    private record Rule(
            String causeId, String title, String explanation, String checkId, String check) {}
}
