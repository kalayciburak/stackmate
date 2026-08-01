package io.github.kalayciburak.stackmate.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.fingerprint.IncidentFingerprinter;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.incident.StackFrameSnapshot;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotLimits;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotter;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicDiagnosticEngineTest {

    @Test
    void should_produce_same_fingerprint_for_volatile_message_values() {
        DeterministicDiagnosticEngine engine =
                new DeterministicDiagnosticEngine(
                        new DiagnosisAggregator(DefaultDiagnosticAnalyzers.create()),
                        new IncidentFingerprinter());

        DeterministicResult first =
                engine.analyze(
                        incident(
                                "STM-ONE",
                                new IllegalArgumentException(
                                        "invalid account 123456 at 2026-08-01T09:00:00Z")));
        DeterministicResult second =
                engine.analyze(
                        incident(
                                "STM-TWO",
                                new IllegalArgumentException(
                                        "invalid account 987654 at 2026-08-01T10:30:00Z")));

        assertThat(first.diagnosis().causes().getFirst().category())
                .isEqualTo(DiagnosticCategory.GENERAL_JAVA);
        assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
    }

    private IncidentSnapshot incident(String id, Throwable throwable) {
        ThrowableSnapshotter snapshotter =
                new ThrowableSnapshotter(
                        ThrowableSnapshotLimits.defaults(), DefaultSanitizer.defaults());
        StackFrameSnapshot applicationFrame =
                new StackFrameSnapshot(
                        "com.example.UserService",
                        "create",
                        "UserService.java",
                        42,
                        false,
                        "",
                        "",
                        "",
                        0);
        return IncidentSnapshot.of(
                id,
                Instant.parse("2026-08-01T00:00:00Z"),
                snapshotter.capture(throwable),
                List.of(applicationFrame));
    }
}
