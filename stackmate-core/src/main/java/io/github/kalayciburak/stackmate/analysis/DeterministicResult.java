package io.github.kalayciburak.stackmate.analysis;

import io.github.kalayciburak.stackmate.fingerprint.ExceptionFingerprint;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import java.util.Objects;

/**
 * Evidence-backed deterministic output before rendering, deduplication, or local inference.
 *
 * @param incident immutable incident that was analyzed
 * @param diagnosis deterministic diagnosis produced from the incident
 * @param fingerprint privacy-safe identifier used to correlate equivalent incidents
 */
public record DeterministicResult(
        IncidentSnapshot incident, Diagnosis diagnosis, ExceptionFingerprint fingerprint) {

    /**
     * Creates a complete deterministic result.
     *
     * @param incident immutable incident that was analyzed
     * @param diagnosis deterministic diagnosis produced from the incident
     * @param fingerprint privacy-safe identifier used to correlate equivalent incidents
     */
    public DeterministicResult {
        incident = Objects.requireNonNull(incident, "incident");
        diagnosis = Objects.requireNonNull(diagnosis, "diagnosis");
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
    }
}
