package io.github.kalayciburak.stackmate.benchmark.corpus.freshfinal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class FreshFinalV2FrozenCorpusTest {

    private static final String GENERATION_RESOURCE =
            "/corpus-v6-fresh-final/generation-inputs/fresh-final.jsonl";
    private static final String MANIFEST_RESOURCE =
            "/corpus-v6-fresh-final/manifests/fresh-final-manifest.json";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void generationClasspathContainsNoScoringLabels() throws Exception {
        byte[] generation = resourceBytes(GENERATION_RESOURCE);
        JsonNode manifest = mapper.readTree(resourceBytes(MANIFEST_RESOURCE));
        List<JsonNode> rows = new ArrayList<>();
        for (String line : new String(generation, StandardCharsets.UTF_8).lines().toList()) {
            assertThat(line).isNotBlank();
            JsonNode row = mapper.readTree(line);
            rejectLabelFields(row, "$");
            rows.add(row);
        }

        assertThat(rows).hasSize(30);
        assertThat(rows.stream().map(row -> row.path("familyId").asText()).distinct()).hasSize(30);
        assertThat(rows)
                .allSatisfy(
                        row ->
                                assertThat(row.path("orders").findValuesAsText("order"))
                                        .containsExactly("ORIGINAL", "PERMUTED"));
        assertThat(manifest.path("status").asText()).isEqualTo("FROZEN");
        assertThat(manifest.path("counts").path("families").asInt()).isEqualTo(30);
        assertThat(manifest.path("artifacts").path("generationInputs").path("sha256").asText())
                .isEqualTo(sha256(generation));
        assertThat(getClass().getResource("/corpus-v6-fresh-final/scoring-labels.jsonl")).isNull();
        assertThat(getClass().getResource("/phase-5-4-3-labels/scoring-labels.jsonl")).isNull();
    }

    private void rejectLabelFields(JsonNode node, String location) {
        if (node.isObject()) {
            node.fieldNames()
                    .forEachRemaining(
                            field -> {
                                String folded = field.toLowerCase(java.util.Locale.ROOT);
                                assertThat(
                                                folded.startsWith("expected")
                                                        || folded.equals("label")
                                                        || folded.equals("disposition")
                                                        || folded.equals("purposelabels"))
                                        .as("label field at %s.%s", location, field)
                                        .isFalse();
                                rejectLabelFields(node.get(field), location + "." + field);
                            });
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                rejectLabelFields(node.get(index), location + "[" + index + "]");
            }
        }
    }

    private byte[] resourceBytes(String resource) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return input.readAllBytes();
        }
    }

    private String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
