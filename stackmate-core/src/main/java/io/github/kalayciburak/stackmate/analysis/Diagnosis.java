package io.github.kalayciburak.stackmate.analysis;

import io.github.kalayciburak.stackmate.evidence.EvidenceItem;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Complete deterministic diagnosis suitable for inference or rendering boundaries.
 *
 * @param evidence immutable evidence available to consumers
 * @param causes ranked or composed causes supported by the evidence
 * @param checks safe follow-up checks supported by the evidence
 * @param analyzerTokens stable analyzer identifiers paired with their versions
 * @param fallback whether the diagnosis was produced by the fallback path
 */
public record Diagnosis(
        List<EvidenceItem> evidence,
        List<DiagnosedCause> causes,
        List<CheckCandidate> checks,
        List<String> analyzerTokens,
        boolean fallback) {

    private static final Pattern ANALYZER_TOKEN =
            Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}@[A-Za-z0-9][A-Za-z0-9._-]{0,31}");

    /**
     * Validates and creates a complete diagnosis.
     *
     * @param evidence immutable evidence available to consumers
     * @param causes ranked or composed causes supported by the evidence
     * @param checks safe follow-up checks supported by the evidence
     * @param analyzerTokens stable analyzer identifiers paired with their versions
     * @param fallback whether the diagnosis was produced by the fallback path
     */
    public Diagnosis {
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        causes = List.copyOf(Objects.requireNonNull(causes, "causes"));
        checks = List.copyOf(Objects.requireNonNull(checks, "checks"));
        analyzerTokens = List.copyOf(Objects.requireNonNull(analyzerTokens, "analyzerTokens"));
        if (causes.isEmpty()) {
            throw new IllegalArgumentException("causes must not be empty");
        }
        if (evidence.stream().map(EvidenceItem::id).distinct().count() != evidence.size()) {
            throw new IllegalArgumentException("evidence IDs must be unique");
        }
        if (causes.stream().map(DiagnosedCause::id).distinct().count() != causes.size()) {
            throw new IllegalArgumentException("cause IDs must be unique");
        }
        if (checks.stream().map(CheckCandidate::id).distinct().count() != checks.size()) {
            throw new IllegalArgumentException("check IDs must be unique");
        }
        List<String> evidenceIds = evidence.stream().map(EvidenceItem::id).toList();
        if (causes.stream().anyMatch(cause -> !evidenceIds.containsAll(cause.evidenceIds()))
                || checks.stream()
                        .anyMatch(check -> !evidenceIds.containsAll(check.evidenceIds()))) {
            throw new IllegalArgumentException("all candidate evidence IDs must resolve");
        }
        if (analyzerTokens.stream().anyMatch(token -> !ANALYZER_TOKEN.matcher(token).matches())
                || analyzerTokens.stream().distinct().count() != analyzerTokens.size()) {
            throw new IllegalArgumentException("analyzer tokens must be stable and unique");
        }
    }
}
