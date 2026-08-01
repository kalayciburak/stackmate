package io.github.kalayciburak.stackmate.inference;

import java.util.List;

/** A deterministic next-check candidate that model output may select by stable ID. */
public record InferenceCheckCandidate(String id, String instruction, List<String> factIds) {

    public InferenceCheckCandidate {
        id = Contracts.requireStableId(id, "id");
        instruction = Contracts.requireBoundedText(instruction, "instruction", 1_024);
        factIds = Contracts.immutableStableIds(factIds, "factIds", 32);
        if (factIds.isEmpty()) {
            throw new IllegalArgumentException("factIds must not be empty");
        }
    }
}
