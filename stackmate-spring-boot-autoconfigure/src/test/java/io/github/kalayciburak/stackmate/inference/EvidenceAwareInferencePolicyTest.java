package io.github.kalayciburak.stackmate.inference;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.analysis.DeterministicResult;
import io.github.kalayciburak.stackmate.analysis.DiagnosedCause;
import io.github.kalayciburak.stackmate.analysis.Diagnosis;
import io.github.kalayciburak.stackmate.analysis.DiagnosticCategory;
import io.github.kalayciburak.stackmate.analysis.DiagnosticStrength;
import io.github.kalayciburak.stackmate.configuration.InferenceTriggerPolicy;
import io.github.kalayciburak.stackmate.evidence.EvidenceItem;
import io.github.kalayciburak.stackmate.evidence.EvidenceReliability;
import io.github.kalayciburak.stackmate.evidence.EvidenceSensitivity;
import io.github.kalayciburak.stackmate.evidence.EvidenceType;
import io.github.kalayciburak.stackmate.fingerprint.ExceptionFingerprint;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotter;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceAwareInferencePolicyTest {

    private final EvidenceAwareInferencePolicy policy = new EvidenceAwareInferencePolicy();

    @Test
    void runsOnlyForNonFallbackMediumEvidenceByDefault() {
        assertThat(
                        policy.shouldInfer(
                                result(DiagnosticStrength.MEDIUM, false),
                                InferenceTriggerPolicy.WHEN_ENHANCEMENT_IS_USEFUL))
                .isTrue();
        assertThat(
                        policy.shouldInfer(
                                result(DiagnosticStrength.HIGH, false),
                                InferenceTriggerPolicy.WHEN_ENHANCEMENT_IS_USEFUL))
                .isFalse();
        assertThat(
                        policy.shouldInfer(
                                result(DiagnosticStrength.LOW, false),
                                InferenceTriggerPolicy.WHEN_ENHANCEMENT_IS_USEFUL))
                .isFalse();
        assertThat(
                        policy.shouldInfer(
                                result(DiagnosticStrength.UNKNOWN, false),
                                InferenceTriggerPolicy.WHEN_ENHANCEMENT_IS_USEFUL))
                .isFalse();
        assertThat(
                        policy.shouldInfer(
                                result(DiagnosticStrength.MEDIUM, true),
                                InferenceTriggerPolicy.WHEN_ENHANCEMENT_IS_USEFUL))
                .isFalse();
    }

    @Test
    void honorsExplicitAlwaysAndNeverPolicies() {
        assertThat(
                        policy.shouldInfer(
                                result(DiagnosticStrength.UNKNOWN, true),
                                InferenceTriggerPolicy.ALWAYS))
                .isTrue();
        assertThat(
                        policy.shouldInfer(
                                result(DiagnosticStrength.MEDIUM, false),
                                InferenceTriggerPolicy.NEVER))
                .isFalse();
    }

    private static DeterministicResult result(DiagnosticStrength strength, boolean fallback) {
        var evidence =
                new EvidenceItem(
                        "fact.one",
                        EvidenceType.MESSAGE_PATTERN,
                        "policy-test",
                        "bounded pattern",
                        EvidenceSensitivity.PUBLIC,
                        EvidenceReliability.MEDIUM);
        var cause =
                new DiagnosedCause(
                        "cause.one",
                        DiagnosticCategory.GENERAL_JAVA,
                        "Known deterministic candidate",
                        "The candidate is supplied by the deterministic engine.",
                        strength,
                        List.of(evidence.id()));
        var failure = ThrowableSnapshotter.defaults().capture(new RuntimeException("test"));
        var incident = IncidentSnapshot.of("capture-1", Instant.EPOCH, failure, List.of());
        var diagnosis =
                new Diagnosis(
                        List.of(evidence),
                        List.of(cause),
                        List.of(),
                        List.of("policy-test@1"),
                        fallback);
        return new DeterministicResult(
                incident, diagnosis, new ExceptionFingerprint("a".repeat(64)));
    }
}
