package io.github.kalayciburak.stackmate.benchmark.corpus.realworld;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RealWorldCorpusManifestTest {

    @Test
    void freezesFamilySeparatedRealWorldComposition() {
        RealWorldCorpusManifest manifest = RealWorldCorpusManifest.load();

        assertThat(manifest.families()).hasSize(120);
        assertThat(manifest.developmentFamilies()).hasSize(90);
        assertThat(manifest.families()).extracting(item -> item.familyId()).doesNotHaveDuplicates();
        assertThat(manifest.families())
                .filteredOn(
                        item ->
                                item.classification()
                                        != RealWorldCorpusManifest.Classification
                                                .CONTROLLED_SYNTHETIC)
                .hasSize(90);
        assertThat(manifest.families())
                .filteredOn(
                        item ->
                                item.classification()
                                        == RealWorldCorpusManifest.Classification
                                                .CONTROLLED_SYNTHETIC)
                .hasSize(30);
    }

    @Test
    void keepsFamiliesAndTriggerPrimitivesIsolatedAcrossSplits() {
        RealWorldCorpusManifest manifest = RealWorldCorpusManifest.load();
        for (RealWorldCorpusManifest.Split left : RealWorldCorpusManifest.Split.values()) {
            Set<String> leftFamilies = values(manifest, left, item -> item.familyId());
            Set<String> leftTriggers = values(manifest, left, item -> item.triggerPrimitive());
            for (RealWorldCorpusManifest.Split right : RealWorldCorpusManifest.Split.values()) {
                if (left == right) {
                    continue;
                }
                assertThat(leftFamilies)
                        .doesNotContainAnyElementsOf(
                                values(manifest, right, item -> item.familyId()));
                assertThat(leftTriggers)
                        .doesNotContainAnyElementsOf(
                                values(manifest, right, item -> item.triggerPrimitive()));
            }
        }
    }

    @Test
    void recordsExplicitGroundTruthAndNoPrivateData() {
        RealWorldCorpusManifest manifest = RealWorldCorpusManifest.load();
        assertThat(manifest.families())
                .allMatch(item -> !item.privateData())
                .allMatch(
                        item ->
                                item.selectionCardinality().causes()
                                        == item.expectedCauseIds().size())
                .allMatch(
                        item ->
                                item.selectionCardinality().checks()
                                        == item.expectedCheckIds().size())
                .allMatch(item -> item.groundTruthReview().status().equals("REVIEWED_TWO_PASS"));
    }

    private Set<String> values(
            RealWorldCorpusManifest manifest,
            RealWorldCorpusManifest.Split split,
            java.util.function.Function<RealWorldCorpusManifest.Family, String> value) {
        return manifest.families().stream()
                .filter(item -> item.split() == split)
                .map(value)
                .collect(Collectors.toSet());
    }
}
