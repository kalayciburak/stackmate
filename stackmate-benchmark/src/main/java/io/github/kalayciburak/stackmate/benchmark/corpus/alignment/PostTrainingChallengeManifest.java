package io.github.kalayciburak.stackmate.benchmark.corpus.alignment;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Frozen post-training challenge manifest that is inaccessible to checkpoint selection. */
public record PostTrainingChallengeManifest(
        String schemaVersion,
        String corpusId,
        String frozenAt,
        Map<String, String> frameworks,
        List<PostTrainingChallengeCase> cases) {

    public static final String RESOURCE = "/corpus-v4-post-training-challenge/manifest.json";

    public PostTrainingChallengeManifest {
        schemaVersion = exact(schemaVersion, "1", "schemaVersion");
        corpusId = exact(corpusId, "stackmate-phase-5-2-post-training-challenge", "corpusId");
        frozenAt = text(frozenAt, "frozenAt");
        frameworks = Map.copyOf(Objects.requireNonNull(frameworks, "frameworks"));
        cases = List.copyOf(Objects.requireNonNull(cases, "cases"));
        if (cases.size() != 30) {
            throw new IllegalArgumentException("challenge corpus must contain exactly 30 cases");
        }
        HashSet<String> ids = new HashSet<>();
        for (PostTrainingChallengeCase benchmarkCase : cases) {
            if (!ids.add(benchmarkCase.caseId())) {
                throw new IllegalArgumentException(
                        "duplicate challenge case ID: " + benchmarkCase.caseId());
            }
        }
        requireCount(
                cases.stream().filter(PostTrainingChallengeCase::realExecution).count(),
                20,
                "real runtime/integration");
        requireCount(
                cases.stream()
                        .filter(
                                item ->
                                        item.challengeClass()
                                                == PostTrainingChallengeCase.ChallengeClass
                                                        .HARD_NEGATIVE)
                        .count(),
                5,
                "hard-negative");
        requireCount(
                cases.stream()
                        .filter(
                                item ->
                                        item.challengeClass()
                                                == PostTrainingChallengeCase.ChallengeClass
                                                        .ADVERSARIAL_OR_INSUFFICIENT)
                        .count(),
                5,
                "adversarial/insufficient");
    }

    /** Loads the immutable challenge manifest with strict JSON parsing. */
    public static PostTrainingChallengeManifest load() {
        ObjectMapper mapper =
                new ObjectMapper()
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        try (InputStream input =
                PostTrainingChallengeManifest.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing challenge manifest: " + RESOURCE);
            }
            return mapper.readValue(input, PostTrainingChallengeManifest.class);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read challenge manifest", failure);
        }
    }

    private static void requireCount(long actual, long expected, String category) {
        if (actual != expected) {
            throw new IllegalArgumentException(
                    category + " challenge count must be " + expected + ", not " + actual);
        }
    }

    private static String exact(String value, String expected, String field) {
        if (!expected.equals(Objects.requireNonNull(value, field))) {
            throw new IllegalArgumentException(field + " must be " + expected);
        }
        return value;
    }

    private static String text(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
