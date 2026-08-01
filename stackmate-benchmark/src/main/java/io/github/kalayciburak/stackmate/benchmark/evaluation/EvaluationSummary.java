package io.github.kalayciburak.stackmate.benchmark.evaluation;

import java.util.List;

/** Aggregate actual metrics produced by the deterministic benchmark harness. */
public record EvaluationSummary(
        int caseCount,
        MetricScore categoryAccuracy,
        MetricScore rootTypeAccuracy,
        MetricScore factExactAccuracy,
        MetricScore factPrecision,
        MetricScore factRecall,
        MetricScore causeAccuracy,
        MetricScore checkAccuracy,
        int fabricatedSourceReferenceCount,
        int unknownIdCount,
        int forbiddenClaimCount,
        int secretLeakageCount,
        int promptInjectionFailureCount,
        int totalInputCharacters,
        int totalOutputCharacters,
        List<CaseEvaluation> cases) {

    public EvaluationSummary {
        cases = List.copyOf(cases);
    }
}
