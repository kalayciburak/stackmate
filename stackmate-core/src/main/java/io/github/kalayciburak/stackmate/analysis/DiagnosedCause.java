package io.github.kalayciburak.stackmate.analysis;

import java.util.List;
import java.util.Objects;

/**
 * A cause candidate after deterministic evidence-strength composition.
 *
 * @param id stable identifier of the cause
 * @param category diagnostic category assigned to the cause
 * @param title concise display title
 * @param explanation evidence-grounded explanation of the cause
 * @param strength deterministic strength of the supporting evidence
 * @param evidenceIds identifiers of the evidence supporting the cause
 */
public record DiagnosedCause(
        String id,
        DiagnosticCategory category,
        String title,
        String explanation,
        DiagnosticStrength strength,
        List<String> evidenceIds) {

    /**
     * Validates and creates a diagnosed cause.
     *
     * @param id stable identifier of the cause
     * @param category diagnostic category assigned to the cause
     * @param title concise display title
     * @param explanation evidence-grounded explanation of the cause
     * @param strength deterministic strength of the supporting evidence
     * @param evidenceIds identifiers of the evidence supporting the cause
     */
    public DiagnosedCause {
        CauseCandidate validated =
                new CauseCandidate(id, category, title, explanation, evidenceIds);
        id = validated.id();
        category = validated.category();
        title = validated.title();
        explanation = validated.explanation();
        strength = Objects.requireNonNull(strength, "strength");
        evidenceIds = validated.evidenceIds();
    }
}
