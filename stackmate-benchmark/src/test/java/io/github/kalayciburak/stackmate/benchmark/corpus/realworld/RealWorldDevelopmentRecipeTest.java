package io.github.kalayciburak.stackmate.benchmark.corpus.realworld;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.analysis.DefaultDiagnosticAnalyzers;
import io.github.kalayciburak.stackmate.analysis.DeterministicDiagnosticEngine;
import io.github.kalayciburak.stackmate.fingerprint.IncidentFingerprinter;
import io.github.kalayciburak.stackmate.inference.DeterministicInferenceRequestFactory;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RealWorldDevelopmentRecipeTest {

    private static final DeterministicDiagnosticEngine DIAGNOSTIC_ENGINE =
            new DeterministicDiagnosticEngine(
                    DefaultDiagnosticAnalyzers.aggregator(), new IncidentFingerprinter());
    private static final DeterministicInferenceRequestFactory REQUEST_FACTORY =
            new DeterministicInferenceRequestFactory(DefaultSanitizer.defaults());

    @ParameterizedTest(name = "{0} variant {1}")
    @MethodSource("developmentNonPostgresRecipes")
    void reproducesDevelopmentExceptionTopology(
            RealWorldCorpusManifest.Family family, int variant) {
        Throwable failure = execute(family.familyId(), variant);
        List<String> types = causeTypes(failure);

        assertThat(types).isNotEmpty();
        assertThat(types.getLast()).isEqualTo(family.rootType());
        assertThat(types.subList(0, types.size() - 1))
                .containsExactlyElementsOf(family.wrapperTopology());
    }

    @ParameterizedTest(name = "synthetic {0} variant {1}")
    @MethodSource("developmentSyntheticRecipes")
    void validatesDevelopmentSyntheticTopologyAndSelections(
            RealWorldCorpusManifest.Family family, int variant) {
        RealWorldSyntheticFixtures.Fixture fixture =
                RealWorldSyntheticFixtures.create(family.familyId(), variant);
        List<String> types =
                fixture.incident().throwable().causeChain().stream()
                        .map(item -> item.type())
                        .toList();
        assertThat(types.getLast()).isEqualTo(family.rootType());
        assertThat(types.subList(0, types.size() - 1))
                .containsExactlyElementsOf(family.wrapperTopology());

        InferenceRequest request =
                fixture.requestOverride() != null
                        ? fixture.requestOverride()
                        : REQUEST_FACTORY.create(
                                DIAGNOSTIC_ENGINE.analyze(fixture.incident()), 16_000, 8_192);
        assertThat(request.candidateCauses())
                .extracting(item -> item.id())
                .containsAll(family.expectedCauseIds());
        assertThat(request.candidateChecks())
                .extracting(item -> item.id())
                .containsAll(family.expectedCheckIds());
        assertThat(family.selectionCardinality().causes())
                .isEqualTo(family.expectedCauseIds().size());
        assertThat(family.selectionCardinality().checks())
                .isEqualTo(family.expectedCheckIds().size());
    }

    @Test
    void implementsEveryCatalogedRealFixtureWithoutExecutingFinalFamilies() {
        RealWorldCorpusManifest manifest = RealWorldCorpusManifest.load();
        assertThat(manifest.families())
                .filteredOn(
                        item ->
                                item.classification()
                                        != RealWorldCorpusManifest.Classification
                                                .CONTROLLED_SYNTHETIC)
                .allMatch(this::supported);
        assertThat(manifest.families())
                .filteredOn(
                        item ->
                                item.classification()
                                        == RealWorldCorpusManifest.Classification
                                                .CONTROLLED_SYNTHETIC)
                .allMatch(item -> RealWorldSyntheticFixtures.supports(item.familyId()));
    }

    static Stream<Arguments> developmentNonPostgresRecipes() {
        return RealWorldCorpusManifest.load().developmentFamilies().stream()
                .filter(
                        item ->
                                item.classification()
                                        != RealWorldCorpusManifest.Classification
                                                .CONTROLLED_SYNTHETIC)
                .filter(item -> !item.fixtureCode().equals("N-PG"))
                .flatMap(item -> Stream.of(Arguments.of(item, 0), Arguments.of(item, 1)));
    }

    static Stream<Arguments> developmentSyntheticRecipes() {
        return RealWorldCorpusManifest.load().developmentFamilies().stream()
                .filter(
                        item ->
                                item.classification()
                                        == RealWorldCorpusManifest.Classification
                                                .CONTROLLED_SYNTHETIC)
                .flatMap(item -> Stream.of(Arguments.of(item, 0), Arguments.of(item, 1)));
    }

    private boolean supported(RealWorldCorpusManifest.Family family) {
        if (family.fixtureCode().equals("N-PG")) {
            return RealWorldPostgresFixtures.supports(family.familyId());
        }
        return RealWorldJavaSpringFixtures.supports(family.familyId())
                || RealWorldMvcInfrastructureFixtures.supports(family.familyId());
    }

    private static Throwable execute(String familyId, int variant) {
        if (RealWorldJavaSpringFixtures.supports(familyId)) {
            return RealWorldJavaSpringFixtures.execute(familyId, variant);
        }
        if (RealWorldMvcInfrastructureFixtures.supports(familyId)) {
            return RealWorldMvcInfrastructureFixtures.execute(familyId, variant);
        }
        throw new IllegalArgumentException("unsupported non-PostgreSQL family: " + familyId);
    }

    private static List<String> causeTypes(Throwable failure) {
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
