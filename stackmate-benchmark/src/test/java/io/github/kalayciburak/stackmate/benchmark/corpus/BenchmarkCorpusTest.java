package io.github.kalayciburak.stackmate.benchmark.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.analysis.DefaultDiagnosticAnalyzers;
import io.github.kalayciburak.stackmate.analysis.DeterministicDiagnosticEngine;
import io.github.kalayciburak.stackmate.analysis.DeterministicResult;
import io.github.kalayciburak.stackmate.evidence.EvidenceItem;
import io.github.kalayciburak.stackmate.fingerprint.IncidentFingerprinter;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BenchmarkCorpusTest {

    private final DeterministicDiagnosticEngine engine =
            new DeterministicDiagnosticEngine(
                    DefaultDiagnosticAnalyzers.aggregator(), new IncidentFingerprinter());

    @Test
    void containsExactlyTheRequiredDistributionAndUniqueBindings() {
        assertThat(BenchmarkCorpus.cases()).hasSize(50);
        assertThat(BenchmarkCorpus.cases())
                .extracting(GroundTruthCase::caseId)
                .doesNotHaveDuplicates();
        assertThat(BenchmarkCorpus.cases())
                .extracting(GroundTruthCase::scenarioFactoryKey)
                .doesNotHaveDuplicates();

        Map<BenchmarkDomain, Long> distribution =
                BenchmarkCorpus.cases().stream()
                        .collect(
                                Collectors.groupingBy(
                                        GroundTruthCase::domain, Collectors.counting()));
        assertThat(distribution)
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                BenchmarkDomain.GENERAL_JAVA, 8L,
                                BenchmarkDomain.SPRING_DI_CONFIGURATION, 8L,
                                BenchmarkDomain.MVC_VALIDATION_SERIALIZATION, 9L,
                                BenchmarkDomain.JDBC_PERSISTENCE, 11L,
                                BenchmarkDomain.SECURITY, 4L,
                                BenchmarkDomain.INFRASTRUCTURE, 5L,
                                BenchmarkDomain.CONCURRENCY_TIMEOUT_RESOURCE, 5L));
        assertThat(BenchmarkCorpus.cases())
                .allMatch(item -> BenchmarkScenarioFactory.supports(item.scenarioFactoryKey()));
    }

    @Test
    void everyGroundTruthExpectationResolvesAgainstItsDeterministicResult() {
        for (GroundTruthCase groundTruth : BenchmarkCorpus.cases()) {
            DeterministicResult result =
                    engine.analyze(
                            BenchmarkScenarioFactory.create(
                                    groundTruth.scenarioFactoryKey(), groundTruth.caseId()));

            assertThat(result.incident().throwable().root().type())
                    .as(groundTruth.caseId())
                    .isEqualTo(groundTruth.expectedRootType());
            assertThat(result.incident().applicationFrames())
                    .as(groundTruth.caseId())
                    .anyMatch(frame -> frame.className().startsWith("com.example."));
            assertThat(result.diagnosis().causes().getFirst().category())
                    .as(groundTruth.caseId())
                    .isEqualTo(groundTruth.expectedCategory());
            assertThat(result.diagnosis().evidence())
                    .as(groundTruth.caseId())
                    .extracting(item -> new ExpectedEvidenceFact(item.type(), item.value()))
                    .containsAll(groundTruth.expectedEvidenceFacts());
            assertThat(result.diagnosis().causes())
                    .as(groundTruth.caseId())
                    .extracting(cause -> cause.id())
                    .containsExactlyInAnyOrderElementsOf(groundTruth.expectedCauseIds());
            assertThat(result.diagnosis().checks())
                    .as(groundTruth.caseId())
                    .extracting(check -> check.id())
                    .containsExactlyInAnyOrderElementsOf(groundTruth.expectedCheckIds());

            var evidenceIds =
                    result.diagnosis().evidence().stream()
                            .map(EvidenceItem::id)
                            .collect(Collectors.toSet());
            assertThat(result.diagnosis().causes())
                    .allMatch(cause -> evidenceIds.containsAll(cause.evidenceIds()));
            assertThat(result.diagnosis().checks())
                    .allMatch(check -> evidenceIds.containsAll(check.evidenceIds()));
        }
    }
}
