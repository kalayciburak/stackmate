package io.github.kalayciburak.stackmate.analysis;

import io.github.kalayciburak.stackmate.evidence.EvidenceItem;
import io.github.kalayciburak.stackmate.evidence.EvidenceReliability;
import io.github.kalayciburak.stackmate.evidence.EvidenceSensitivity;
import io.github.kalayciburak.stackmate.evidence.EvidenceType;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.incident.StackFrameSnapshot;
import io.github.kalayciburak.stackmate.throwable.ThrowableNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Runs analyzers deterministically and composes only internally consistent evidence-backed output.
 */
public final class DiagnosisAggregator {

    private static final String FALLBACK_EVIDENCE_ID = "diagnosis.fallback.no-match";
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,31}");
    private static final Comparator<DiagnosticAnalyzer> ANALYZER_ORDER =
            Comparator.comparingInt(DiagnosticAnalyzer::order)
                    .thenComparing(DiagnosticAnalyzer::id);

    private final List<DiagnosticAnalyzer> analyzers;

    public DiagnosisAggregator(Collection<? extends DiagnosticAnalyzer> analyzers) {
        Objects.requireNonNull(analyzers, "analyzers");
        this.analyzers =
                analyzers.stream().map(Objects::requireNonNull).sorted(ANALYZER_ORDER).toList();
        this.analyzers.forEach(DiagnosisAggregator::validateAnalyzerIdentity);
        if (this.analyzers.stream().map(DiagnosticAnalyzer::id).distinct().count()
                != this.analyzers.size()) {
            throw new IllegalArgumentException("analyzer IDs must be unique");
        }
    }

    public Diagnosis diagnose(IncidentSnapshot incident) {
        Objects.requireNonNull(incident, "incident");
        Map<String, EvidenceItem> evidenceById = new LinkedHashMap<>();
        Map<String, CauseCandidate> causesById = new LinkedHashMap<>();
        Map<String, CheckCandidate> checksById = new LinkedHashMap<>();
        List<String> analyzerTokens =
                analyzers.stream().map(DiagnosisAggregator::analyzerToken).toList();

        for (DiagnosticAnalyzer analyzer : analyzers) {
            AnalysisContribution contribution = safelyAnalyze(analyzer, incident);
            if (contribution == null || !isInternallyConsistent(contribution)) {
                continue;
            }
            mergeContribution(contribution, evidenceById, causesById, checksById);
        }

        if (causesById.isEmpty()) {
            return unknownDiagnosis(incident, evidenceById, checksById, analyzerTokens);
        }

        List<DiagnosedCause> diagnosedCauses =
                causesById.values().stream()
                        .map(candidate -> diagnoseCause(candidate, evidenceById))
                        .toList();
        return new Diagnosis(
                List.copyOf(evidenceById.values()),
                diagnosedCauses,
                List.copyOf(checksById.values()),
                analyzerTokens,
                false);
    }

    public List<DiagnosticAnalyzer> analyzers() {
        return analyzers;
    }

    private AnalysisContribution safelyAnalyze(
            DiagnosticAnalyzer analyzer, IncidentSnapshot incident) {
        try {
            AnalysisContribution contribution = analyzer.analyze(incident);
            if (contribution != null && analyzer.id().equals(contribution.analyzerId())) {
                return contribution;
            }
        } catch (RuntimeException ignored) {
            // An analyzer is advisory and must not replace the application's failure path.
        }
        return null;
    }

    private boolean isInternallyConsistent(AnalysisContribution contribution) {
        Map<String, EvidenceItem> localEvidence = new LinkedHashMap<>();
        for (EvidenceItem item : contribution.evidence()) {
            if (localEvidence.putIfAbsent(item.id(), item) != null) {
                return false;
            }
        }
        return contribution.causes().stream().map(CauseCandidate::id).distinct().count()
                        == contribution.causes().size()
                && contribution.checks().stream().map(CheckCandidate::id).distinct().count()
                        == contribution.checks().size()
                && contribution.causes().stream()
                        .allMatch(cause -> localEvidence.keySet().containsAll(cause.evidenceIds()))
                && contribution.checks().stream()
                        .allMatch(check -> localEvidence.keySet().containsAll(check.evidenceIds()));
    }

    private boolean mergeContribution(
            AnalysisContribution contribution,
            Map<String, EvidenceItem> evidenceById,
            Map<String, CauseCandidate> causesById,
            Map<String, CheckCandidate> checksById) {
        if (hasConflictingEvidence(contribution.evidence(), evidenceById)
                || hasConflictingCauses(contribution.causes(), causesById)
                || hasConflictingChecks(contribution.checks(), checksById)) {
            return false;
        }

        contribution.evidence().forEach(item -> evidenceById.putIfAbsent(item.id(), item));
        contribution
                .causes()
                .forEach(cause -> causesById.merge(cause.id(), cause, this::mergeCause));
        contribution
                .checks()
                .forEach(check -> checksById.merge(check.id(), check, this::mergeCheck));
        return !contribution.evidence().isEmpty()
                || !contribution.causes().isEmpty()
                || !contribution.checks().isEmpty();
    }

    private boolean hasConflictingEvidence(
            List<EvidenceItem> incoming, Map<String, EvidenceItem> existing) {
        return incoming.stream()
                .anyMatch(
                        item ->
                                existing.containsKey(item.id())
                                        && !existing.get(item.id()).equals(item));
    }

    private boolean hasConflictingCauses(
            List<CauseCandidate> incoming, Map<String, CauseCandidate> existing) {
        return incoming.stream()
                .anyMatch(
                        item ->
                                existing.containsKey(item.id())
                                        && !sameCause(existing.get(item.id()), item));
    }

    private boolean hasConflictingChecks(
            List<CheckCandidate> incoming, Map<String, CheckCandidate> existing) {
        return incoming.stream()
                .anyMatch(
                        item ->
                                existing.containsKey(item.id())
                                        && !existing.get(item.id())
                                                .instruction()
                                                .equals(item.instruction()));
    }

    private boolean sameCause(CauseCandidate first, CauseCandidate second) {
        return first.category() == second.category()
                && first.title().equals(second.title())
                && first.explanation().equals(second.explanation());
    }

    private CauseCandidate mergeCause(CauseCandidate first, CauseCandidate second) {
        return new CauseCandidate(
                first.id(),
                first.category(),
                first.title(),
                first.explanation(),
                union(first.evidenceIds(), second.evidenceIds()));
    }

    private CheckCandidate mergeCheck(CheckCandidate first, CheckCandidate second) {
        return new CheckCandidate(
                first.id(), first.instruction(), union(first.evidenceIds(), second.evidenceIds()));
    }

    private List<String> union(List<String> first, List<String> second) {
        LinkedHashSet<String> values = new LinkedHashSet<>(first);
        values.addAll(second);
        return List.copyOf(values);
    }

    private DiagnosedCause diagnoseCause(
            CauseCandidate candidate, Map<String, EvidenceItem> evidenceById) {
        List<EvidenceItem> supportingEvidence =
                candidate.evidenceIds().stream().map(evidenceById::get).toList();
        return new DiagnosedCause(
                candidate.id(),
                candidate.category(),
                candidate.title(),
                candidate.explanation(),
                EvidenceStrengthCalculator.calculate(supportingEvidence),
                candidate.evidenceIds());
    }

    private Diagnosis unknownDiagnosis(
            IncidentSnapshot incident,
            Map<String, EvidenceItem> evidenceById,
            Map<String, CheckCandidate> checksById,
            List<String> analyzerTokens) {
        List<String> fallbackEvidenceIds = new ArrayList<>();
        EvidenceItem fallbackEvidence =
                new EvidenceItem(
                        FALLBACK_EVIDENCE_ID,
                        EvidenceType.ANALYZER_RESULT,
                        "diagnosis.aggregator",
                        "insufficient-context-for-specific-diagnosis",
                        EvidenceSensitivity.PUBLIC,
                        EvidenceReliability.HIGH);
        evidenceById.put(FALLBACK_EVIDENCE_ID, fallbackEvidence);
        fallbackEvidenceIds.add(FALLBACK_EVIDENCE_ID);
        addRootEvidence(incident, evidenceById, fallbackEvidenceIds);
        addApplicationLocationEvidence(incident, evidenceById, fallbackEvidenceIds);
        addCauseRelationshipEvidence(incident, evidenceById, fallbackEvidenceIds);
        CheckCandidate fallbackCheck =
                new CheckCandidate(
                        "check.inspect-root-cause",
                        "Inspect the deepest captured cause and its application frames.",
                        List.copyOf(fallbackEvidenceIds));
        checksById.putIfAbsent(fallbackCheck.id(), fallbackCheck);
        DiagnosedCause unknownCause =
                new DiagnosedCause(
                        "cause.unknown",
                        DiagnosticCategory.UNKNOWN,
                        "No supported deterministic pattern matched",
                        "The captured root type, sanitized message, cause chain, and application location are insufficient for a more specific supported cause.",
                        DiagnosticStrength.UNKNOWN,
                        List.copyOf(fallbackEvidenceIds));
        return new Diagnosis(
                List.copyOf(evidenceById.values()),
                List.of(unknownCause),
                List.copyOf(checksById.values()),
                List.copyOf(analyzerTokens),
                true);
    }

    private void addRootEvidence(
            IncidentSnapshot incident,
            Map<String, EvidenceItem> evidenceById,
            List<String> fallbackEvidenceIds) {
        ThrowableNode root = incident.throwable().root();
        if (!root.type().isBlank()) {
            addFallbackEvidence(
                    evidenceById,
                    fallbackEvidenceIds,
                    new EvidenceItem(
                            "diagnosis.fallback.root-type",
                            EvidenceType.EXCEPTION_CLASS,
                            "throwable.root.type",
                            root.type(),
                            EvidenceSensitivity.PUBLIC,
                            EvidenceReliability.HIGH));
        }
        if (!root.message().isBlank()) {
            addFallbackEvidence(
                    evidenceById,
                    fallbackEvidenceIds,
                    new EvidenceItem(
                            "diagnosis.fallback.root-message",
                            EvidenceType.SANITIZED_MESSAGE,
                            "throwable.root.message",
                            root.message(),
                            EvidenceSensitivity.POTENTIALLY_SENSITIVE,
                            EvidenceReliability.HIGH));
        }
    }

    private void addApplicationLocationEvidence(
            IncidentSnapshot incident,
            Map<String, EvidenceItem> evidenceById,
            List<String> fallbackEvidenceIds) {
        if (incident.applicationFrames().isEmpty()) {
            return;
        }
        StackFrameSnapshot frame = incident.applicationFrames().getFirst();
        addFallbackEvidence(
                evidenceById,
                fallbackEvidenceIds,
                new EvidenceItem(
                        "diagnosis.fallback.application-frame",
                        EvidenceType.APPLICATION_FRAME,
                        "incident.applicationFrames[0]",
                        renderFrame(frame),
                        EvidenceSensitivity.POTENTIALLY_SENSITIVE,
                        EvidenceReliability.HIGH));
    }

    private void addCauseRelationshipEvidence(
            IncidentSnapshot incident,
            Map<String, EvidenceItem> evidenceById,
            List<String> fallbackEvidenceIds) {
        List<ThrowableNode> chain = incident.throwable().causeChain();
        for (int index = 0; index + 1 < chain.size(); index++) {
            ThrowableNode outer = chain.get(index);
            ThrowableNode cause = chain.get(index + 1);
            addFallbackEvidence(
                    evidenceById,
                    fallbackEvidenceIds,
                    new EvidenceItem(
                            "diagnosis.fallback.cause." + index,
                            EvidenceType.CAUSE_RELATIONSHIP,
                            "throwable.causeChain[" + index + "]",
                            outer.type() + " -> " + cause.type(),
                            EvidenceSensitivity.PUBLIC,
                            EvidenceReliability.HIGH));
        }
    }

    private void addFallbackEvidence(
            Map<String, EvidenceItem> evidenceById,
            List<String> fallbackEvidenceIds,
            EvidenceItem item) {
        evidenceById.put(item.id(), item);
        fallbackEvidenceIds.add(item.id());
    }

    private String renderFrame(StackFrameSnapshot frame) {
        StringBuilder value = new StringBuilder();
        if (!frame.className().isBlank()) {
            value.append(frame.className());
        }
        if (!frame.methodName().isBlank()) {
            if (!value.isEmpty()) {
                value.append('#');
            }
            value.append(frame.methodName());
        }
        if (!frame.fileName().isBlank()) {
            value.append('(').append(frame.fileName());
            if (frame.lineNumber() >= 0) {
                value.append(':').append(frame.lineNumber());
            }
            value.append(')');
        }
        return value.isEmpty() ? "application-frame-present" : value.toString();
    }

    private static void validateAnalyzerIdentity(DiagnosticAnalyzer analyzer) {
        new AnalysisContribution(analyzer.id(), List.of(), List.of(), List.of());
        Objects.requireNonNull(analyzer.version(), "analyzer version");
        if (!VERSION.matcher(analyzer.version()).matches()) {
            throw new IllegalArgumentException("analyzer version must be a stable token");
        }
    }

    private static String analyzerToken(DiagnosticAnalyzer analyzer) {
        return analyzer.id() + "@" + analyzer.version();
    }
}
