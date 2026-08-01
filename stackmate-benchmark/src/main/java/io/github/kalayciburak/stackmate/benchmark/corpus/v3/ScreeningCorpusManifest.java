package io.github.kalayciburak.stackmate.benchmark.corpus.v3;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Frozen English-only screening tournament manifest. */
public record ScreeningCorpusManifest(
        String schemaVersion,
        String corpusId,
        String frozenAt,
        String sourceCorpusId,
        String sourceManifestSha256,
        String language,
        String promptVersion,
        List<ScreeningCase> cases) {

    public static final String RESOURCE = "/corpus-v3-screening/manifest.json";

    public ScreeningCorpusManifest {
        schemaVersion = exact(schemaVersion, "3", "schemaVersion");
        corpusId = exact(corpusId, "stackmate-corpus-v3-screening", "corpusId");
        frozenAt = text(frozenAt, "frozenAt");
        sourceCorpusId = exact(sourceCorpusId, "stackmate-corpus-v2", "sourceCorpusId");
        sourceManifestSha256 = text(sourceManifestSha256, "sourceManifestSha256");
        language = exact(language, "en", "language");
        promptVersion = text(promptVersion, "promptVersion");
        cases = List.copyOf(Objects.requireNonNull(cases, "cases"));
        if (cases.size() != 20) {
            throw new IllegalArgumentException("screening corpus must contain exactly 20 cases");
        }
        var ids = new HashSet<String>();
        var purposes = new HashSet<ScreeningCase.Purpose>();
        for (ScreeningCase benchmarkCase : cases) {
            if (!ids.add(benchmarkCase.caseId())) {
                throw new IllegalArgumentException("duplicate screening case ID");
            }
            if (!purposes.add(benchmarkCase.purpose())) {
                throw new IllegalArgumentException("duplicate screening purpose");
            }
        }
    }

    /** Loads the immutable classpath manifest with strict unknown-field handling. */
    public static ScreeningCorpusManifest load() {
        ObjectMapper mapper =
                new ObjectMapper()
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        try (InputStream input = ScreeningCorpusManifest.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing screening manifest: " + RESOURCE);
            }
            return mapper.readValue(input, ScreeningCorpusManifest.class);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read screening manifest", failure);
        }
    }

    private static String exact(String value, String expected, String field) {
        if (!expected.equals(Objects.requireNonNull(value, field))) {
            throw new IllegalArgumentException(field + " must be " + expected);
        }
        return value;
    }

    private static String text(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
