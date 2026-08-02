package io.github.kalayciburak.stackmate.benchmark.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.inference.StructuredInferenceOutput;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptRepairPolicyTest {

    @Test
    void permitsOnlyBoundedStructuralFailures() {
        assertThat(PromptRepairPolicy.isRepairEligible(List.of("SCHEMA_OR_JSON"))).isTrue();
        assertThat(
                        PromptRepairPolicy.isRepairEligible(
                                List.of("UNKNOWN_CAUSE_ID", "UNKNOWN_CHECK_ID")))
                .isTrue();
        assertThat(PromptRepairPolicy.isRepairEligible(List.of("EXCESSIVE_LENGTH"))).isTrue();
        assertThat(PromptRepairPolicy.isRepairEligible(List.of())).isFalse();
    }

    @Test
    void neverRepairsSemanticGroundingOrRestraintFailures() {
        assertThat(PromptRepairPolicy.isRepairEligible(List.of("CASE_FORBIDDEN_CLAIM"))).isFalse();
        assertThat(PromptRepairPolicy.isRepairEligible(List.of("MISSING_LIMITATION"))).isFalse();
        assertThat(PromptRepairPolicy.isRepairEligible(List.of("CONTRADICTORY_SELECTION")))
                .isFalse();
    }

    @Test
    void allowsInvalidIdsToBeRemovedWithoutAddingMeaning() {
        StructuredInferenceOutput initial =
                output(List.of("cause.one", "cause.invalid"), List.of("check.one"));
        StructuredInferenceOutput repaired = output(List.of("cause.one"), List.of("check.one"));

        assertThat(PromptRepairPolicy.preservesMeaning(initial, repaired)).isTrue();
    }

    @Test
    void rejectsAddedSelectionsOrRewrittenTechnicalProse() {
        StructuredInferenceOutput initial = output(List.of("cause.one"), List.of("check.one"));
        StructuredInferenceOutput added =
                output(List.of("cause.one", "cause.two"), List.of("check.one"));
        StructuredInferenceOutput rewritten =
                new StructuredInferenceOutput(
                        "2",
                        "The driver may also be faulty.",
                        initial.explanation(),
                        initial.selectedCauseIds(),
                        initial.selectedCheckIds(),
                        initial.usedEvidenceIds(),
                        initial.limitations());

        assertThat(PromptRepairPolicy.preservesMeaning(initial, added)).isFalse();
        assertThat(PromptRepairPolicy.preservesMeaning(initial, rewritten)).isFalse();
    }

    private StructuredInferenceOutput output(List<String> causes, List<String> checks) {
        return new StructuredInferenceOutput(
                "2",
                "The supplied cause is supported.",
                "The supplied evidence supports the selection.",
                causes,
                checks,
                List.of("fact.one"),
                List.of());
    }
}
