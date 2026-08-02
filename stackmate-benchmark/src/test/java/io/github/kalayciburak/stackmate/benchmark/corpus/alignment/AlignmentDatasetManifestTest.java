package io.github.kalayciburak.stackmate.benchmark.corpus.alignment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AlignmentDatasetManifestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PRIVATE_DATA =
            Pattern.compile(
                    "(?i)(c:\\\\users\\\\|/home/|AKIA[0-9A-Z]{16}|BEGIN [A-Z ]+PRIVATE KEY)");
    private static Path root;
    private static JsonNode manifest;
    private static JsonNode lock;
    private static List<JsonNode> examples;

    @BeforeAll
    static void loadDataset() throws Exception {
        root = findResearchRoot();
        manifest = MAPPER.readTree(root.resolve("datasets/manifest.json").toFile());
        lock = MAPPER.readTree(root.resolve("datasets/lock.json").toFile());
        examples = new ArrayList<>();
        examples.addAll(readJsonLines(root.resolve("datasets/train.jsonl")));
        examples.addAll(readJsonLines(root.resolve("datasets/validation.jsonl")));
    }

    @Test
    void enforcesDatasetLockAndChecksums() throws Exception {
        assertThat(lock.path("schemaVersion").asText()).isEqualTo("1");
        assertThat(lock.path("datasetId").asText()).isEqualTo("stackmate-alignment-dataset-v1");
        assertThat(lock.path("lockedBeforeTraining").asBoolean()).isTrue();
        assertThat(lock.path("datasetManifestSha256").asText())
                .isEqualTo(sha256(root.resolve("datasets/manifest.json")));
        assertThat(lock.path("leakageAuditSha256").asText())
                .isEqualTo(sha256(root.resolve("reports/leakage-audit.json")));
        assertThat(manifest.path("files").path("train").path("sha256").asText())
                .isEqualTo(sha256(root.resolve("datasets/train.jsonl")));
        assertThat(manifest.path("files").path("validation").path("sha256").asText())
                .isEqualTo(sha256(root.resolve("datasets/validation.jsonl")));
    }

    @Test
    void preservesExactDistributionAndFamilyIsolation() {
        assertThat(examples).hasSize(1000);
        assertThat(examples.stream().filter(item -> item.path("split").asText().equals("TRAIN")))
                .hasSize(800);
        assertThat(
                        examples.stream()
                                .filter(item -> item.path("split").asText().equals("VALIDATION")))
                .hasSize(200);
        assertThat(examples.stream().filter(item -> item.path("kind").asText().equals("SUPPORTED")))
                .hasSize(450);
        assertThat(
                        examples.stream()
                                .filter(item -> item.path("kind").asText().equals("HARD_NEGATIVE")))
                .hasSize(250);
        assertThat(
                        examples.stream()
                                .filter(item -> item.path("kind").asText().equals("ABSTENTION")))
                .hasSize(250);
        assertThat(examples.stream().filter(item -> item.path("kind").asText().equals("SECURITY")))
                .hasSize(50);

        Set<String> trainFamilies = families("TRAIN");
        Set<String> validationFamilies = families("VALIDATION");
        assertThat(trainFamilies).hasSize(32);
        assertThat(validationFamilies).hasSize(8);
        assertThat(trainFamilies).doesNotContainAnyElementsOf(validationFamilies);
    }

    @Test
    void validatesEveryTargetAgainstItsSuppliedIdentifiers() throws Exception {
        Set<String> ids = new HashSet<>();
        for (JsonNode example : examples) {
            assertThat(ids.add(example.path("exampleId").asText())).isTrue();
            JsonNode messages = example.path("messages");
            assertThat(messages.size()).isEqualTo(3);
            assertThat(messages.path(0).path("role").asText()).isEqualTo("system");
            assertThat(messages.path(1).path("role").asText()).isEqualTo("user");
            assertThat(messages.path(2).path("role").asText()).isEqualTo("assistant");
            JsonNode request = request(messages.path(1).path("content").asText());
            JsonNode target = MAPPER.readTree(messages.path(2).path("content").asText());
            assertThat(target.path("schemaVersion").asText()).isEqualTo("2");
            assertThat(strings(request.path("candidateCauses"), "id"))
                    .containsAll(strings(target.path("selectedCauseIds"), null));
            assertThat(strings(request.path("candidateChecks"), "id"))
                    .containsAll(strings(target.path("selectedCheckIds"), null));
            assertThat(strings(request.path("facts"), "id"))
                    .containsAll(strings(target.path("usedEvidenceIds"), null));
            boolean abstention = example.path("metadata").path("abstentionRequired").asBoolean();
            if (abstention) {
                assertThat(target.path("selectedCauseIds").size()).isZero();
                assertThat(target.path("selectedCheckIds").size()).isZero();
                assertThat(strings(target.path("limitations"), null))
                        .containsExactly("EVIDENCE_INSUFFICIENT");
            } else {
                assertThat(target.path("selectedCauseIds").size()).isEqualTo(1);
                assertThat(target.path("selectedCheckIds").size()).isEqualTo(1);
                assertThat(target.path("limitations").size()).isZero();
            }
            assertThat(PRIVATE_DATA.matcher(messages.path(2).path("content").asText()).find())
                    .isFalse();
        }
    }

    @Test
    void requiresPassingLeakageAuditAndFrozenChallenge() throws Exception {
        JsonNode report = MAPPER.readTree(root.resolve("reports/leakage-audit.json").toFile());
        assertThat(report.path("status").asText()).isEqualTo("PASS");
        assertThat(report.path("prohibitedOverlapCount").asInt()).isZero();
        assertThat(report.path("lockedSources").path("challengeLockEnforced").asBoolean()).isTrue();
        assertThat(report.path("lockedSources").path("challengeManifestSha256").asText())
                .isEqualTo(lock.path("challengeManifestSha256").asText());
    }

    private static Set<String> families(String split) {
        return examples.stream()
                .filter(item -> item.path("split").asText().equals(split))
                .map(item -> item.path("templateFamily").asText())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static JsonNode request(String user) throws Exception {
        int start = user.indexOf('\n') + 1;
        int end = user.lastIndexOf("\n</STACKMATE_UNTRUSTED_INCIDENT>");
        assertThat(start).isPositive();
        assertThat(end).isGreaterThan(start);
        return MAPPER.readTree(user.substring(start, end));
    }

    private static List<String> strings(JsonNode values, String field) {
        List<String> result = new ArrayList<>();
        values.forEach(
                value -> result.add(field == null ? value.asText() : value.path(field).asText()));
        return result;
    }

    private static List<JsonNode> readJsonLines(Path path) throws Exception {
        List<JsonNode> values = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            values.add(MAPPER.readTree(line));
        }
        return values;
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static Path findResearchRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("research/model-alignment");
            if (Files.isRegularFile(candidate.resolve("datasets/manifest.json"))) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("cannot find research/model-alignment dataset");
    }
}
