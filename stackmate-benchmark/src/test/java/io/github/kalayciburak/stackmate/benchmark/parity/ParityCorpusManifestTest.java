package io.github.kalayciburak.stackmate.benchmark.parity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2Case;
import io.github.kalayciburak.stackmate.benchmark.corpus.v2.CorpusV2Manifest;
import io.github.kalayciburak.stackmate.benchmark.prompt.PromptAblationManifest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ParityCorpusManifestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PRIVATE_DATA =
            Pattern.compile("(?i)(c:\\\\users\\\\|/home/|/mnt/c/|BEGIN [A-Z ]+PRIVATE KEY)");
    private static Path root;
    private static JsonNode manifest;
    private static JsonNode corpus;

    @BeforeAll
    static void loadCorpus() throws Exception {
        root = findParityRoot();
        manifest = MAPPER.readTree(root.resolve("manifest.json").toFile());
        corpus = MAPPER.readTree(root.resolve("corpus.json").toFile());
    }

    @Test
    void freezesFortyBalancedDevelopmentOnlyCases() throws Exception {
        assertThat(manifest.path("schemaVersion").asText()).isEqualTo("1");
        assertThat(manifest.path("corpusId").asText())
                .isEqualTo("stackmate-phase-5-3-parity-v1");
        assertThat(manifest.path("caseCount").asInt()).isEqualTo(40);
        assertThat(manifest.path("corpusSha256").asText())
                .isEqualTo(sha256(root.resolve("corpus.json")));
        assertThat(manifest.path("realRuntimeOrIntegrationCases").asInt()).isEqualTo(31);
        assertThat(manifest.path("restraintCases").asInt()).isEqualTo(8);
        assertThat(manifest.path("unsupportedClaimTrapCases").asInt()).isEqualTo(9);
        assertThat(manifest.path("domainCounts").size()).isEqualTo(8);
        manifest.path("domainCounts").elements().forEachRemaining(value -> assertThat(value.asInt()).isGreaterThanOrEqualTo(3));

        List<String> ids = strings(manifest.path("caseIds"), null);
        assertThat(ids).hasSize(40).doesNotHaveDuplicates();
        assertThat(strings(corpus.path("cases"), "caseId")).containsExactlyElementsOf(ids);
    }

    @Test
    void excludesEveryLockedCaseAndKeepsMandatoryDevelopmentCoverage() {
        PromptAblationManifest split = PromptAblationManifest.load();
        Map<String, CorpusV2Case> source =
                CorpusV2Manifest.load().cases().stream()
                        .collect(Collectors.toMap(CorpusV2Case::caseId, Function.identity()));
        Set<String> selected = Set.copyOf(strings(manifest.path("caseIds"), null));

        assertThat(selected)
                .doesNotContainAnyElementsOf(
                        split.caseIds(PromptAblationManifest.Dataset.LOCKED_FINAL));
        assertThat(selected)
                .allMatch(id -> source.get(id).split() == CorpusV2Case.Split.DEVELOPMENT);

        Set<String> mandatoryDevelopment = new HashSet<>();
        java.util.stream.Stream.concat(
                        split.abstentionRequiredCaseIds().stream(),
                        split.unsupportedClaimTrapCaseIds().stream())
                .filter(source::containsKey)
                .filter(id -> source.get(id).split() == CorpusV2Case.Split.DEVELOPMENT)
                .forEach(mandatoryDevelopment::add);
        assertThat(selected).containsAll(mandatoryDevelopment);
    }

    @Test
    void validatesGroundTruthAgainstEachProductionRequest() throws Exception {
        for (JsonNode item : corpus.path("cases")) {
            JsonNode request = item.path("request");
            assertThat(request.path("schemaVersion").asText()).isEqualTo("2");
            assertThat(strings(request.path("candidateCauses"), "id"))
                    .containsAll(strings(item.path("expectedCauseIds"), null));
            assertThat(strings(request.path("candidateChecks"), "id"))
                    .containsAll(strings(item.path("expectedCheckIds"), null));

            JsonNode promptRequest = requestFromPrompt(item.path("productionUserPrompt").asText());
            ObjectNode modelVisibleRequest = request.deepCopy();
            modelVisibleRequest.remove(List.of("id", "maximumOutputCharacters"));
            assertThat(promptRequest).isEqualTo(modelVisibleRequest);
            if (item.path("abstentionRequired").asBoolean()) {
                assertThat(item.path("expectedCauseIds")).isEmpty();
                assertThat(item.path("expectedCheckIds")).isEmpty();
            } else {
                assertThat(item.path("expectedCauseIds")).isNotEmpty();
                assertThat(item.path("expectedCheckIds")).isNotEmpty();
            }
        }
    }

    @Test
    void preservesPromptHashesAndContainsNoPrivatePaths() throws Exception {
        assertThat(corpus.path("prompts").path("P0").path("semanticSha256").asText())
                .isEqualTo(
                        sha256(
                                corpus.path("prompts").path("P0").path("system").asText()
                                        .getBytes(StandardCharsets.UTF_8)));
        assertThat(corpus.path("prompts").path("P3").path("semanticSha256").asText())
                .isEqualTo(
                        sha256(
                                corpus.path("prompts").path("P3").path("system").asText()
                                        .getBytes(StandardCharsets.UTF_8)));
        assertThat(PRIVATE_DATA.matcher(Files.readString(root.resolve("corpus.json"))).find())
                .isFalse();
        assertThat(PRIVATE_DATA.matcher(Files.readString(root.resolve("manifest.json"))).find())
                .isFalse();
    }

    private static JsonNode requestFromPrompt(String prompt) throws Exception {
        int start = prompt.indexOf('\n') + 1;
        int end = prompt.lastIndexOf("\n</STACKMATE_UNTRUSTED_INCIDENT>");
        assertThat(start).isPositive();
        assertThat(end).isGreaterThan(start);
        return MAPPER.readTree(prompt.substring(start, end));
    }

    private static List<String> strings(JsonNode values, String field) {
        List<String> result = new ArrayList<>();
        values.forEach(
                value -> result.add(field == null ? value.asText() : value.path(field).asText()));
        return result;
    }

    private static String sha256(Path path) throws Exception {
        return sha256(Files.readAllBytes(path));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static Path findParityRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("research/model-alignment/parity");
            if (Files.isRegularFile(candidate.resolve("manifest.json"))) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("cannot find research/model-alignment/parity");
    }
}
