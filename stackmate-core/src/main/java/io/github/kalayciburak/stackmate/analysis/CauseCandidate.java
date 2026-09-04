package io.github.kalayciburak.stackmate.analysis;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A bounded possible cause whose factual basis is expressed only through evidence IDs.
 *
 * @param id stable identifier of the candidate
 * @param category diagnostic category assigned to the candidate
 * @param title concise display title
 * @param explanation evidence-grounded explanation of the possible cause
 * @param evidenceIds identifiers of the evidence supporting the candidate
 */
public record CauseCandidate(
        String id,
        DiagnosticCategory category,
        String title,
        String explanation,
        List<String> evidenceIds) {

    private static final Pattern STABLE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");

    /**
     * Validates and creates a cause candidate.
     *
     * @param id stable identifier of the candidate
     * @param category diagnostic category assigned to the candidate
     * @param title concise display title
     * @param explanation evidence-grounded explanation of the possible cause
     * @param evidenceIds identifiers of the evidence supporting the candidate
     */
    public CauseCandidate {
        id = requireStableId(id);
        category = Objects.requireNonNull(category, "category");
        title = requireText(title, "title");
        explanation = requireText(explanation, "explanation");
        evidenceIds = immutableEvidenceIds(evidenceIds);
    }

    private static String requireStableId(String value) {
        Objects.requireNonNull(value, "id");
        if (!STABLE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("id must be a stable lowercase identifier");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static List<String> immutableEvidenceIds(List<String> values) {
        Objects.requireNonNull(values, "evidenceIds");
        List<String> copy = List.copyOf(values);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("evidenceIds must not be empty");
        }
        copy.forEach(CauseCandidate::requireStableId);
        if (copy.stream().distinct().count() != copy.size()) {
            throw new IllegalArgumentException("evidenceIds must be unique");
        }
        return copy;
    }
}
