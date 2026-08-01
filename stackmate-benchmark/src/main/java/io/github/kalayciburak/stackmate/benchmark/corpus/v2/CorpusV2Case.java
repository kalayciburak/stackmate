package io.github.kalayciburak.stackmate.benchmark.corpus.v2;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** One independent expected outcome and executable recipe binding in corpus V2. */
public record CorpusV2Case(
        String caseId,
        Domain domain,
        Provenance provenance,
        String recipe,
        String category,
        String expectedRootType,
        List<ExpectedFactV2> expectedFacts,
        Disposition expectedDisposition,
        List<String> expectedCauseKeys,
        List<String> expectedCheckKeys,
        List<String> forbiddenClaims,
        Split split) {

    private static final Pattern CASE_ID = Pattern.compile("[A-Z][A-Z0-9-]{4,63}");
    private static final Pattern RECIPE = Pattern.compile("[a-z][a-z0-9.-]{2,127}");

    public CorpusV2Case {
        if (!CASE_ID.matcher(Objects.requireNonNull(caseId, "caseId")).matches()) {
            throw new IllegalArgumentException("caseId is not stable: " + caseId);
        }
        domain = Objects.requireNonNull(domain, "domain");
        provenance = Objects.requireNonNull(provenance, "provenance");
        if (!RECIPE.matcher(Objects.requireNonNull(recipe, "recipe")).matches()) {
            throw new IllegalArgumentException("recipe is not stable: " + recipe);
        }
        category = text(category, "category");
        expectedRootType = text(expectedRootType, "expectedRootType");
        expectedFacts = List.copyOf(Objects.requireNonNull(expectedFacts, "expectedFacts"));
        expectedDisposition = Objects.requireNonNull(expectedDisposition, "expectedDisposition");
        expectedCauseKeys = List.copyOf(expectedCauseKeys);
        expectedCheckKeys = List.copyOf(expectedCheckKeys);
        forbiddenClaims = List.copyOf(forbiddenClaims);
        split = Objects.requireNonNull(split, "split");
        if (expectedDisposition == Disposition.RESTRAIN
                && (!expectedCauseKeys.isEmpty() || !expectedCheckKeys.isEmpty())) {
            throw new IllegalArgumentException("restraint cases must not require a cause or check");
        }
    }

    public boolean realExecution() {
        return provenance == Provenance.REAL_RUNTIME || provenance == Provenance.REAL_INTEGRATION;
    }

    private static String text(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public enum Domain {
        GENERAL_JAVA,
        SPRING_CONFIGURATION,
        MVC,
        DATABASE,
        SECURITY,
        NETWORK,
        CONCURRENCY,
        ADVERSARIAL
    }

    public enum Provenance {
        REAL_RUNTIME,
        REAL_INTEGRATION,
        SYNTHETIC_WRAPPER,
        SYNTHETIC_EDGE_CASE
    }

    public enum Disposition {
        DIAGNOSE,
        RESTRAIN
    }

    public enum Split {
        DEVELOPMENT,
        HOLDOUT
    }
}
