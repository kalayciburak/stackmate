package io.github.kalayciburak.stackmate.inference;

import java.util.List;

/** A deterministic cause candidate that model output may select by stable ID. */
public record InferenceCauseCandidate(
        String id, String title, String explanation, List<String> factIds) {

    public InferenceCauseCandidate {
        id = Contracts.requireStableId(id, "id");
        title = Contracts.requireBoundedText(title, "title", 256);
        explanation = Contracts.requireBoundedText(explanation, "explanation", 2_048);
        factIds = Contracts.immutableStableIds(factIds, "factIds", 32);
        if (factIds.isEmpty()) {
            throw new IllegalArgumentException("factIds must not be empty");
        }
    }
}
