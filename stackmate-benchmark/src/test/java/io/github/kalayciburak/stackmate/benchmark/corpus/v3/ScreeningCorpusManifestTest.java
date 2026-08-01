package io.github.kalayciburak.stackmate.benchmark.corpus.v3;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2Manifest;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ScreeningCorpusManifestTest {

    @Test
    void freezes_twenty_balanced_cases_with_real_execution_majority() {
        ScreeningCorpusManifest manifest = ScreeningCorpusManifest.load();

        assertThat(manifest.cases()).hasSize(20);
        assertThat(manifest.cases())
                .filteredOn(item -> item.split() == ScreeningCase.Split.DEVELOPMENT)
                .hasSize(10);
        assertThat(manifest.cases())
                .filteredOn(item -> item.split() == ScreeningCase.Split.HOLDOUT)
                .hasSize(10);
        assertThat(manifest.cases())
                .filteredOn(ScreeningCase::realExecution)
                .hasSizeGreaterThanOrEqualTo(10);
        assertThat(manifest.cases())
                .extracting(ScreeningCase::purpose)
                .containsExactlyInAnyOrder(ScreeningCase.Purpose.values());
    }

    @Test
    void binds_each_case_to_matching_frozen_v2_provenance() {
        var v2ById =
                CorpusV2Manifest.load().cases().stream()
                        .collect(Collectors.toMap(item -> item.caseId(), item -> item));

        for (ScreeningCase screeningCase : ScreeningCorpusManifest.load().cases()) {
            assertThat(v2ById).containsKey(screeningCase.sourceCaseId());
            assertThat(screeningCase.provenance())
                    .isEqualTo(v2ById.get(screeningCase.sourceCaseId()).provenance());
        }
    }

    @Test
    void pins_the_exact_v2_manifest_digest() throws Exception {
        try (InputStream input =
                CorpusV2Manifest.class.getResourceAsStream(CorpusV2Manifest.RESOURCE)) {
            assertThat(input).isNotNull();
            String actual =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(input.readAllBytes()));

            assertThat(actual).isEqualTo(ScreeningCorpusManifest.load().sourceManifestSha256());
        }
    }

    @Test
    void keeps_holdout_and_adversarial_labels_auditable() {
        Set<ScreeningCase.Purpose> adversarialPurposes =
                ScreeningCorpusManifest.load().cases().stream()
                        .filter(ScreeningCase::adversarial)
                        .map(ScreeningCase::purpose)
                        .collect(Collectors.toSet());

        assertThat(adversarialPurposes)
                .containsExactlyInAnyOrder(
                        ScreeningCase.Purpose.PROMPT_INJECTION,
                        ScreeningCase.Purpose.SECRET_REDACTION,
                        ScreeningCase.Purpose.UNSUPPORTED_CLAIM_TRAP,
                        ScreeningCase.Purpose.FABRICATED_SOURCE_TRAP);
    }

    @Test
    void admits_only_three_candidates_below_the_total_parameter_cap() {
        ScreeningCandidateManifest manifest = ScreeningCandidateManifest.load();

        assertThat(manifest.maximumTotalParameters()).isEqualTo(2_000_000_000L);
        assertThat(manifest.candidates()).filteredOn(ScreeningCandidate::eligible).hasSize(3);
        assertThat(manifest.candidates())
                .filteredOn(ScreeningCandidate::eligible)
                .allSatisfy(
                        candidate ->
                                assertThat(candidate.totalParameters())
                                        .isLessThanOrEqualTo(manifest.maximumTotalParameters()));
        assertThat(manifest.requireCandidate("qwen3.5-2b-q4km-control"))
                .satisfies(
                        candidate -> {
                            assertThat(candidate.eligible()).isFalse();
                            assertThat(candidate.totalParameters())
                                    .isGreaterThan(manifest.maximumTotalParameters());
                        });
    }

    @Test
    void pins_candidate_artifacts_and_the_screening_runtime() {
        ScreeningCandidateManifest manifest = ScreeningCandidateManifest.load();

        assertThat(manifest.runtime().release()).isEqualTo("b10221");
        assertThat(manifest.runtime().commit()).hasSize(40);
        assertThat(manifest.runtime().sha256()).hasSize(64);
        assertThat(manifest.candidates())
                .allSatisfy(
                        candidate -> {
                            assertThat(candidate.officialRevision()).hasSize(40);
                            assertThat(candidate.ggufRevision()).hasSize(40);
                            assertThat(candidate.sha256()).hasSize(64);
                            assertThat(candidate.quantization()).isEqualTo("Q4_K_M");
                            assertThat(candidate.license()).isEqualTo("Apache-2.0");
                        });
    }
}
