package io.github.kalayciburak.stackmate.inference;

import java.util.List;

/** Trusted model-derived output created only after request-scoped validation. */
public final class ValidatedInferenceOutput {

    private final InferenceLanguage language;
    private final String summary;
    private final List<StructuredCause> causes;
    private final List<StructuredCheck> checks;

    ValidatedInferenceOutput(StructuredInferenceOutput output) {
        this.language = output.language();
        this.summary = output.summary();
        this.causes = output.causes();
        this.checks = output.checks();
    }

    public InferenceLanguage language() {
        return language;
    }

    public String summary() {
        return summary;
    }

    public List<StructuredCause> causes() {
        return causes;
    }

    public List<StructuredCheck> checks() {
        return checks;
    }
}
