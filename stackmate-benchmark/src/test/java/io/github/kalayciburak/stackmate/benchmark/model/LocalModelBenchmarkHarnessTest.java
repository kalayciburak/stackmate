package io.github.kalayciburak.stackmate.benchmark.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.inference.InferenceAvailability;
import io.github.kalayciburak.stackmate.inference.InferenceLanguage;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import io.github.kalayciburak.stackmate.inference.InferenceResult;
import io.github.kalayciburak.stackmate.inference.LocalInferenceEngine;
import io.github.kalayciburak.stackmate.inference.ModelCapabilities;
import io.github.kalayciburak.stackmate.inference.ModelDescriptor;
import io.github.kalayciburak.stackmate.inference.StructuredInferenceOutput;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalModelBenchmarkHarnessTest {

    @Test
    void keepsRawCaseIdentifiersNeutralAndEvaluatesAllModes() throws InterruptedException {
        var engine = new CapturingEngine();

        var result = new LocalModelBenchmarkHarness(engine).evaluate(InferenceLanguage.ENGLISH);

        assertThat(engine.requests).hasSize(100);
        assertThat(engine.requests.subList(0, 99))
                .filteredOn(request -> request.id().startsWith("raw-case-"))
                .extracting(InferenceRequest::id)
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, 50)
                                .mapToObj(index -> "raw-case-" + String.format("%03d", index))
                                .toList());
        assertThat(engine.requests)
                .filteredOn(request -> request.id().startsWith("raw-case-"))
                .allSatisfy(
                        request -> {
                            assertThat(request.candidateChecks()).isEmpty();
                            assertThat(request.facts())
                                    .singleElement()
                                    .satisfies(
                                            fact ->
                                                    assertThat(fact.id())
                                                            .isEqualTo("fact.raw-stack"));
                        });
        assertThat(result.raw().caseCount()).isEqualTo(50);
        assertThat(result.structured().caseCount()).isEqualTo(50);
        assertThat(result.raw().status()).isEqualTo("EXECUTED");
        assertThat(result.structured().status()).isEqualTo("EXECUTED");
        assertThat(result.raw().categoryAccuracy()).containsKey("rate");
        assertThat(result.raw().rootTypeAccuracy()).containsEntry("status", "NOT_APPLICABLE");
        assertThat(result.structured().categoryAccuracy())
                .containsEntry("status", "NOT_APPLICABLE");
        assertThat(result.structured().rootTypeAccuracy())
                .containsEntry("status", "NOT_APPLICABLE");
    }

    @Test
    void canScreenOnlyTheProductionStructuredMode() throws InterruptedException {
        var engine = new CapturingEngine();

        var result =
                new LocalModelBenchmarkHarness(engine)
                        .evaluate(InferenceLanguage.TURKISH, 2, false);

        assertThat(engine.requests).hasSize(2);
        assertThat(engine.requests)
                .allSatisfy(
                        request ->
                                assertThat(request.language())
                                        .isEqualTo(InferenceLanguage.TURKISH));
        assertThat(result.raw().status()).isEqualTo("NOT_EXECUTED");
        assertThat(result.structured().caseCount()).isEqualTo(2);
    }

    private static final class CapturingEngine implements LocalInferenceEngine {
        private final List<InferenceRequest> requests = new ArrayList<>();

        @Override
        public InferenceAvailability availability() {
            return new InferenceAvailability(InferenceAvailability.Status.AVAILABLE, "fake");
        }

        @Override
        public ModelDescriptor model() {
            return new ModelDescriptor(
                    "fake", "Fake", "1", new ModelCapabilities(true, true, true, 16_000, 8_192));
        }

        @Override
        public InferenceResult infer(InferenceRequest request) {
            requests.add(request);
            return new InferenceResult.Success(
                    new StructuredInferenceOutput(
                            "2",
                            request.language(),
                            request.facts().getFirst().value(),
                            "The explanation uses only the supplied facts.",
                            request.candidateCauses().stream()
                                    .limit(1)
                                    .map(cause -> cause.id())
                                    .toList(),
                            request.candidateChecks().stream()
                                    .limit(1)
                                    .map(check -> check.id())
                                    .toList(),
                            List.of(request.facts().getFirst().id()),
                            List.of()),
                    Duration.ofMillis(1));
        }

        @Override
        public void close() {}
    }
}
