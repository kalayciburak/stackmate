package io.github.kalayciburak.stackmate.analysis;

import io.github.kalayciburak.stackmate.evidence.EvidenceItem;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Evidence and bounded candidates produced by one stateless analyzer invocation.
 *
 * @param analyzerId stable identifier of the contributing analyzer
 * @param evidence facts observed by the analyzer
 * @param causes possible causes supported by {@code evidence}
 * @param checks safe follow-up checks supported by {@code evidence}
 */
public record AnalysisContribution(
        String analyzerId,
        List<EvidenceItem> evidence,
        List<CauseCandidate> causes,
        List<CheckCandidate> checks) {

    private static final Pattern STABLE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final int MAX_EVIDENCE = 128;
    private static final int MAX_CAUSES = 64;
    private static final int MAX_CHECKS = 128;

    /**
     * Creates a contribution with no evidence, causes, or checks.
     *
     * @param analyzerId stable identifier of the contributing analyzer
     * @return an empty contribution for the analyzer
     */
    public static AnalysisContribution empty(String analyzerId) {
        return new AnalysisContribution(analyzerId, List.of(), List.of(), List.of());
    }

    /**
     * Validates and creates an analyzer contribution.
     *
     * @param analyzerId stable identifier of the contributing analyzer
     * @param evidence facts observed by the analyzer
     * @param causes possible causes supported by {@code evidence}
     * @param checks safe follow-up checks supported by {@code evidence}
     */
    public AnalysisContribution {
        analyzerId = requireStableId(analyzerId);
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        causes = List.copyOf(Objects.requireNonNull(causes, "causes"));
        checks = List.copyOf(Objects.requireNonNull(checks, "checks"));
        if (evidence.size() > MAX_EVIDENCE
                || causes.size() > MAX_CAUSES
                || checks.size() > MAX_CHECKS) {
            throw new IllegalArgumentException("analysis contribution exceeds bounded item counts");
        }
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
