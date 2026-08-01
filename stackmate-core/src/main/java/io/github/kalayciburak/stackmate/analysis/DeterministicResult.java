package io.github.kalayciburak.stackmate.analysis;

import io.github.kalayciburak.stackmate.fingerprint.ExceptionFingerprint;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import java.util.Objects;

/** Evidence-backed deterministic output before rendering, deduplication, or local inference. */
public record DeterministicResult(
        IncidentSnapshot incident, Diagnosis diagnosis, ExceptionFingerprint fingerprint) {

    public DeterministicResult {
        incident = Objects.requireNonNull(incident, "incident");
        diagnosis = Objects.requireNonNull(diagnosis, "diagnosis");
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
    }
}
