package io.github.kalayciburak.stackmate.inference;

import java.util.List;

/** A deterministic next-check candidate that model output may select by stable ID. */
public record InferenceCheckCandidate(String id, String text, List<String> evidenceIds) {

    public InferenceCheckCandidate {
        id = Contracts.requireStableId(id, "id");
        text = Contracts.requireBoundedText(text, "text", 1_024);
        evidenceIds = Contracts.immutableStableIds(evidenceIds, "evidenceIds", 32);
        if (evidenceIds.isEmpty()) {
            throw new IllegalArgumentException("evidenceIds must not be empty");
        }
    }
}
