package io.github.kalayciburak.stackmate.inference;

import io.github.kalayciburak.stackmate.analysis.DeterministicResult;
import io.github.kalayciburak.stackmate.analysis.DiagnosticStrength;
import io.github.kalayciburak.stackmate.configuration.InferenceTriggerPolicy;
import java.util.Objects;

/** Applies the configured local-inference policy to trusted deterministic evidence. */
final class EvidenceAwareInferencePolicy {

    boolean shouldInfer(DeterministicResult result, InferenceTriggerPolicy policy) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(policy, "policy");
        return switch (policy) {
            case NEVER -> false;
            case ALWAYS -> true;
            case WHEN_ENHANCEMENT_IS_USEFUL -> enhancementIsUseful(result);
        };
    }

    private boolean enhancementIsUseful(DeterministicResult result) {
        var diagnosis = result.diagnosis();
        return !diagnosis.fallback()
                && diagnosis.causes().getFirst().strength() == DiagnosticStrength.MEDIUM;
    }
}
