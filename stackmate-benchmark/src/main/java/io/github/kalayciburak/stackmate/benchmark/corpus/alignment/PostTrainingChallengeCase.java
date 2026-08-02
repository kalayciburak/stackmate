package io.github.kalayciburak.stackmate.benchmark.corpus.alignment;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** One independently authored case in the frozen post-training challenge corpus. */
public record PostTrainingChallengeCase(
        String caseId,
        Provenance provenance,
        String recipe,
        String category,
        String expectedRootType,
        Disposition expectedDisposition,
        int expectedCauseSelections,
        int expectedCheckSelections,
        ChallengeClass challengeClass,
        List<String> forbiddenClaims) {

    private static final Pattern CASE_ID = Pattern.compile("CH-[A-Z0-9-]{6,61}");
    private static final Pattern RECIPE = Pattern.compile("challenge\\.[a-z][a-z0-9.-]{4,119}");

    public PostTrainingChallengeCase {
        if (!CASE_ID.matcher(Objects.requireNonNull(caseId, "caseId")).matches()) {
            throw new IllegalArgumentException("caseId is not a challenge ID: " + caseId);
        }
        provenance = Objects.requireNonNull(provenance, "provenance");
        if (!RECIPE.matcher(Objects.requireNonNull(recipe, "recipe")).matches()) {
            throw new IllegalArgumentException("recipe is not stable: " + recipe);
        }
        category = text(category, "category");
        expectedRootType = text(expectedRootType, "expectedRootType");
        expectedDisposition = Objects.requireNonNull(expectedDisposition, "expectedDisposition");
        challengeClass = Objects.requireNonNull(challengeClass, "challengeClass");
        forbiddenClaims = List.copyOf(Objects.requireNonNull(forbiddenClaims, "forbiddenClaims"));
        if (expectedCauseSelections < 0 || expectedCheckSelections < 0) {
            throw new IllegalArgumentException("selection counts must not be negative");
        }
        boolean restrain = expectedDisposition == Disposition.RESTRAIN;
        if (restrain != (expectedCauseSelections == 0 && expectedCheckSelections == 0)) {
            throw new IllegalArgumentException(
                    "restraint and empty selections must agree: " + caseId);
        }
        if (forbiddenClaims.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("forbiddenClaims must contain bounded text");
        }
    }

    public boolean realExecution() {
        return provenance == Provenance.REAL_RUNTIME || provenance == Provenance.REAL_INTEGRATION;
    }

    private static String text(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public enum Provenance {
        REAL_RUNTIME,
        REAL_INTEGRATION,
        SYNTHETIC_HARD_NEGATIVE,
        SYNTHETIC_ADVERSARIAL,
        SYNTHETIC_INSUFFICIENT
    }

    public enum Disposition {
        DIAGNOSE,
        RESTRAIN
    }

    public enum ChallengeClass {
        NORMAL,
        HARD_NEGATIVE,
        ADVERSARIAL_OR_INSUFFICIENT
    }
}
