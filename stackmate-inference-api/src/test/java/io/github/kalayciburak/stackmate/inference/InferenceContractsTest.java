package io.github.kalayciburak.stackmate.inference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kalayciburak.stackmate.analysis.DiagnosticCategory;
import io.github.kalayciburak.stackmate.analysis.DiagnosticStrength;
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
    void requestRejectsUnknownDuplicateAndWrongSchemaReferences() {
        assertThatThrownBy(
                        () ->
                                new InferenceRequest(
                                        "1",
                                        InferenceRequest.PROMPT_VERSION,
                                        "request.one",
                                        incident(),
                                        List.of(fact()),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        1_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion");

        assertThatThrownBy(
                        () ->
                                new InferenceRequest(
                                        InferenceRequest.SCHEMA_VERSION,
                                        InferenceRequest.PROMPT_VERSION,
                                        "request.one",
                                        incident(),
                                        List.of(fact(), fact()),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        1_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fact IDs must be unique");

        assertThatThrownBy(
                        () ->
                                request(
                                        List.of(fact()),
                                        List.of(
                                                new InferenceCauseCandidate(
                                                        "cause.one",
                                                        "Cause",
                                                        List.of("fact.missing")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown cause evidence");
    }

    @Test
    void requestEnforcesStableIdsFactTypesAndBounds() {
        assertThatThrownBy(() -> new InferenceFact("BAD ID", "SQL_STATE", "23505"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InferenceFact("fact.one", "sql state", "23505"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request(List.of(fact()), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                request(
                                        List.of(
                                                new InferenceFact(
                                                        "fact.one",
                                                        "SANITIZED_MESSAGE",
                                                        "x".repeat(2_049))),
                                        1_000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void modelContractsRejectNullAndInvalidLimits() {
        assertThatThrownBy(() -> new ModelCapabilities(true, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> descriptor(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("capabilities");
        assertThat(new ModelCapabilities(true, 1, 1).structuredOutput()).isTrue();
    }

    @Test
    void resultSeparatesUntrustedSuccessFromFailure() {
        StructuredInferenceOutput output =
                new StructuredInferenceOutput(
                        "2",
                        "Summary",
                        "Explanation",
                        List.of(),
                        List.of(),
                        List.of("fact.one"),
                        List.of());
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
                InferenceRequest.SCHEMA_VERSION,
                InferenceRequest.PROMPT_VERSION,
                "request.one",
                incident(),
                facts,
                List.of(new InferenceCauseCandidate("cause.one", "Cause", List.of("fact.one"))),
                List.of(new InferenceCheckCandidate("check.one", "Check it", List.of("fact.one"))),
                List.of(new InferenceTerminology("constraint", "A database integrity rule")),
                maximumOutputCharacters);
    }

    private static InferenceRequest request(
            List<InferenceFact> facts, List<InferenceCauseCandidate> causes) {
        return new InferenceRequest(
                InferenceRequest.SCHEMA_VERSION,
                InferenceRequest.PROMPT_VERSION,
                "request.one",
                incident(),
                facts,
                causes,
                List.of(),
                List.of(),
                1_000);
    }

    private static InferenceFact fact() {
        return new InferenceFact("fact.one", "SQL_STATE", "23505");
    }

    private static InferenceIncident incident() {
        return new InferenceIncident(
                DiagnosticCategory.DATABASE_CONSTRAINT, DiagnosticStrength.HIGH);
    }

    private static ModelDescriptor descriptor(ModelCapabilities capabilities) {
        return new ModelDescriptor("model.one", "Model", "1", capabilities);
    }
}
