package io.github.kalayciburak.stackmate.analysis;

import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;

/**
 * Ordered stateless extension point for deterministic diagnostics.
 *
 * <p>Implementations must keep all invocation state local to {@link #analyze(IncidentSnapshot)}.
 */
public interface DiagnosticAnalyzer {

    /**
     * Identifies the analyzer in contributions and version tokens.
     *
     * @return the stable lowercase identifier of this analyzer
     */
    String id();

    /**
     * Identifies the revision of the analyzer's deterministic rules.
     *
     * @return the stable version token for this analyzer's deterministic rules
     */
    default String version() {
        return "1";
    }

    /**
     * Defines this analyzer's position in deterministic execution order.
     *
     * @return deterministic execution order; lower values run first
     */
    int order();

    /**
     * Analyzes one immutable incident without mutating shared state.
     *
     * @param incident incident snapshot to analyze
     * @return evidence and bounded candidates produced by this analyzer
     */
    AnalysisContribution analyze(IncidentSnapshot incident);
}
