package io.github.kalayciburak.stackmate.analysis;

import java.util.List;

/** Creates the ordered built-in deterministic analyzer set. */
public final class DefaultDiagnosticAnalyzers {

    private DefaultDiagnosticAnalyzers() {}

    public static List<DiagnosticAnalyzer> create() {
        return List.of(
                new GeneralJavaAnalyzer(),
                new SpringDependencyConfigurationAnalyzer(),
                new WebRequestAnalyzer(),
                new DatabaseAnalyzer(),
                new SpringSecurityAnalyzer(),
                new InfrastructureAnalyzer());
    }

    public static DiagnosisAggregator aggregator() {
        return new DiagnosisAggregator(create());
    }
}
