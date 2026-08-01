package io.github.kalayciburak.stackmate.inference;

import java.util.List;

/** A deterministic cause candidate that model output may select by stable ID. */
public record InferenceCauseCandidate(String id, String text, List<String> evidenceIds) {

    public InferenceCauseCandidate {
        id = Contracts.requireStableId(id, "id");
        text = Contracts.requireBoundedText(text, "text", 2_048);
        evidenceIds = Contracts.immutableStableIds(evidenceIds, "evidenceIds", 32);
        if (evidenceIds.isEmpty()) {
            throw new IllegalArgumentException("evidenceIds must not be empty");
        }
    }
}
