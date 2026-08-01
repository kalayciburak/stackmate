package io.github.kalayciburak.stackmate.analysis;

import io.github.kalayciburak.stackmate.evidence.EvidenceItem;
import io.github.kalayciburak.stackmate.fingerprint.FingerprintInput;
import io.github.kalayciburak.stackmate.fingerprint.IncidentFingerprinter;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.incident.StackFrameSnapshot;
import java.util.Objects;

/** Coordinates deterministic analysis and privacy-safe fingerprint construction. */
public final class DeterministicDiagnosticEngine {

    private final DiagnosisAggregator diagnosisAggregator;
    private final IncidentFingerprinter incidentFingerprinter;

    public DeterministicDiagnosticEngine(
            DiagnosisAggregator diagnosisAggregator, IncidentFingerprinter incidentFingerprinter) {
        this.diagnosisAggregator =
                Objects.requireNonNull(diagnosisAggregator, "diagnosisAggregator");
        this.incidentFingerprinter =
                Objects.requireNonNull(incidentFingerprinter, "incidentFingerprinter");
    }

    public DeterministicResult analyze(IncidentSnapshot incident) {
        Objects.requireNonNull(incident, "incident");
        Diagnosis diagnosis = diagnosisAggregator.diagnose(incident);
        DiagnosedCause primaryCause = diagnosis.causes().getFirst();
        String analyzerVersion =
                diagnosis.analyzerTokens().isEmpty()
                        ? "none"
                        : String.join(",", diagnosis.analyzerTokens());
        FingerprintInput input =
                new FingerprintInput(
                        analyzerVersion,
                        incident.throwable().root().type(),
                        primaryCause.category().name(),
                        incident.throwable().root().message(),
                        incident.applicationFrames().stream()
                                .limit(FingerprintInput.MAX_APPLICATION_FRAMES)
                                .map(DeterministicDiagnosticEngine::frameIdentity)
                                .toList(),
                        diagnosis.evidence().stream()
                                .map(EvidenceItem::type)
                                .map(Enum::name)
                                .distinct()
                                .limit(FingerprintInput.MAX_STABLE_FACT_TYPES)
                                .toList());
        return new DeterministicResult(
                incident, diagnosis, incidentFingerprinter.fingerprint(input));
    }

    private static String frameIdentity(StackFrameSnapshot frame) {
        return frame.className() + '#' + frame.methodName();
    }
}
