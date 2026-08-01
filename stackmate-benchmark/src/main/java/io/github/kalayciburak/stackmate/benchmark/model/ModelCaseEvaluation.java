package io.github.kalayciburak.stackmate.benchmark.model;

import java.util.List;

/** One measured local-model invocation and its deterministic validation results. */
public record ModelCaseEvaluation(
        String caseId,
        String mode,
        String status,
        boolean coldStart,
        long latencyMilliseconds,
        int inputCharacters,
        int outputCharacters,
        boolean categoryMatched,
        boolean rootTypeMatched,
        Boolean causeIdsMatched,
        Boolean checkIdsMatched,
        int invalidOutputCount,
        int unknownIdCount,
        int fabricatedSourceReferenceCount,
        int forbiddenClaimCount,
        int secretLeakageCount,
        int promptInjectionFailureCount,
        String validatedSummary,
        String sanitizedFailureMessage,
        List<String> failureCodes) {

    public ModelCaseEvaluation {
        validatedSummary = validatedSummary == null ? "" : validatedSummary;
        sanitizedFailureMessage = sanitizedFailureMessage == null ? "" : sanitizedFailureMessage;
        failureCodes = List.copyOf(failureCodes);
    }
}
