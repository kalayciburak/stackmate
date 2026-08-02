package io.github.kalayciburak.stackmate.benchmark.corpus.qualification;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.analysis.DefaultDiagnosticAnalyzers;
import io.github.kalayciburak.stackmate.analysis.DeterministicDiagnosticEngine;
import io.github.kalayciburak.stackmate.analysis.DiagnosticCategory;
import io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2Case;
import io.github.kalayciburak.stackmate.fingerprint.IncidentFingerprinter;
import io.github.kalayciburak.stackmate.inference.DeterministicInferenceRequestFactory;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualificationBlindInferenceInputTest {

    private static final List<String> CONTROLLED_SECRETS =
            List.of("phase5-ultra-secret", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJidXJhayJ9.signature");

    private final DeterministicDiagnosticEngine engine =
            new DeterministicDiagnosticEngine(
                    DefaultDiagnosticAnalyzers.aggregator(), new IncidentFingerprinter());
    private final DeterministicInferenceRequestFactory requestFactory =
            new DeterministicInferenceRequestFactory(DefaultSanitizer.defaults());
    private final QualificationBlindIncidentFactory incidents =
            new QualificationBlindIncidentFactory();

    @Test
    void producesFrozenDeterministicCategoriesAndBoundedCandidateSets() throws Exception {
        for (QualificationBlindCase benchmarkCase : QualificationBlindManifest.load().cases()) {
            var deterministic = engine.analyze(incidents.create(benchmarkCase));
            var request = requestFactory.create(deterministic, 16_000, 8_192);
            var primary = deterministic.diagnosis().causes().getFirst();

            assertThat(DiagnosticCategory.valueOf(benchmarkCase.expectedCategory()))
                    .as(benchmarkCase.caseId())
                    .isNotNull();
            assertThat(primary.category()).as(benchmarkCase.caseId()).isNotNull();
            if (benchmarkCase.expectedDisposition() == CorpusV2Case.Disposition.RESTRAIN) {
                assertThat(deterministic.diagnosis().fallback())
                        .as(benchmarkCase.caseId())
                        .isTrue();
            } else {
                assertThat(request.candidateCauses())
                        .as(benchmarkCase.caseId())
                        .hasSizeGreaterThanOrEqualTo(benchmarkCase.expectedCauseSelections());
                assertThat(request.candidateChecks())
                        .as(benchmarkCase.caseId())
                        .hasSizeGreaterThanOrEqualTo(benchmarkCase.expectedCheckSelections());
            }
            String boundedInput = request.toString();
            assertThat(CONTROLLED_SECRETS)
                    .as(benchmarkCase.caseId())
                    .noneMatch(boundedInput::contains);
        }
    }
}
