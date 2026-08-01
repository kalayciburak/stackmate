package io.github.kalayciburak.stackmate.benchmark.corpus.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CorpusV2ManifestTest {

    @Test
    void freezesIndependentDevelopmentAndHoldoutComposition() {
        CorpusV2Manifest manifest = CorpusV2Manifest.load();

        assertThat(manifest.cases()).hasSize(80);
        assertThat(manifest.cases())
                .filteredOn(item -> item.split() == CorpusV2Case.Split.DEVELOPMENT)
                .hasSize(60);
        assertThat(manifest.cases())
                .filteredOn(item -> item.split() == CorpusV2Case.Split.HOLDOUT)
                .hasSize(20);
        assertThat(manifest.cases()).filteredOn(CorpusV2Case::realExecution).hasSize(54);
        assertThat(manifest.cases().stream().map(CorpusV2Case::caseId)).doesNotHaveDuplicates();

        Map<CorpusV2Case.Domain, Long> distribution =
                manifest.cases().stream()
                        .collect(
                                Collectors.groupingBy(CorpusV2Case::domain, Collectors.counting()));
        assertThat(distribution)
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                CorpusV2Case.Domain.GENERAL_JAVA, 10L,
                                CorpusV2Case.Domain.SPRING_CONFIGURATION, 12L,
                                CorpusV2Case.Domain.MVC, 12L,
                                CorpusV2Case.Domain.DATABASE, 20L,
                                CorpusV2Case.Domain.SECURITY, 8L,
                                CorpusV2Case.Domain.NETWORK, 8L,
                                CorpusV2Case.Domain.CONCURRENCY, 5L,
                                CorpusV2Case.Domain.ADVERSARIAL, 5L));
    }

    @Test
    void keepsUnknownCasesExplicitlyRestrained() {
        assertThat(CorpusV2Manifest.load().cases())
                .filteredOn(item -> item.category().equals("UNKNOWN"))
                .allSatisfy(
                        item -> {
                            assertThat(item.expectedDisposition())
                                    .isEqualTo(CorpusV2Case.Disposition.RESTRAIN);
                            assertThat(item.expectedCauseKeys()).isEmpty();
                            assertThat(item.expectedCheckKeys()).isEmpty();
                        });
    }
}
