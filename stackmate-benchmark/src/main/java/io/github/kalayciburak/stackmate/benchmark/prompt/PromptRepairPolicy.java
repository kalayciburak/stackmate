package io.github.kalayciburak.stackmate.benchmark.prompt;

import io.github.kalayciburak.stackmate.inference.StructuredInferenceOutput;
import java.util.List;
import java.util.Set;

/** Bounded structural-repair policy shared by the explicit prompt qualification lab. */
public final class PromptRepairPolicy {

    private static final Set<String> REPAIRABLE_ISSUES =
            Set.of(
                    "SCHEMA_OR_JSON",
                    "UNKNOWN_SCHEMA_VERSION",
                    "UNKNOWN_FACT_ID",
                    "UNKNOWN_CAUSE_ID",
                    "UNKNOWN_CHECK_ID",
                    "DUPLICATE_SELECTION",
                    "EXCESSIVE_LENGTH");

    private PromptRepairPolicy() {}

    /** Returns true only for a non-empty set of structural/schema issue codes. */
    public static boolean isRepairEligible(List<String> issueCodes) {
        return !issueCodes.isEmpty() && REPAIRABLE_ISSUES.containsAll(issueCodes);
    }

    /** Repair may remove invalid IDs but may not add or rewrite semantic content. */
    public static boolean preservesMeaning(
            StructuredInferenceOutput initial, StructuredInferenceOutput repaired) {
        if (initial == null) {
            return true;
        }
        return initial.summary().equals(repaired.summary())
                && initial.explanation().equals(repaired.explanation())
                && initial.limitations().equals(repaired.limitations())
                && initial.selectedCauseIds().containsAll(repaired.selectedCauseIds())
                && initial.selectedCheckIds().containsAll(repaired.selectedCheckIds())
                && initial.usedEvidenceIds().containsAll(repaired.usedEvidenceIds());
    }
}
