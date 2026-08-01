package io.github.kalayciburak.stackmate.benchmark.corpus.qualification;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Immutable Phase 5 blind corpus loaded with strict JSON handling. */
public record QualificationBlindManifest(
        String schemaVersion,
        String corpusId,
        String frozenAt,
        String language,
        String promptVersion,
        List<QualificationBlindCase> cases) {

    public static final String RESOURCE = "/corpus-phase-5-blind/manifest.json";

    public QualificationBlindManifest {
        schemaVersion = exact(schemaVersion, "1", "schemaVersion");
        corpusId = exact(corpusId, "stackmate-phase-5-blind", "corpusId");
        frozenAt = text(frozenAt, "frozenAt");
        language = exact(language, "en", "language");
        promptVersion = exact(promptVersion, "2", "promptVersion");
        cases = List.copyOf(Objects.requireNonNull(cases, "cases"));
        if (cases.size() != 20) {
            throw new IllegalArgumentException("blind corpus must contain exactly 20 cases");
        }
        var ids = new HashSet<String>();
        var recipes = new HashSet<String>();
        for (QualificationBlindCase benchmarkCase : cases) {
            if (!ids.add(benchmarkCase.caseId())) {
                throw new IllegalArgumentException("duplicate blind case ID");
            }
            if (!recipes.add(benchmarkCase.recipe())) {
                throw new IllegalArgumentException("duplicate blind recipe");
            }
        }
        if (cases.stream().filter(QualificationBlindCase::realExecution).count() < 14) {
            throw new IllegalArgumentException("blind corpus needs at least 14 real executions");
        }
    }

    /** Loads the frozen classpath manifest without accepting unknown fields or trailing data. */
    public static QualificationBlindManifest load() {
        ObjectMapper mapper =
                new ObjectMapper()
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        try (InputStream input =
                QualificationBlindManifest.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing blind corpus manifest: " + RESOURCE);
            }
            return mapper.readValue(input, QualificationBlindManifest.class);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read blind corpus manifest", failure);
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
