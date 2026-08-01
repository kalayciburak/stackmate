package io.github.kalayciburak.stackmate.benchmark.corpus;

import io.github.kalayciburak.stackmate.analysis.DiagnosticCategory;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable expected outcome and scenario binding for one controlled incident. */
public record GroundTruthCase(
        String caseId,
        BenchmarkDomain domain,
        DiagnosticCategory expectedCategory,
        String expectedRootType,
        List<ExpectedEvidenceFact> expectedEvidenceFacts,
        List<String> expectedCauseIds,
        List<String> expectedCheckIds,
        List<String> forbiddenClaims,
        String scenarioFactoryKey) {

    private static final Pattern STABLE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");

    public GroundTruthCase {
        caseId = stable(caseId, "caseId");
        domain = Objects.requireNonNull(domain, "domain");
        expectedCategory = Objects.requireNonNull(expectedCategory, "expectedCategory");
        expectedRootType = text(expectedRootType, "expectedRootType");
        expectedEvidenceFacts = List.copyOf(expectedEvidenceFacts);
        expectedCauseIds = stableList(expectedCauseIds, "expectedCauseIds");
        expectedCheckIds = stableList(expectedCheckIds, "expectedCheckIds");
        forbiddenClaims = List.copyOf(forbiddenClaims);
        scenarioFactoryKey = stable(scenarioFactoryKey, "scenarioFactoryKey");
        if (expectedEvidenceFacts.isEmpty()
                || expectedCauseIds.isEmpty()
                || expectedCheckIds.isEmpty()) {
            throw new IllegalArgumentException("ground truth expectations must not be empty");
        }
        if (expectedEvidenceFacts.stream().distinct().count() != expectedEvidenceFacts.size()
                || expectedCauseIds.stream().distinct().count() != expectedCauseIds.size()
                || expectedCheckIds.stream().distinct().count() != expectedCheckIds.size()) {
            throw new IllegalArgumentException("ground truth expectations must be unique");
        }
        if (forbiddenClaims.stream().anyMatch(claim -> claim == null || claim.isBlank())) {
            throw new IllegalArgumentException("forbidden claims must contain text");
        }
    }

    private static List<String> stableList(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        return values.stream().map(value -> stable(value, name)).toList();
    }

    private static String stable(String value, String name) {
        value = text(value, name);
        if (!STABLE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a stable lowercase identifier");
        }
        return value;
    }

    private static String text(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
