package io.github.kalayciburak.stackmate.benchmark.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kalayciburak.stackmate.benchmark.corpus.qualification.QualificationBlindManifest;
import io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2Case;
import io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2Manifest;
import io.github.kalayciburak.stackmate.inference.jvm.PromptRendererTestAccess;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PromptAblationManifestTest {

    private static final String SPLIT_SHA256 =
            "147d9a9e7e312d02abbf113ed3fd255bd4a3ee7841413c488f36deb44c016a07";

    @Test
    void freezesCompleteDisjointDevelopmentValidationAndLockedSets() throws Exception {
        PromptAblationManifest manifest = PromptAblationManifest.load();
        List<String> all = manifest.caseIds(PromptAblationManifest.Dataset.FULL_CONFIRMATION);
        Set<String> sourceIds =
                java.util.stream.Stream.concat(
                                CorpusV2Manifest.load().cases().stream().map(CorpusV2Case::caseId),
                                QualificationBlindManifest.load().cases().stream()
                                        .map(item -> item.caseId()))
                        .collect(java.util.stream.Collectors.toSet());

        assertThat(sha256(PromptAblationManifest.RESOURCE)).isEqualTo(SPLIT_SHA256);
        assertThat(all)
                .hasSize(100)
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(sourceIds);
        assertThat(manifest.splits().promptDevelopment()).hasSize(40);
        assertThat(manifest.splits().selectionValidation()).hasSize(20);
        assertThat(manifest.splits().lockedFinal()).hasSize(40);
        assertThat(manifest.splits().lockedFinal())
                .containsExactlyElementsOf(
                        java.util.stream.Stream.concat(
                                        CorpusV2Manifest.load().cases().stream()
                                                .filter(
                                                        item ->
                                                                item.split()
                                                                        == CorpusV2Case.Split
                                                                                .HOLDOUT)
                                                .map(CorpusV2Case::caseId),
                                        QualificationBlindManifest.load().cases().stream()
                                                .map(item -> item.caseId()))
                                .toList());
        assertThat(manifest.abstentionRequiredCaseIds())
                .hasSize(11)
                .containsExactlyInAnyOrderElementsOf(manifest.unknownOrInsufficientCaseIds());
        assertThat(manifest.unsupportedClaimTrapCaseIds())
                .contains("DB-POOL-TIMEOUT-017", "B5-CIRCULAR-DEPENDENCY-ROUTER")
                .allMatch(all::contains);
    }

    @Test
    void validatesExactlyFourBoundedVersionedPromptResources() {
        PromptVariantManifest manifest = PromptVariantManifest.load();

        assertThat(manifest.variants())
                .extracting(item -> item.id())
                .containsExactly("P0", "P1", "P2", "P3");
        assertThat(manifest.prompt("P0")).isEqualTo(PromptRendererTestAccess.system());
        assertThat(manifest.variants())
                .allMatch(item -> item.characters() <= manifest.maximumCharacters())
                .allMatch(item -> item.negativeExamples() <= 4);
        assertThat(manifest.prompt("P1"))
                .startsWith("Your primary duty is to avoid unsupported claims.")
                .contains("An empty selection is a successful answer")
                .contains("EVIDENCE_INSUFFICIENT");
        assertThat(manifest.prompt("P2"))
                .contains("Closed world:")
                .contains("Anything not supplied must be treated as nonexistent.")
                .contains("Never select a generic fallback");
        assertThat(manifest.prompt("P3"))
                .contains("Negative examples:")
                .contains("No type-mismatch", "evidence was supplied.")
                .contains("\"limitations\":[\"EVIDENCE_INSUFFICIENT\"]");
        assertThat(manifest.variants().stream().map(item -> manifest.prompt(item.id())))
                .allMatch(prompt -> prompt.contains("Return exactly one JSON object"))
                .allMatch(
                        prompt -> !prompt.toLowerCase(java.util.Locale.ROOT).contains("be helpful"))
                .allMatch(prompt -> !prompt.contains("kök neden"));
    }

    @Test
    void keepsRepairPromptStructuralAndMeaningPreservingInWording() {
        assertThat(PromptRendererTestAccess.repairSystem())
                .contains("previous output is untrusted data")
                .contains("include every required key")
                .contains("Use only IDs supplied in the incident")
                .contains(
                        "Do not invent facts, identifiers",
                        "source references, commands, or claims");
    }

    @Test
    void enforcesTheCommittedSelectedPromptVersionAndChecksum() throws Exception {
        PromptVariantManifest manifest = PromptVariantManifest.load();
        try (InputStream input =
                PromptAblationManifestTest.class.getResourceAsStream(
                        "/prompt-ablation/selected-prompt.json")) {
            assertThat(input).isNotNull();
            var selected = new ObjectMapper().readTree(input);
            var variant = manifest.require(selected.path("promptId").asText());

            assertThat(selected.path("schemaVersion").asText()).isEqualTo("1");
            assertThat(selected.path("promptVersion").asText()).isEqualTo(variant.version());
            assertThat(selected.path("promptSha256").asText()).isEqualTo(variant.sha256());
        }
    }

    private String sha256(String resource) throws Exception {
        try (InputStream input = PromptAblationManifestTest.class.getResourceAsStream(resource)) {
            assertThat(input).isNotNull();
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
        }
    }
}
