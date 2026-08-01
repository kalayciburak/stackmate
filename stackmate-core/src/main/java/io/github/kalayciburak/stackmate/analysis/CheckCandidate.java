package io.github.kalayciburak.stackmate.analysis;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** A safe next check tied to the evidence that made it relevant. */
public record CheckCandidate(String id, String instruction, List<String> evidenceIds) {

    private static final Pattern STABLE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");

    public CheckCandidate {
        id = requireStableId(id);
        instruction = Objects.requireNonNull(instruction, "instruction");
        if (instruction.isBlank()) {
            throw new IllegalArgumentException("instruction must not be blank");
        }
        evidenceIds = List.copyOf(Objects.requireNonNull(evidenceIds, "evidenceIds"));
        if (evidenceIds.isEmpty()) {
            throw new IllegalArgumentException("evidenceIds must not be empty");
        }
        evidenceIds.forEach(CheckCandidate::requireStableId);
        if (evidenceIds.stream().distinct().count() != evidenceIds.size()) {
            throw new IllegalArgumentException("evidenceIds must be unique");
        }
    }

    private static String requireStableId(String value) {
        Objects.requireNonNull(value, "id");
        if (!STABLE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("id must be a stable lowercase identifier");
        }
        return value;
    }
}
