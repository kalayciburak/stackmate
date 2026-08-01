package io.github.kalayciburak.stackmate.export;

import io.github.kalayciburak.stackmate.analysis.DeterministicResult;

/** Extension point for optional sanitized incident persistence. */
public interface IncidentExporter {

    /** Exports one deterministic result without exposing implementation exceptions. */
    IncidentExportResult export(DeterministicResult result);
}
