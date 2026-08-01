package io.github.kalayciburak.stackmate.benchmark.corpus.v2;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Independently authored benchmark V2 manifest; no production analyzer constant is imported. */
public record CorpusV2Manifest(
        String schemaVersion,
        String corpusId,
        String frozenAt,
        Map<String, String> frameworks,
        List<CorpusV2Case> cases) {

    public static final String RESOURCE = "/corpus-v2/manifest.json";

    public CorpusV2Manifest {
        schemaVersion = text(schemaVersion, "schemaVersion");
        corpusId = text(corpusId, "corpusId");
        frozenAt = text(frozenAt, "frozenAt");
        frameworks = Map.copyOf(Objects.requireNonNull(frameworks, "frameworks"));
        cases = List.copyOf(Objects.requireNonNull(cases, "cases"));
        if (!"2".equals(schemaVersion) || cases.isEmpty()) {
            throw new IllegalArgumentException("invalid corpus V2 manifest");
        }
        var ids = new HashSet<String>();
        for (CorpusV2Case benchmarkCase : cases) {
            if (!ids.add(benchmarkCase.caseId())) {
                throw new IllegalArgumentException(
                        "duplicate corpus V2 case ID: " + benchmarkCase.caseId());
            }
        }
    }

    /** Loads the immutable classpath manifest with strict unknown-field handling. */
    public static CorpusV2Manifest load() {
        ObjectMapper mapper =
                new ObjectMapper()
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        try (InputStream input = CorpusV2Manifest.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing corpus V2 manifest: " + RESOURCE);
            }
            return mapper.readValue(input, CorpusV2Manifest.class);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read corpus V2 manifest", failure);
        }
    }

    private static String text(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
