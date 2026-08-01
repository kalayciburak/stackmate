package io.github.kalayciburak.stackmate.benchmark.corpus.qualification;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2Case;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class QualificationBlindManifestTest {

    @Test
    void freezesTwentyIndependentCasesWithAtLeastFourteenRealExecutions() {
        QualificationBlindManifest manifest = QualificationBlindManifest.load();

        assertThat(manifest.cases()).hasSize(20);
        assertThat(manifest.cases().stream().filter(QualificationBlindCase::realExecution))
                .hasSizeGreaterThanOrEqualTo(14);
        assertThat(manifest.cases().stream().map(QualificationBlindCase::caseId))
                .doesNotHaveDuplicates();
        assertThat(manifest.cases().stream().map(QualificationBlindCase::recipe))
                .doesNotHaveDuplicates();
        assertThat(
                        manifest.cases().stream()
                                .filter(
                                        item ->
                                                item.expectedDisposition()
                                                        == CorpusV2Case.Disposition.RESTRAIN))
                .hasSize(3);
    }

    @Test
    void doesNotReuseAnyFrozenV2Recipe() {
        var v2Recipes =
                io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2Manifest.load()
                        .cases()
                        .stream()
                        .map(CorpusV2Case::recipe)
                        .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        assertThat(QualificationBlindManifest.load().cases())
                .allMatch(item -> !v2Recipes.contains(item.recipe()))
                .allMatch(item -> item.recipe().startsWith("blind."));
    }
}
