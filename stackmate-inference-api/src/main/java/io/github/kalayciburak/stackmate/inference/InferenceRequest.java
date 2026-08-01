package io.github.kalayciburak.stackmate.inference;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A bounded, sanitized request containing only stable, referenceable inputs. */
public record InferenceRequest(
        String id,
        InferenceLanguage language,
        List<InferenceFact> facts,
        List<InferenceCauseCandidate> causes,
        List<InferenceCheckCandidate> checks,
        List<SourceLocation> sourceLocations,
        int maximumOutputCharacters) {

    public static final int MAXIMUM_FACTS = 128;
    public static final int MAXIMUM_CAUSES = 32;
    public static final int MAXIMUM_CHECKS = 32;
    public static final int MAXIMUM_SOURCE_LOCATIONS = 128;
    public static final int MAXIMUM_TOTAL_CHARACTERS = 16_000;
    public static final int MAXIMUM_OUTPUT_CHARACTERS = 8_192;

    public InferenceRequest {
        id = Contracts.requireStableId(id, "id");
        language = Objects.requireNonNull(language, "language");
        facts = immutableBounded(facts, "facts", MAXIMUM_FACTS);
        causes = immutableBounded(causes, "causes", MAXIMUM_CAUSES);
        checks = immutableBounded(checks, "checks", MAXIMUM_CHECKS);
        sourceLocations =
                immutableBounded(sourceLocations, "sourceLocations", MAXIMUM_SOURCE_LOCATIONS);
        if (facts.isEmpty()) {
            throw new IllegalArgumentException("facts must not be empty");
        }
        if (maximumOutputCharacters < 1 || maximumOutputCharacters > MAXIMUM_OUTPUT_CHARACTERS) {
            throw new IllegalArgumentException(
                    "maximumOutputCharacters is outside the supported range");
        }
        requireUniqueIds(facts.stream().map(InferenceFact::id).toList(), "fact");
        requireUniqueIds(causes.stream().map(InferenceCauseCandidate::id).toList(), "cause");
        requireUniqueIds(checks.stream().map(InferenceCheckCandidate::id).toList(), "check");
        requireUniqueIds(
                sourceLocations.stream().map(SourceLocation::id).toList(), "source location");
        validateReferences(facts, causes, checks, sourceLocations);
        if (totalCharacters(facts, causes, checks, sourceLocations) > MAXIMUM_TOTAL_CHARACTERS) {
            throw new IllegalArgumentException("request exceeds the total character limit");
        }
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
            List<InferenceCheckCandidate> checks,
            List<SourceLocation> sourceLocations) {
        Set<String> factIds =
                facts.stream().map(InferenceFact::id).collect(java.util.stream.Collectors.toSet());
        Set<String> locations =
                sourceLocations.stream()
                        .map(SourceLocation::id)
                        .collect(java.util.stream.Collectors.toSet());
        facts.forEach(
                fact ->
                        requireSubset(
                                fact.sourceLocationIds(), locations, "unknown source location"));
        causes.forEach(cause -> requireSubset(cause.factIds(), factIds, "unknown fact"));
        checks.forEach(check -> requireSubset(check.factIds(), factIds, "unknown fact"));
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
            List<SourceLocation> locations) {
        long total = facts.stream().mapToLong(f -> f.id().length() + f.text().length()).sum();
        total +=
                causes.stream()
                        .mapToLong(
                                c ->
                                        c.id().length()
                                                + c.title().length()
                                                + c.explanation().length())
                        .sum();
        total += checks.stream().mapToLong(c -> c.id().length() + c.instruction().length()).sum();
        total +=
                locations.stream()
                        .mapToLong(
                                l ->
                                        l.id().length()
                                                + l.className().length()
                                                + l.methodName().length()
                                                + l.fileName().length())
                        .sum();
        if (total > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) total;
    }
}
