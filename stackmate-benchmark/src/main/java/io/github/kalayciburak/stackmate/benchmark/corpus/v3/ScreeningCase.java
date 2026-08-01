package io.github.kalayciburak.stackmate.benchmark.corpus.v3;

import io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2Case;
import io.github.kalayciburak.stackmate.benchmark.corpus.v2.ExpectedFactV2;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** One independently frozen English model-screening purpose backed by a V2 recipe. */
public record ScreeningCase(
        String caseId,
        String sourceCaseId,
        Purpose purpose,
        CorpusV2Case.Provenance provenance,
        Split split,
        boolean adversarial,
        CorpusV2Case.Disposition expectedDisposition,
        List<ExpectedFactV2> expectedFacts,
        int expectedCauseSelections,
        int expectedCheckSelections,
        List<String> forbiddenClaims,
        String manualReviewFocus) {

    private static final Pattern CASE_ID = Pattern.compile("V3-[A-Z0-9-]{4,60}");
    private static final Pattern SOURCE_ID = Pattern.compile("[A-Z][A-Z0-9-]{4,63}");

    public ScreeningCase {
        if (!CASE_ID.matcher(Objects.requireNonNull(caseId, "caseId")).matches()) {
            throw new IllegalArgumentException("caseId is not stable: " + caseId);
        }
        if (!SOURCE_ID.matcher(Objects.requireNonNull(sourceCaseId, "sourceCaseId")).matches()) {
            throw new IllegalArgumentException("sourceCaseId is not stable: " + sourceCaseId);
        }
        purpose = Objects.requireNonNull(purpose, "purpose");
        provenance = Objects.requireNonNull(provenance, "provenance");
        split = Objects.requireNonNull(split, "split");
        expectedDisposition = Objects.requireNonNull(expectedDisposition, "expectedDisposition");
        expectedFacts = List.copyOf(Objects.requireNonNull(expectedFacts, "expectedFacts"));
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

    public enum Split {
        DEVELOPMENT,
        HOLDOUT
    }

    public enum Purpose {
        DATABASE_UNIQUE_CONSTRAINT,
        DATABASE_MISSING_COLUMN,
        DATABASE_FOREIGN_KEY,
        CONFIGURATION_BINDING,
        MISSING_CONFIGURATION,
        BEAN_DEPENDENCY,
        VALIDATION,
        MALFORMED_JSON,
        CONNECTION_FAILURE,
        TIMEOUT,
        UNKNOWN_EXCEPTION,
        PROMPT_INJECTION,
        SECRET_REDACTION,
        WRAPPER_EXCEPTION,
        VOLATILE_IDS,
        AMBIGUOUS_EVIDENCE,
        INSUFFICIENT_EVIDENCE,
        DUPLICATE_FINGERPRINT,
        UNSUPPORTED_CLAIM_TRAP,
        FABRICATED_SOURCE_TRAP
    }
}
