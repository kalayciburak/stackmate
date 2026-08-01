package io.github.kalayciburak.stackmate.benchmark.evaluation;

import io.github.kalayciburak.stackmate.analysis.DefaultDiagnosticAnalyzers;
import io.github.kalayciburak.stackmate.analysis.DeterministicDiagnosticEngine;
import io.github.kalayciburak.stackmate.analysis.DeterministicResult;
import io.github.kalayciburak.stackmate.analysis.Diagnosis;
import io.github.kalayciburak.stackmate.benchmark.corpus.BenchmarkCorpus;
import io.github.kalayciburak.stackmate.benchmark.corpus.BenchmarkScenarioFactory;
import io.github.kalayciburak.stackmate.benchmark.corpus.ExpectedEvidenceFact;
import io.github.kalayciburak.stackmate.benchmark.corpus.GroundTruthCase;
import io.github.kalayciburak.stackmate.evidence.EvidenceItem;
import io.github.kalayciburak.stackmate.fingerprint.IncidentFingerprinter;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runs Mode C without a model, network, random input, or wall-clock performance measurement. */
public final class DeterministicEvaluationHarness {

    private static final Pattern THROWABLE_SOURCE =
            Pattern.compile(
                    "throwable\\.causeChain\\[(\\d+)]\\.(type|message|sql\\.sqlState|sql\\.vendorCode)");
    private static final Pattern APPLICATION_FRAME_SOURCE =
            Pattern.compile("incident\\.applicationFrames\\[(\\d+)]");

    private final DeterministicDiagnosticEngine engine;

    public DeterministicEvaluationHarness() {
        this(
                new DeterministicDiagnosticEngine(
                        DefaultDiagnosticAnalyzers.aggregator(), new IncidentFingerprinter()));
    }

    public DeterministicEvaluationHarness(DeterministicDiagnosticEngine engine) {
        this.engine = engine;
    }

    public EvaluationSummary evaluate() {
        List<CaseEvaluation> cases =
                BenchmarkCorpus.cases().stream().map(this::evaluateCase).toList();
        int expectedFacts = cases.stream().mapToInt(CaseEvaluation::expectedFactCount).sum();
        int actualFacts = cases.stream().mapToInt(CaseEvaluation::actualFactCount).sum();
        int matchedFacts = cases.stream().mapToInt(CaseEvaluation::matchedFactCount).sum();
        return new EvaluationSummary(
                cases.size(),
                score(cases, CaseEvaluation::categoryMatched),
                score(cases, CaseEvaluation::rootTypeMatched),
                score(cases, CaseEvaluation::factsExactlyMatched),
                new MetricScore(matchedFacts, actualFacts),
                new MetricScore(matchedFacts, expectedFacts),
                score(cases, CaseEvaluation::causesExactlyMatched),
                score(cases, CaseEvaluation::checksExactlyMatched),
                sum(cases, CaseEvaluation::fabricatedSourceReferenceCount),
                sum(cases, CaseEvaluation::unknownIdCount),
                sum(cases, CaseEvaluation::forbiddenClaimCount),
                sum(cases, CaseEvaluation::secretLeakageCount),
                sum(cases, CaseEvaluation::promptInjectionFailureCount),
                sum(cases, CaseEvaluation::inputCharacters),
                sum(cases, CaseEvaluation::outputCharacters),
                cases);
    }

    private CaseEvaluation evaluateCase(GroundTruthCase groundTruth) {
        IncidentSnapshot incident =
                BenchmarkScenarioFactory.create(
                        groundTruth.scenarioFactoryKey(), groundTruth.caseId());
        DeterministicResult result = engine.analyze(incident);
        Diagnosis diagnosis = result.diagnosis();
        Set<ExpectedEvidenceFact> expectedFacts =
                new HashSet<>(groundTruth.expectedEvidenceFacts());
        Set<ExpectedEvidenceFact> actualFacts = new HashSet<>();
        diagnosis.evidence().stream()
                .map(item -> new ExpectedEvidenceFact(item.type(), item.value()))
                .forEach(actualFacts::add);
        int matchedFacts = intersectionSize(expectedFacts, actualFacts);
        List<String> actualCauseIds = diagnosis.causes().stream().map(cause -> cause.id()).toList();
        List<String> actualCheckIds = diagnosis.checks().stream().map(check -> check.id()).toList();
        String output = renderOutput(result);
        String input = renderInput(incident);
        int unknownIds =
                unknownSelectionCount(actualCauseIds, groundTruth.expectedCauseIds())
                        + unknownSelectionCount(actualCheckIds, groundTruth.expectedCheckIds())
                        + danglingEvidenceReferenceCount(diagnosis);
        return new CaseEvaluation(
                groundTruth.caseId(),
                groundTruth.domain(),
                groundTruth.expectedCategory(),
                diagnosis.causes().getFirst().category(),
                diagnosis.causes().getFirst().category() == groundTruth.expectedCategory(),
                groundTruth.expectedRootType(),
                incident.throwable().root().type(),
                incident.throwable().root().type().equals(groundTruth.expectedRootType()),
                expectedFacts.size(),
                actualFacts.size(),
                matchedFacts,
                expectedFacts.equals(actualFacts),
                groundTruth.expectedCauseIds(),
                actualCauseIds,
                sameSet(actualCauseIds, groundTruth.expectedCauseIds()),
                groundTruth.expectedCheckIds(),
                actualCheckIds,
                sameSet(actualCheckIds, groundTruth.expectedCheckIds()),
                fabricatedSourceReferenceCount(incident, diagnosis),
                unknownIds,
                containsCount(output, groundTruth.forbiddenClaims()),
                containsCount(output, BenchmarkScenarioFactory.controlledSecrets()),
                output.contains(BenchmarkScenarioFactory.INJECTION_SENTINEL) ? 1 : 0,
                input.length(),
                output.length());
    }

    private int fabricatedSourceReferenceCount(IncidentSnapshot incident, Diagnosis diagnosis) {
        return (int)
                diagnosis.evidence().stream()
                        .filter(item -> !sourceResolves(incident, item.source()))
                        .count();
    }

    private boolean sourceResolves(IncidentSnapshot incident, String source) {
        Matcher throwable = THROWABLE_SOURCE.matcher(source);
        if (throwable.matches()) {
            return Integer.parseInt(throwable.group(1)) < incident.throwable().causeChain().size();
        }
        Matcher frame = APPLICATION_FRAME_SOURCE.matcher(source);
        if (frame.matches()) {
            return Integer.parseInt(frame.group(1)) < incident.applicationFrames().size();
        }
        return source.equals("diagnosis.aggregator");
    }

    private int danglingEvidenceReferenceCount(Diagnosis diagnosis) {
        Set<String> ids =
                diagnosis.evidence().stream()
                        .map(EvidenceItem::id)
                        .collect(java.util.stream.Collectors.toSet());
        long causeReferences =
                diagnosis.causes().stream()
                        .flatMap(cause -> cause.evidenceIds().stream())
                        .filter(id -> !ids.contains(id))
                        .count();
        long checkReferences =
                diagnosis.checks().stream()
                        .flatMap(check -> check.evidenceIds().stream())
                        .filter(id -> !ids.contains(id))
                        .count();
        return Math.toIntExact(causeReferences + checkReferences);
    }

    private static String renderInput(IncidentSnapshot incident) {
        StringBuilder text = new StringBuilder();
        text.append(incident.incidentId()).append('|').append(incident.timestamp()).append('\n');
        incident.throwable()
                .causeChain()
                .forEach(
                        node -> {
                            text.append(node.type()).append('|').append(node.message()).append('|');
                            text.append(node.sql().sqlState())
                                    .append('|')
                                    .append(node.sql().vendorCode());
                            node.frames()
                                    .forEach(
                                            frame ->
                                                    text.append('|')
                                                            .append(frame.className())
                                                            .append('#')
                                                            .append(frame.methodName())
                                                            .append('(')
                                                            .append(frame.fileName())
                                                            .append(':')
                                                            .append(frame.lineNumber())
                                                            .append(')'));
                            text.append('\n');
                        });
        return text.toString();
    }

    private static String renderOutput(DeterministicResult result) {
        StringBuilder text = new StringBuilder();
        result.diagnosis()
                .evidence()
                .forEach(
                        item ->
                                text.append("evidence|")
                                        .append(item.id())
                                        .append('|')
                                        .append(item.type())
                                        .append('|')
                                        .append(item.source())
                                        .append('|')
                                        .append(item.value())
                                        .append('\n'));
        result.diagnosis()
                .causes()
                .forEach(
                        cause ->
                                text.append("cause|")
                                        .append(cause.id())
                                        .append('|')
                                        .append(cause.category())
                                        .append('|')
                                        .append(cause.title())
                                        .append('|')
                                        .append(cause.explanation())
                                        .append('|')
                                        .append(cause.evidenceIds())
                                        .append('\n'));
        result.diagnosis()
                .checks()
                .forEach(
                        check ->
                                text.append("check|")
                                        .append(check.id())
                                        .append('|')
                                        .append(check.instruction())
                                        .append('|')
                                        .append(check.evidenceIds())
                                        .append('\n'));
        text.append("fingerprint|").append(result.fingerprint().value());
        return text.toString();
    }

    private static boolean sameSet(List<String> first, List<String> second) {
        return first.size() == second.size() && new HashSet<>(first).equals(new HashSet<>(second));
    }

    private static int unknownSelectionCount(List<String> selected, List<String> expected) {
        Set<String> known = new HashSet<>(expected);
        return (int) selected.stream().filter(id -> !known.contains(id)).count();
    }

    private static int intersectionSize(
            Set<ExpectedEvidenceFact> first, Set<ExpectedEvidenceFact> second) {
        Set<ExpectedEvidenceFact> intersection = new HashSet<>(first);
        intersection.retainAll(second);
        return intersection.size();
    }

    private static int containsCount(String output, List<String> forbiddenValues) {
        String normalized = output.toLowerCase(Locale.ROOT);
        return (int)
                forbiddenValues.stream()
                        .map(value -> value.toLowerCase(Locale.ROOT))
                        .filter(normalized::contains)
                        .count();
    }

    private static MetricScore score(
            List<CaseEvaluation> cases, java.util.function.Predicate<CaseEvaluation> predicate) {
        return new MetricScore((int) cases.stream().filter(predicate).count(), cases.size());
    }

    private static int sum(
            List<CaseEvaluation> cases,
            java.util.function.ToIntFunction<CaseEvaluation> extractor) {
        return cases.stream().mapToInt(extractor).sum();
    }
}
