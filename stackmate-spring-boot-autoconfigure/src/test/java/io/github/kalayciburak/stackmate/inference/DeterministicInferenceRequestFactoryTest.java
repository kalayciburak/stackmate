package io.github.kalayciburak.stackmate.inference;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.analysis.DefaultDiagnosticAnalyzers;
import io.github.kalayciburak.stackmate.analysis.DeterministicDiagnosticEngine;
import io.github.kalayciburak.stackmate.fingerprint.IncidentFingerprinter;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotter;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicInferenceRequestFactoryTest {

    @Test
    void creates_bounded_sanitized_request_using_only_stable_referenceable_inputs() {
        NullPointerException failure =
                new NullPointerException("password=private-value query=must-not-leak");
        failure.setStackTrace(
                new StackTraceElement[] {
                    new StackTraceElement("example.OrderService", "create", "OrderService.java", 42)
                });
        var throwable = ThrowableSnapshotter.defaults().capture(failure);
        var incident =
                IncidentSnapshot.of(
                        "capture-1",
                        Instant.now(),
                        throwable,
                        List.of(throwable.root().frames().getFirst()));
        var result =
                new DeterministicDiagnosticEngine(
                                DefaultDiagnosticAnalyzers.aggregator(),
                                new IncidentFingerprinter())
                        .analyze(incident);
        DeterministicInferenceRequestFactory factory =
                new DeterministicInferenceRequestFactory(DefaultSanitizer.defaults());

        InferenceRequest request = factory.create(result, 300, 512);

        assertThat(totalCharacters(request)).isLessThanOrEqualTo(300);
        assertThat(request.schemaVersion()).isEqualTo("2");
        assertThat(request.promptVersion()).isEqualTo("2");
        assertThat(request.incident().category())
                .isEqualTo(result.diagnosis().causes().getFirst().category());
        assertThat(request.incident().evidenceStrength())
                .isEqualTo(result.diagnosis().causes().getFirst().strength());
        assertThat(request.maximumOutputCharacters()).isEqualTo(512);
        assertThat(request.facts()).isNotEmpty();
        assertThat(request.facts())
                .extracting(InferenceFact::id)
                .allMatch(id -> id.matches("[a-z0-9._-]+"));
        assertThat(request.facts())
                .filteredOn(fact -> fact.type().equals("APPLICATION_LOCATION"))
                .extracting(InferenceFact::value)
                .contains("example.OrderService#create(OrderService.java:42)");
        assertThat(request.candidateCauses())
                .allSatisfy(
                        cause ->
                                assertThat(request.facts())
                                        .extracting(InferenceFact::id)
                                        .containsAll(cause.evidenceIds()));
        assertThat(request.candidateChecks())
                .allSatisfy(
                        check ->
                                assertThat(request.facts())
                                        .extracting(InferenceFact::id)
                                        .containsAll(check.evidenceIds()));
        assertThat(request.toString())
                .doesNotContain("private-value")
                .doesNotContain("headers")
                .doesNotContain("environment");
    }

    private static int totalCharacters(InferenceRequest request) {
        int total =
                request.facts().stream()
                        .mapToInt(
                                fact ->
                                        fact.id().length()
                                                + fact.type().length()
                                                + fact.value().length())
                        .sum();
        total +=
                request.candidateCauses().stream()
                        .mapToInt(cause -> cause.id().length() + cause.text().length())
                        .sum();
        total +=
                request.candidateChecks().stream()
                        .mapToInt(check -> check.id().length() + check.text().length())
                        .sum();
        total +=
                request.terminology().stream()
                        .mapToInt(term -> term.term().length() + term.definition().length())
                        .sum();
        return total;
    }
}
