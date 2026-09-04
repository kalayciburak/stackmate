package io.github.kalayciburak.stackmate.export;

import io.github.kalayciburak.stackmate.analysis.DeterministicResult;

/** Extension point for optional sanitized incident persistence. */
public interface IncidentExporter {

    /**
     * Exports one deterministic result without exposing implementation exceptions.
     *
     * @param result sanitized deterministic result to export
     * @return bounded success, rejection, or failure outcome
     */
    IncidentExportResult export(DeterministicResult result);
}
