package io.github.kalayciburak.stackmate.analysis;

import io.github.kalayciburak.stackmate.evidence.EvidenceItem;
import io.github.kalayciburak.stackmate.evidence.EvidenceReliability;
import io.github.kalayciburak.stackmate.evidence.EvidenceSensitivity;
import io.github.kalayciburak.stackmate.evidence.EvidenceType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class ContributionBuilder {

    private final String analyzerId;
    private final Map<String, EvidenceItem> evidence = new LinkedHashMap<>();
    private final Map<String, CauseCandidate> causes = new LinkedHashMap<>();
    private final Map<String, CheckCandidate> checks = new LinkedHashMap<>();

    ContributionBuilder(String analyzerId) {
        this.analyzerId = analyzerId;
    }

    String addClassEvidence(String prefix, AnalyzerSupport.NodeFact fact) {
        return addEvidence(
                prefix + ".class." + fact.ordinal(),
                EvidenceType.EXCEPTION_CLASS,
                fact.path() + ".type",
                fact.node().type(),
                EvidenceSensitivity.PUBLIC,
                EvidenceReliability.HIGH);
    }

    String addWrapperEvidence(String prefix, AnalyzerSupport.NodeFact fact) {
        return addEvidence(
                prefix + ".wrapper." + fact.ordinal(),
                EvidenceType.WRAPPER_CLASS,
                fact.path() + ".type",
                fact.node().type(),
                EvidenceSensitivity.PUBLIC,
                EvidenceReliability.HIGH);
    }

    String addMessagePatternEvidence(
            String prefix, AnalyzerSupport.NodeFact fact, String patternId) {
        return addEvidence(
                prefix + ".pattern." + patternId + "." + fact.ordinal(),
                EvidenceType.MESSAGE_PATTERN,
                fact.path() + ".message",
                patternId,
                EvidenceSensitivity.PUBLIC,
                EvidenceReliability.MEDIUM);
    }

    String addEvidence(
            String id,
            EvidenceType type,
            String source,
            String value,
            EvidenceSensitivity sensitivity,
            EvidenceReliability reliability) {
        EvidenceItem item = new EvidenceItem(id, type, source, value, sensitivity, reliability);
        EvidenceItem existing = evidence.putIfAbsent(id, item);
        if (existing != null && !existing.equals(item)) {
            throw new IllegalArgumentException("conflicting evidence ID: " + id);
        }
        return id;
    }

    void addCause(
            String id,
            DiagnosticCategory category,
            String title,
            String explanation,
            String... evidenceIds) {
        CauseCandidate incoming =
                new CauseCandidate(id, category, title, explanation, List.of(evidenceIds));
        causes.merge(id, incoming, ContributionBuilder::mergeCause);
    }

    void addCheck(String id, String instruction, String... evidenceIds) {
        CheckCandidate incoming = new CheckCandidate(id, instruction, List.of(evidenceIds));
        checks.merge(id, incoming, ContributionBuilder::mergeCheck);
    }

    AnalysisContribution build() {
        return new AnalysisContribution(
                analyzerId,
                new ArrayList<>(evidence.values()),
                new ArrayList<>(causes.values()),
                new ArrayList<>(checks.values()));
    }

    private static CauseCandidate mergeCause(CauseCandidate first, CauseCandidate second) {
        if (first.category() != second.category()
                || !first.title().equals(second.title())
                || !first.explanation().equals(second.explanation())) {
            throw new IllegalArgumentException("conflicting cause ID: " + first.id());
        }
        return new CauseCandidate(
                first.id(),
                first.category(),
                first.title(),
                first.explanation(),
                union(first.evidenceIds(), second.evidenceIds()));
    }

    private static CheckCandidate mergeCheck(CheckCandidate first, CheckCandidate second) {
        if (!first.instruction().equals(second.instruction())) {
            throw new IllegalArgumentException("conflicting check ID: " + first.id());
        }
        return new CheckCandidate(
                first.id(), first.instruction(), union(first.evidenceIds(), second.evidenceIds()));
    }

    private static List<String> union(List<String> first, List<String> second) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(first);
        ids.addAll(second);
        return List.copyOf(ids);
    }
}
