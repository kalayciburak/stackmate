package io.github.kalayciburak.stackmate.benchmark.corpus.alignment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kalayciburak.stackmate.analysis.DefaultDiagnosticAnalyzers;
import io.github.kalayciburak.stackmate.analysis.DeterministicDiagnosticEngine;
import io.github.kalayciburak.stackmate.fingerprint.IncidentFingerprinter;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PostTrainingChallengeManifestTest {

    private static final String LOCK = "/corpus-v4-post-training-challenge/lock.json";

    @Test
    void freezesRequiredChallengeComposition() {
        PostTrainingChallengeManifest manifest = PostTrainingChallengeManifest.load();

        assertThat(manifest.cases()).hasSize(30);
        assertThat(manifest.cases().stream().filter(PostTrainingChallengeCase::realExecution))
                .hasSize(20);
        assertThat(
                        manifest.cases().stream()
                                .filter(
                                        item ->
                                                item.challengeClass()
                                                        == PostTrainingChallengeCase.ChallengeClass
                                                                .HARD_NEGATIVE))
                .hasSize(5);
        assertThat(
                        manifest.cases().stream()
                                .filter(
                                        item ->
                                                item.challengeClass()
                                                        == PostTrainingChallengeCase.ChallengeClass
                                                                .ADVERSARIAL_OR_INSUFFICIENT))
                .hasSize(5);
        assertThat(
                        manifest.cases().stream()
                                .filter(
                                        item ->
                                                item.expectedDisposition()
                                                        == PostTrainingChallengeCase.Disposition
                                                                .RESTRAIN))
                .hasSize(3);
    }

    @Test
    void enforcesFrozenManifestChecksum() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode lock;
        try (InputStream input = getClass().getResourceAsStream(LOCK)) {
            lock = mapper.readTree(java.util.Objects.requireNonNull(input, LOCK));
        }
        byte[] manifestBytes;
        try (InputStream input =
                getClass().getResourceAsStream(PostTrainingChallengeManifest.RESOURCE)) {
            manifestBytes =
                    java.util.Objects.requireNonNull(input, PostTrainingChallengeManifest.RESOURCE)
                            .readAllBytes();
        }

        assertThat(lock.path("schemaVersion").asText()).isEqualTo("1");
        assertThat(lock.path("manifestResource").asText())
                .isEqualTo(PostTrainingChallengeManifest.RESOURCE);
        assertThat(lock.path("manifestSha256").asText()).isEqualTo(sha256(manifestBytes));
        assertThat(lock.path("caseCount").asInt()).isEqualTo(30);
        assertThat(lock.path("lockedForTraining").asBoolean()).isTrue();
    }

    @Test
    void usesNoExistingQualificationCaseId() throws Exception {
        Set<String> challengeIds =
                PostTrainingChallengeManifest.load().cases().stream()
                        .map(PostTrainingChallengeCase::caseId)
                        .collect(java.util.stream.Collectors.toSet());
        JsonNode v2 = resourceJson("/corpus-v2/manifest.json");
        JsonNode blind = resourceJson("/corpus-v3-qualification/manifest.json");

        assertThat(v2.path("cases").findValuesAsText("caseId"))
                .doesNotContainAnyElementsOf(challengeIds);
        assertThat(blind.path("cases").findValuesAsText("caseId"))
                .doesNotContainAnyElementsOf(challengeIds);
    }

    @Test
    void deterministicEngineCoversFrozenSelectionContract() throws Exception {
        DeterministicDiagnosticEngine engine =
                new DeterministicDiagnosticEngine(
                        DefaultDiagnosticAnalyzers.aggregator(), new IncidentFingerprinter());
        PostTrainingChallengeIncidentFactory incidents = new PostTrainingChallengeIncidentFactory();

        for (PostTrainingChallengeCase benchmarkCase :
                PostTrainingChallengeManifest.load().cases()) {
            var diagnosis = engine.analyze(incidents.create(benchmarkCase)).diagnosis();

            assertThat(diagnosis.causes())
                    .as(benchmarkCase.caseId())
                    .hasSizeGreaterThanOrEqualTo(
                            Math.max(1, benchmarkCase.expectedCauseSelections()));
            assertThat(diagnosis.checks())
                    .as(benchmarkCase.caseId())
                    .hasSizeGreaterThanOrEqualTo(benchmarkCase.expectedCheckSelections());
            assertThat(diagnosis.causes().getFirst().category().name())
                    .as(benchmarkCase.caseId())
                    .isEqualTo(benchmarkCase.category());
        }
    }

    private JsonNode resourceJson(String resource) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            return new ObjectMapper().readTree(java.util.Objects.requireNonNull(input, resource));
        }
    }

    private String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
