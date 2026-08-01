package io.github.kalayciburak.stackmate.inference;

import java.util.List;

/** A sanitized fact supplied to a local model. */
public record InferenceFact(String id, String text, List<String> sourceLocationIds) {

    public InferenceFact {
        id = Contracts.requireStableId(id, "id");
        text = Contracts.requireBoundedText(text, "text", 2_048);
        sourceLocationIds =
                Contracts.immutableStableIds(sourceLocationIds, "sourceLocationIds", 16);
    }
}
