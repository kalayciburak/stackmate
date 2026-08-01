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

        InferenceRequest request = factory.create(result, InferenceLanguage.TURKISH, 300, 512);

        assertThat(totalCharacters(request)).isLessThanOrEqualTo(300);
        assertThat(request.language()).isEqualTo(InferenceLanguage.TURKISH);
        assertThat(request.maximumOutputCharacters()).isEqualTo(512);
        assertThat(request.facts()).isNotEmpty();
        assertThat(request.facts())
                .extracting(InferenceFact::id)
                .allMatch(id -> id.matches("[a-z0-9._-]+"));
        assertThat(request.sourceLocations())
                .extracting(SourceLocation::id)
                .allMatch(id -> id.matches("source\\.app\\.\\d+"));
        assertThat(request.toString())
                .doesNotContain("private-value")
                .doesNotContain("headers")
                .doesNotContain("environment");
    }

    private static int totalCharacters(InferenceRequest request) {
        int total =
                request.facts().stream()
                        .mapToInt(fact -> fact.id().length() + fact.text().length())
                        .sum();
        total +=
                request.causes().stream()
                        .mapToInt(
                                cause ->
                                        cause.id().length()
                                                + cause.title().length()
                                                + cause.explanation().length())
                        .sum();
        total +=
                request.checks().stream()
                        .mapToInt(check -> check.id().length() + check.instruction().length())
                        .sum();
        total +=
                request.sourceLocations().stream()
                        .mapToInt(
                                source ->
                                        source.id().length()
                                                + source.className().length()
                                                + source.methodName().length()
                                                + source.fileName().length())
                        .sum();
        return total;
    }
}
