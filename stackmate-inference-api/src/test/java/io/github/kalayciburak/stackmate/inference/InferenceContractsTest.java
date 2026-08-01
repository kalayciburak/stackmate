package io.github.kalayciburak.stackmate.inference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InferenceContractsTest {

    @Test
    void requestDefensivelyCopiesEveryCollection() {
        List<InferenceFact> facts = new ArrayList<>(List.of(fact()));
        InferenceRequest request = request(facts, 1_000);

        facts.clear();

        assertThat(request.facts()).containsExactly(fact());
        assertThatThrownBy(() -> request.facts().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void requestRejectsUnknownAndDuplicateReferences() {
        assertThatThrownBy(
                        () ->
                                new InferenceRequest(
                                        "request.one",
                                        InferenceLanguage.ENGLISH,
                                        List.of(
                                                new InferenceFact(
                                                        "fact.one",
                                                        "A fact",
                                                        List.of("src.missing"))),
                                        List.of(),
                                        List.of(),
                                        List.of(location()),
                                        1_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown source location");

        assertThatThrownBy(
                        () ->
                                new InferenceRequest(
                                        "request.one",
                                        InferenceLanguage.ENGLISH,
                                        List.of(fact(), fact()),
                                        List.of(),
                                        List.of(),
                                        List.of(location()),
                                        1_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fact IDs must be unique");
    }

    @Test
    void requestEnforcesStableIdsAndBounds() {
        assertThatThrownBy(() -> new InferenceFact("BAD ID", "fact", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request(List.of(fact()), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                request(
                                        List.of(
                                                new InferenceFact(
                                                        "fact.one", "x".repeat(2_049), List.of())),
                                        1_000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void modelContractsRejectNullAndInvalidLimits() {
        assertThatThrownBy(() -> new ModelCapabilities(true, true, true, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> descriptor(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("capabilities");
        assertThat(
                        new ModelCapabilities(true, true, false, 1, 1)
                                .supports(InferenceLanguage.ENGLISH))
                .isTrue();
    }

    @Test
    void resultSeparatesUntrustedSuccessFromFailure() {
        StructuredInferenceOutput output =
                new StructuredInferenceOutput(
                        InferenceLanguage.ENGLISH, "Summary", List.of(), List.of());
        InferenceResult success = new InferenceResult.Success(output, Duration.ZERO);
        InferenceResult failure =
                new InferenceResult.Failed(
                        new InferenceFailure(
                                InferenceFailure.Kind.UNAVAILABLE, "not loaded", true));

        assertThat(success).isInstanceOf(InferenceResult.Success.class);
        assertThat(failure).isInstanceOf(InferenceResult.Failed.class);
        assertThatThrownBy(() -> new InferenceResult.Success(output, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicEngineContractDoesNotExposeFrameworkOrEngineTypes() {
        for (Method method : LocalInferenceEngine.class.getDeclaredMethods()) {
            assertThat(method.getReturnType().getName())
                    .doesNotContain("jackson", "spring", "jlama");
            for (Class<?> parameter : method.getParameterTypes()) {
                assertThat(parameter.getName()).doesNotContain("jackson", "spring", "jlama");
            }
        }
    }

    private static InferenceRequest request(
            List<InferenceFact> facts, int maximumOutputCharacters) {
        return new InferenceRequest(
                "request.one",
                InferenceLanguage.ENGLISH,
                facts,
                List.of(
                        new InferenceCauseCandidate(
                                "cause.one", "Cause", "Explanation", List.of("fact.one"))),
                List.of(new InferenceCheckCandidate("check.one", "Check it", List.of("fact.one"))),
                List.of(location()),
                maximumOutputCharacters);
    }

    private static InferenceFact fact() {
        return new InferenceFact("fact.one", "Captured failure", List.of("source.one"));
    }

    private static SourceLocation location() {
        return new SourceLocation("source.one", "example.Service", "run", "Service.java", 12);
    }

    private static ModelDescriptor descriptor(ModelCapabilities capabilities) {
        return new ModelDescriptor("model.one", "Model", "1", capabilities);
    }
}
