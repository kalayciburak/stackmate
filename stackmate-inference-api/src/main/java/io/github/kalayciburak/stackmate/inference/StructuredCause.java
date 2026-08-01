package io.github.kalayciburak.stackmate.inference;

import java.util.List;

/** An untrusted structured cause selection produced by a model. */
public record StructuredCause(
        String causeId, String explanation, List<String> factIds, List<String> sourceLocationIds) {

    public StructuredCause {
        causeId = Contracts.requireStableId(causeId, "causeId");
        explanation = Contracts.requireBoundedText(explanation, "explanation", 4_096);
        factIds = Contracts.immutableStableIds(factIds, "factIds", 64);
        sourceLocationIds =
                Contracts.immutableStableIds(sourceLocationIds, "sourceLocationIds", 64);
    }
}
