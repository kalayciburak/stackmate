package io.github.kalayciburak.stackmate.benchmark.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.inference.InferenceAvailability;
import io.github.kalayciburak.stackmate.inference.InferenceLanguage;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import io.github.kalayciburak.stackmate.inference.InferenceResult;
import io.github.kalayciburak.stackmate.inference.LocalInferenceEngine;
import io.github.kalayciburak.stackmate.inference.ModelCapabilities;
import io.github.kalayciburak.stackmate.inference.ModelDescriptor;
import io.github.kalayciburak.stackmate.inference.StructuredCause;
import io.github.kalayciburak.stackmate.inference.StructuredCheck;
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
                            assertThat(request.checks()).isEmpty();
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
            List<StructuredCause> causes =
                    request.causes().isEmpty()
                            ? List.of()
                            : List.of(
                                    new StructuredCause(
                                            request.causes().getFirst().id(),
                                            request.causes().getFirst().explanation(),
                                            request.causes().getFirst().factIds(),
                                            List.of()));
            List<StructuredCheck> checks =
                    request.checks().isEmpty()
                            ? List.of()
                            : List.of(
                                    new StructuredCheck(
                                            request.checks().getFirst().id(),
                                            request.checks().getFirst().instruction(),
                                            request.checks().getFirst().factIds(),
                                            List.of()));
            return new InferenceResult.Success(
                    new StructuredInferenceOutput(
                            request.language(), request.facts().getFirst().text(), causes, checks),
                    Duration.ofMillis(1));
        }

        @Override
        public void close() {}
    }
}
