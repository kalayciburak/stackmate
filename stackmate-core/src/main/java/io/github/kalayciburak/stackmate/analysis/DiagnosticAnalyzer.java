package io.github.kalayciburak.stackmate.analysis;

import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;

/**
 * Ordered stateless extension point for deterministic diagnostics.
 *
 * <p>Implementations must keep all invocation state local to {@link #analyze(IncidentSnapshot)}.
 */
public interface DiagnosticAnalyzer {

    String id();

    default String version() {
        return "1";
    }

    int order();

    AnalysisContribution analyze(IncidentSnapshot incident);
}
