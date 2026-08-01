package io.github.kalayciburak.stackmate.inference;

/** A bounded deterministic definition the model may rephrase. */
public record InferenceTerminology(String term, String definition) {

    public InferenceTerminology {
        term = Contracts.requireBoundedText(term, "term", 128);
        definition = Contracts.requireBoundedText(definition, "definition", 512);
    }
}
