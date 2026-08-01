package io.github.kalayciburak.stackmate.benchmark.corpus.qualification;

import io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2Case;
import io.github.kalayciburak.stackmate.benchmark.corpus.v2.ExpectedFactV2;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** One independently authored Phase 5 blind qualification case. */
public record QualificationBlindCase(
        String caseId,
        CorpusV2Case.Domain domain,
        CorpusV2Case.Provenance provenance,
        String recipe,
        String expectedCategory,
        String expectedRootType,
        List<ExpectedFactV2> expectedFacts,
        CorpusV2Case.Disposition expectedDisposition,
        int expectedCauseSelections,
        int expectedCheckSelections,
        List<String> forbiddenClaims,
        String manualReviewFocus) {

    private static final Pattern CASE_ID = Pattern.compile("B5-[A-Z0-9-]{4,60}");
    private static final Pattern RECIPE = Pattern.compile("blind\\.[a-z][a-z0-9.-]{2,120}");

    public QualificationBlindCase {
        if (!CASE_ID.matcher(Objects.requireNonNull(caseId, "caseId")).matches()) {
            throw new IllegalArgumentException("caseId is not stable: " + caseId);
        }
        domain = Objects.requireNonNull(domain, "domain");
        provenance = Objects.requireNonNull(provenance, "provenance");
        if (!RECIPE.matcher(Objects.requireNonNull(recipe, "recipe")).matches()) {
            throw new IllegalArgumentException("recipe is not stable: " + recipe);
        }
        expectedCategory = text(expectedCategory, "expectedCategory");
        expectedRootType = text(expectedRootType, "expectedRootType");
        expectedFacts = List.copyOf(Objects.requireNonNull(expectedFacts, "expectedFacts"));
        expectedDisposition = Objects.requireNonNull(expectedDisposition, "expectedDisposition");
        forbiddenClaims = List.copyOf(Objects.requireNonNull(forbiddenClaims, "forbiddenClaims"));
        manualReviewFocus = text(manualReviewFocus, "manualReviewFocus");
        if (expectedCauseSelections < 0 || expectedCheckSelections < 0) {
            throw new IllegalArgumentException("expected selection counts must not be negative");
        }
        if (expectedDisposition == CorpusV2Case.Disposition.RESTRAIN
                && (expectedCauseSelections != 0 || expectedCheckSelections != 0)) {
            throw new IllegalArgumentException("restraint cases must not select causes or checks");
        }
    }

    public boolean realExecution() {
        return provenance == CorpusV2Case.Provenance.REAL_RUNTIME
                || provenance == CorpusV2Case.Provenance.REAL_INTEGRATION;
    }

    private static String text(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
