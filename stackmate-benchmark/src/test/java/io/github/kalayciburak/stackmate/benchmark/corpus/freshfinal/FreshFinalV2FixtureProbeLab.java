package io.github.kalayciburak.stackmate.benchmark.corpus.freshfinal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kalayciburak.stackmate.analysis.DefaultDiagnosticAnalyzers;
import io.github.kalayciburak.stackmate.analysis.DeterministicDiagnosticEngine;
import io.github.kalayciburak.stackmate.analysis.DeterministicResult;
import io.github.kalayciburak.stackmate.fingerprint.IncidentFingerprinter;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.inference.DeterministicInferenceRequestFactory;
import io.github.kalayciburak.stackmate.inference.InferenceCauseCandidate;
import io.github.kalayciburak.stackmate.inference.InferenceCheckCandidate;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import io.github.kalayciburak.stackmate.privacy.Sanitizer;
import io.github.kalayciburak.stackmate.privacy.SanitizerConfig;
import io.github.kalayciburak.stackmate.throwable.ApplicationFrameSelector;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshot;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotLimits;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotter;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Explicit-only two-run topology probe; the Lab suffix excludes normal Surefire discovery. */
class FreshFinalV2FixtureProbeLab {

    private static final String RESOURCE = "/corpus-v6-fresh-final/descriptors/manifest.json";
    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse(
                            "postgres:17-alpine@sha256:18cfe3ef5e6815560c98237d6216d1e5119702fb0f3894c8785dd58b8bbe5d73")
                    .asCompatibleSubstituteFor("postgres");
    private static final Instant CAPTURE_TIME = Instant.parse("2026-09-04T00:00:00Z");

    private final Sanitizer sanitizer = new DefaultSanitizer(SanitizerConfig.defaults());
    private final ThrowableSnapshotter snapshotter =
            new ThrowableSnapshotter(ThrowableSnapshotLimits.defaults(), sanitizer);
    private final ApplicationFrameSelector applicationFrameSelector =
            new ApplicationFrameSelector(List.of("io.github.kalayciburak.stackmate", "com.example"), 8);
    private final DeterministicDiagnosticEngine diagnosticEngine =
            new DeterministicDiagnosticEngine(
                    DefaultDiagnosticAnalyzers.aggregator(), new IncidentFingerprinter());
    private final DeterministicInferenceRequestFactory requestFactory =
            new DeterministicInferenceRequestFactory(sanitizer);

    @Test
    void reproducesEveryRealFamilyTwice() throws Exception {
        JsonNode manifest = readManifest();
        List<JsonNode> real = new ArrayList<>();
        manifest.path("families")
                .forEach(
                        family -> {
                            if (family.path("classification").asText().startsWith("CONTROLLED_REAL_")) {
                                real.add(family);
                            }
                        });
        assertThat(real).hasSize(24);
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(POSTGRES_IMAGE)) {
            postgres.start();
            for (JsonNode family : real) {
                String familyId = family.path("familyId").asText();
                List<String> expected = strings(family.path("expectedThrowableChain"));
                for (int variant = 0; variant < 2; variant++) {
                    Throwable firstFailure = execute(familyId, variant, postgres);
                    List<String> first = chain(firstFailure);
                    List<String> second = chain(execute(familyId, variant, postgres));
                    System.out.println(
                            familyId
                                    + "/v"
                                    + variant
                                    + " expected="
                                    + expected
                                    + " first="
                                    + first
                                    + " second="
                                    + second);
                    assertThat(first).as(familyId + "/v" + variant).containsExactlyElementsOf(expected);
                    assertThat(second).as(familyId + "/v" + variant + " rerun").containsExactlyElementsOf(expected);
                    if (variant == 0) {
                        printDeterministicRequest(familyId, firstFailure);
                    }
                }
            }
        }
    }

    private void printDeterministicRequest(String familyId, Throwable failure) {
        ThrowableSnapshot throwable = snapshotter.capture(failure);
        IncidentSnapshot incident =
                IncidentSnapshot.of(
                        familyId.toLowerCase(java.util.Locale.ROOT),
                        CAPTURE_TIME,
                        throwable,
                        applicationFrameSelector.select(throwable));
        DeterministicResult result = diagnosticEngine.analyze(incident);
        InferenceRequest request =
                requestFactory.create(
                        result,
                        InferenceRequest.MAXIMUM_TOTAL_CHARACTERS,
                        InferenceRequest.MAXIMUM_OUTPUT_CHARACTERS);
        System.out.println(
                familyId
                        + " diagnostic category="
                        + request.incident().category()
                        + " strength="
                        + request.incident().evidenceStrength()
                        + " causes="
                        + request.candidateCauses().stream()
                                .map(InferenceCauseCandidate::id)
                                .toList()
                        + " checks="
                        + request.candidateChecks().stream()
                                .map(InferenceCheckCandidate::id)
                                .toList());
    }

    private Throwable execute(String familyId, int variant, PostgreSQLContainer postgres) {
        if (FreshFinalV2RuntimeFixtures.supports(familyId)) {
            return FreshFinalV2RuntimeFixtures.execute(familyId, variant);
        }
        if (FreshFinalV2PostgresFixtures.supports(familyId)) {
            return FreshFinalV2PostgresFixtures.execute(familyId, variant, () -> open(postgres));
        }
        throw new IllegalArgumentException("unsupported real fresh-final family: " + familyId);
    }

    private Connection open(PostgreSQLContainer postgres) throws SQLException {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource.getConnection();
    }

    private JsonNode readManifest() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing fresh-final descriptor manifest");
            }
            return new ObjectMapper().readTree(input);
        }
    }

    private List<String> strings(JsonNode values) {
        List<String> result = new ArrayList<>();
        values.forEach(item -> result.add(item.asText()));
        return List.copyOf(result);
    }

    private List<String> chain(Throwable failure) {
        List<String> result = new ArrayList<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (current != null && visited.add(current)) {
            result.add(current.getClass().getName());
            current = current.getCause();
        }
        return List.copyOf(result);
    }
}
