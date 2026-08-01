package io.github.kalayciburak.stackmate.inference;

import io.github.kalayciburak.stackmate.analysis.DiagnosticCategory;
import io.github.kalayciburak.stackmate.analysis.DiagnosticStrength;
import java.util.Objects;

/** Trusted deterministic incident classification supplied to, but never owned by, a model. */
public record InferenceIncident(DiagnosticCategory category, DiagnosticStrength evidenceStrength) {

    public InferenceIncident {
        category = Objects.requireNonNull(category, "category");
        evidenceStrength = Objects.requireNonNull(evidenceStrength, "evidenceStrength");
    }
}
