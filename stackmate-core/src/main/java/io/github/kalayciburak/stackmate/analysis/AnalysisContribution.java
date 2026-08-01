package io.github.kalayciburak.stackmate.analysis;

import io.github.kalayciburak.stackmate.evidence.EvidenceItem;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Evidence and bounded candidates produced by one stateless analyzer invocation. */
public record AnalysisContribution(
        String analyzerId,
        List<EvidenceItem> evidence,
        List<CauseCandidate> causes,
        List<CheckCandidate> checks) {

    private static final Pattern STABLE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");

    public static AnalysisContribution empty(String analyzerId) {
        return new AnalysisContribution(analyzerId, List.of(), List.of(), List.of());
    }

    public AnalysisContribution {
        analyzerId = requireStableId(analyzerId);
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        causes = List.copyOf(Objects.requireNonNull(causes, "causes"));
        checks = List.copyOf(Objects.requireNonNull(checks, "checks"));
        if (evidence.stream().map(EvidenceItem::id).distinct().count() != evidence.size()) {
            throw new IllegalArgumentException("evidence IDs must be unique within a contribution");
        }
        if (causes.stream().map(CauseCandidate::id).distinct().count() != causes.size()) {
            throw new IllegalArgumentException("cause IDs must be unique within a contribution");
        }
        if (checks.stream().map(CheckCandidate::id).distinct().count() != checks.size()) {
            throw new IllegalArgumentException("check IDs must be unique within a contribution");
        }
        List<String> availableEvidenceIds = evidence.stream().map(EvidenceItem::id).toList();
        if (causes.stream()
                        .anyMatch(cause -> !availableEvidenceIds.containsAll(cause.evidenceIds()))
                || checks.stream()
                        .anyMatch(
                                check -> !availableEvidenceIds.containsAll(check.evidenceIds()))) {
            throw new IllegalArgumentException(
                    "candidate evidence IDs must resolve within the contribution");
        }
    }

    private static String requireStableId(String value) {
        Objects.requireNonNull(value, "analyzerId");
        if (!STABLE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("analyzerId must be a stable lowercase identifier");
        }
        return value;
    }
}
