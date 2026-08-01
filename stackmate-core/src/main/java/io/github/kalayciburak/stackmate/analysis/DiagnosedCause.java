package io.github.kalayciburak.stackmate.analysis;

import java.util.List;
import java.util.Objects;

/** A cause candidate after deterministic evidence-strength composition. */
public record DiagnosedCause(
        String id,
        DiagnosticCategory category,
        String title,
        String explanation,
        DiagnosticStrength strength,
        List<String> evidenceIds) {

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
