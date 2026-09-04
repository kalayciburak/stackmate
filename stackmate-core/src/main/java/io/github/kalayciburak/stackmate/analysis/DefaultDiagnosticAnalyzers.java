package io.github.kalayciburak.stackmate.analysis;

import java.util.List;

/** Creates the ordered built-in deterministic analyzer set. */
public final class DefaultDiagnosticAnalyzers {

    private DefaultDiagnosticAnalyzers() {}

    /**
     * Creates the standard analyzer set.
     *
     * @return a new immutable list of the built-in deterministic analyzers
     */
    public static List<DiagnosticAnalyzer> create() {
        return List.of(
                new GeneralJavaAnalyzer(),
                new SpringDependencyConfigurationAnalyzer(),
                new WebRequestAnalyzer(),
                new DatabaseAnalyzer(),
                new SpringSecurityAnalyzer(),
                new InfrastructureAnalyzer());
    }

    /**
     * Creates the standard diagnosis aggregator.
     *
     * @return an aggregator wired with the built-in analyzers
     */
    public static DiagnosisAggregator aggregator() {
        return new DiagnosisAggregator(create());
    }
}
