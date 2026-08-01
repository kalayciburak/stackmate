package io.github.kalayciburak.stackmate.inference;

import java.util.List;

/** An untrusted structured next-check selection produced by a model. */
public record StructuredCheck(
        String checkId, String instruction, List<String> factIds, List<String> sourceLocationIds) {

    public StructuredCheck {
        checkId = Contracts.requireStableId(checkId, "checkId");
        instruction = Contracts.requireBoundedText(instruction, "instruction", 2_048);
        factIds = Contracts.immutableStableIds(factIds, "factIds", 64);
        sourceLocationIds =
                Contracts.immutableStableIds(sourceLocationIds, "sourceLocationIds", 64);
    }
}
