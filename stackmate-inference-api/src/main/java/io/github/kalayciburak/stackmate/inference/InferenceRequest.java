package io.github.kalayciburak.stackmate.inference;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A bounded, sanitized explanation request derived only from deterministic evidence. */
public record InferenceRequest(
        String schemaVersion,
        String promptVersion,
        String id,
        InferenceLanguage language,
        InferenceIncident incident,
        List<InferenceFact> facts,
        List<InferenceCauseCandidate> candidateCauses,
        List<InferenceCheckCandidate> candidateChecks,
        List<InferenceTerminology> terminology,
        int maximumOutputCharacters) {

    public static final String SCHEMA_VERSION = "2";
    public static final String PROMPT_VERSION = "2";
    public static final int MAXIMUM_FACTS = 128;
    public static final int MAXIMUM_CAUSES = 32;
    public static final int MAXIMUM_CHECKS = 32;
    public static final int MAXIMUM_TERMS = 32;
    public static final int MAXIMUM_TOTAL_CHARACTERS = 16_000;
    public static final int MAXIMUM_OUTPUT_CHARACTERS = 8_192;

    public InferenceRequest {
        schemaVersion = exactVersion(schemaVersion, SCHEMA_VERSION, "schemaVersion");
        promptVersion = exactVersion(promptVersion, PROMPT_VERSION, "promptVersion");
        id = Contracts.requireStableId(id, "id");
        language = Objects.requireNonNull(language, "language");
        incident = Objects.requireNonNull(incident, "incident");
        facts = immutableBounded(facts, "facts", MAXIMUM_FACTS);
        candidateCauses = immutableBounded(candidateCauses, "candidateCauses", MAXIMUM_CAUSES);
        candidateChecks = immutableBounded(candidateChecks, "candidateChecks", MAXIMUM_CHECKS);
        terminology = immutableBounded(terminology, "terminology", MAXIMUM_TERMS);
        if (facts.isEmpty()) {
            throw new IllegalArgumentException("facts must not be empty");
        }
        if (maximumOutputCharacters < 1 || maximumOutputCharacters > MAXIMUM_OUTPUT_CHARACTERS) {
            throw new IllegalArgumentException(
                    "maximumOutputCharacters is outside the supported range");
        }
        requireUniqueIds(facts.stream().map(InferenceFact::id).toList(), "fact");
        requireUniqueIds(
                candidateCauses.stream().map(InferenceCauseCandidate::id).toList(), "cause");
        requireUniqueIds(
                candidateChecks.stream().map(InferenceCheckCandidate::id).toList(), "check");
        validateReferences(facts, candidateCauses, candidateChecks);
        if (totalCharacters(facts, candidateCauses, candidateChecks, terminology)
                > MAXIMUM_TOTAL_CHARACTERS) {
            throw new IllegalArgumentException("request exceeds the total character limit");
        }
    }

    private static String exactVersion(String value, String expected, String name) {
        String version = Contracts.requireBoundedText(value, name, 16);
        if (!version.equals(expected)) {
            throw new IllegalArgumentException(name + " must be " + expected);
        }
        return version;
    }

    private static <T> List<T> immutableBounded(List<T> values, String name, int maximum) {
        Objects.requireNonNull(values, name);
        List<T> copy = List.copyOf(values);
        if (copy.size() > maximum) {
            throw new IllegalArgumentException(name + " exceeds limit " + maximum);
        }
        return copy;
    }

    private static void requireUniqueIds(List<String> values, String type) {
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException(type + " IDs must be unique");
        }
    }

    private static void validateReferences(
            List<InferenceFact> facts,
            List<InferenceCauseCandidate> causes,
            List<InferenceCheckCandidate> checks) {
        Set<String> evidenceIds =
                facts.stream().map(InferenceFact::id).collect(java.util.stream.Collectors.toSet());
        causes.forEach(
                cause -> requireSubset(cause.evidenceIds(), evidenceIds, "unknown cause evidence"));
        checks.forEach(
                check -> requireSubset(check.evidenceIds(), evidenceIds, "unknown check evidence"));
    }

    private static void requireSubset(List<String> values, Set<String> known, String message) {
        if (!known.containsAll(values)) {
            throw new IllegalArgumentException(message + " reference");
        }
    }

    private static int totalCharacters(
            List<InferenceFact> facts,
            List<InferenceCauseCandidate> causes,
            List<InferenceCheckCandidate> checks,
            List<InferenceTerminology> terminology) {
        long total =
                facts.stream()
                        .mapToLong(
                                fact ->
                                        fact.id().length()
                                                + fact.type().length()
                                                + fact.value().length())
                        .sum();
        total +=
                causes.stream()
                        .mapToLong(cause -> cause.id().length() + cause.text().length())
                        .sum();
        total +=
                checks.stream()
                        .mapToLong(check -> check.id().length() + check.text().length())
                        .sum();
        total +=
                terminology.stream()
                        .mapToLong(term -> term.term().length() + term.definition().length())
                        .sum();
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }
}
