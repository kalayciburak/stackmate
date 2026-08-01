package io.github.kalayciburak.stackmate.inference;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Trusted model prose with selections resolved back to deterministic candidates. */
public final class ValidatedInferenceOutput {

    private final String summary;
    private final String explanation;
    private final List<InferenceCauseCandidate> selectedCauses;
    private final List<InferenceCheckCandidate> selectedChecks;
    private final List<String> usedEvidenceIds;
    private final List<String> limitations;

    ValidatedInferenceOutput(InferenceRequest request, StructuredInferenceOutput output) {
        Map<String, InferenceCauseCandidate> causes =
                request.candidateCauses().stream()
                        .collect(
                                Collectors.toMap(InferenceCauseCandidate::id, Function.identity()));
        Map<String, InferenceCheckCandidate> checks =
                request.candidateChecks().stream()
                        .collect(
                                Collectors.toMap(InferenceCheckCandidate::id, Function.identity()));
        this.summary = output.summary();
        this.explanation = output.explanation();
        this.selectedCauses = output.selectedCauseIds().stream().map(causes::get).toList();
        this.selectedChecks = output.selectedCheckIds().stream().map(checks::get).toList();
        this.usedEvidenceIds = output.usedEvidenceIds();
        this.limitations = output.limitations();
    }

    public String summary() {
        return summary;
    }

    public String explanation() {
        return explanation;
    }

    public List<InferenceCauseCandidate> selectedCauses() {
        return selectedCauses;
    }

    public List<InferenceCheckCandidate> selectedChecks() {
        return selectedChecks;
    }

    public List<String> usedEvidenceIds() {
        return usedEvidenceIds;
    }

    public List<String> limitations() {
        return limitations;
    }
}
