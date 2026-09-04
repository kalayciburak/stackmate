package io.github.kalayciburak.stackmate.analysis;

import io.github.kalayciburak.stackmate.evidence.EvidenceReliability;
import io.github.kalayciburak.stackmate.evidence.EvidenceSensitivity;
import io.github.kalayciburak.stackmate.evidence.EvidenceType;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import java.util.List;
import java.util.regex.Pattern;

/** Diagnoses Spring dependency-injection and configuration failures by captured type name. */
public final class SpringDependencyConfigurationAnalyzer implements DiagnosticAnalyzer {

    /** Creates the built-in Spring dependency and configuration analyzer. */
    public SpringDependencyConfigurationAnalyzer() {}

    /** Stable identifier used in analyzer tokens and contributions. */
    public static final String ID = "spring-di-config";

    private static final List<Pattern> PROPERTY_PATTERNS =
            List.of(
                    Pattern.compile(
                            "(?i)could not resolve placeholder\\s+['\"]([A-Za-z0-9_.-]{1,128})['\"]"),
                    Pattern.compile(
                            "(?i)failed to bind properties under\\s+['\"]([A-Za-z0-9_.-]{1,128})['\"]"),
                    Pattern.compile(
                            "(?i)(?:invalid property|property)\\s+['\"]([A-Za-z][A-Za-z0-9_.-]{0,127})['\"]"));
    private static final Pattern EXPECTED_TYPE =
            Pattern.compile(
                    "(?i)(?:to (?:required )?type|target type)\\s*[\\['\"]+([A-Za-z_$][A-Za-z0-9_.$]*(?:\\[\\])?)[\\]'\"]+");

    private static final List<TypeRule> TYPE_RULES =
            List.of(
                    new TypeRule(
                            "NoSuchBeanDefinitionException",
                            "cause.spring.missing-bean",
                            DiagnosticCategory.SPRING_DEPENDENCY_INJECTION,
                            "A required Spring bean was not available",
                            "The captured Spring exception type reports a missing bean definition.",
                            "check.spring.bean-registration",
                            "Check component scanning, conditional configuration, qualifiers, and bean declarations."),
                    new TypeRule(
                            "NoUniqueBeanDefinitionException",
                            "cause.spring.ambiguous-bean",
                            DiagnosticCategory.SPRING_DEPENDENCY_INJECTION,
                            "Multiple Spring beans matched one injection point",
                            "The captured Spring exception type reports an ambiguous bean lookup.",
                            "check.spring.bean-qualifier",
                            "Check qualifiers, primary beans, and the injection-point type."),
                    new TypeRule(
                            "UnsatisfiedDependencyException",
                            "cause.spring.unsatisfied-dependency",
                            DiagnosticCategory.SPRING_DEPENDENCY_INJECTION,
                            "A Spring bean dependency could not be satisfied",
                            "The captured Spring exception type reports an unsatisfied dependency.",
                            "check.spring.dependency-chain",
                            "Follow the captured cause chain to the first failed dependency and verify its bean declaration."),
                    new TypeRule(
                            "BeanCurrentlyInCreationException",
                            "cause.spring.circular-dependency",
                            DiagnosticCategory.SPRING_DEPENDENCY_INJECTION,
                            "Spring encountered a bean creation cycle",
                            "The captured Spring exception type reports a bean that is already being created.",
                            "check.spring.dependency-cycle",
                            "Inspect constructor and factory-method dependencies for a cycle."),
                    new TypeRule(
                            "BeanCreationException",
                            "cause.spring.bean-creation",
                            DiagnosticCategory.SPRING_CONFIGURATION,
                            "A Spring bean failed during creation",
                            "The captured Spring exception type reports bean creation failure.",
                            "check.spring.bean-cause",
                            "Inspect the deeper captured cause and the affected bean configuration."),
                    new TypeRule(
                            "BeanDefinitionOverrideException",
                            "cause.spring.bean-definition-conflict",
                            DiagnosticCategory.SPRING_CONFIGURATION,
                            "Spring bean definitions conflicted",
                            "The captured Spring exception type reports a bean definition override conflict.",
                            "check.spring.bean-names",
                            "Check duplicate bean names and configuration imports."),
                    new TypeRule(
                            "ConfigurationPropertiesBindException",
                            "cause.spring.configuration-binding",
                            DiagnosticCategory.SPRING_CONFIGURATION,
                            "Spring configuration properties could not be bound",
                            "The captured Spring exception type reports configuration binding failure.",
                            "check.spring.configuration-properties",
                            "Check property names, value types, active profiles, and configuration sources."),
                    new TypeRule(
                            "PlaceholderResolutionException",
                            "cause.spring.unresolved-placeholder",
                            DiagnosticCategory.SPRING_CONFIGURATION,
                            "A Spring configuration placeholder could not be resolved",
                            "The captured Spring exception type reports placeholder resolution failure.",
                            "check.spring.placeholder",
                            "Check the active property sources and the placeholder key."),
                    new TypeRule(
                            "ConversionFailedException",
                            "cause.spring.configuration-conversion",
                            DiagnosticCategory.SPRING_CONFIGURATION,
                            "A Spring value conversion failed",
                            "The captured Spring exception type reports value conversion failure.",
                            "check.spring.configuration-conversion",
                            "Check the source value, target type, registered converters, and active configuration."),
                    new TypeRule(
                            "ConverterNotFoundException",
                            "cause.spring.converter-not-found",
                            DiagnosticCategory.SPRING_CONFIGURATION,
                            "Spring could not find a required value converter",
                            "The captured Spring exception type reports a missing converter.",
                            "check.spring.configuration-conversion",
                            "Check the source and target types and whether the required converter is registered."),
                    new TypeRule(
                            "TypeMismatchException",
                            "cause.spring.type-mismatch",
                            DiagnosticCategory.SPRING_CONFIGURATION,
                            "A Spring value did not match the required type",
                            "The captured Spring exception type reports a value type mismatch.",
                            "check.spring.configuration-conversion",
                            "Check the configured value format and the declared target type."),
                    new TypeRule(
                            "InvalidPropertyException",
                            "cause.spring.invalid-property",
                            DiagnosticCategory.SPRING_CONFIGURATION,
                            "Spring rejected a bean property",
                            "The captured Spring exception type reports an invalid bean property.",
                            "check.spring.invalid-property",
                            "Check the property name, mutability, accessor methods, and configured value."),
                    new TypeRule(
                            "NotWritablePropertyException",
                            "cause.spring.property-not-writable",
                            DiagnosticCategory.SPRING_CONFIGURATION,
                            "A configured Spring bean property was not writable",
                            "The captured Spring exception type reports a non-writable property.",
                            "check.spring.invalid-property",
                            "Check the property name and its setter or constructor binding."),
                    new TypeRule(
                            "BeanDefinitionStoreException",
                            "cause.spring.bean-definition-processing",
                            DiagnosticCategory.SPRING_CONFIGURATION,
                            "Spring could not process a bean definition",
                            "The captured Spring exception type reports bean-definition processing failure.",
                            "check.spring.bean-definition-processing",
                            "Inspect the deeper cause, configuration class, imports, and conditional declarations."));

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String version() {
        return "2";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public AnalysisContribution analyze(IncidentSnapshot incident) {
        ContributionBuilder contribution = new ContributionBuilder(ID);
        for (AnalyzerSupport.NodeFact fact : AnalyzerSupport.nodes(incident.throwable())) {
            boolean exactMatch = false;
            if (fact.node().type().startsWith("org.springframework.")) {
                for (TypeRule rule : TYPE_RULES) {
                    if (AnalyzerSupport.hasSimpleType(fact.node(), rule.simpleType())) {
                        addTypeMatch(contribution, fact, rule);
                        exactMatch = true;
                        break;
                    }
                }
            }
            if (!exactMatch) {
                addMessagePatternMatches(contribution, fact);
            }
            addStructuredMessageEvidence(contribution, fact);
        }
        return contribution.build();
    }

    private void addTypeMatch(
            ContributionBuilder contribution, AnalyzerSupport.NodeFact fact, TypeRule rule) {
        String evidenceId = contribution.addClassEvidence("spring", fact);
        contribution.addCause(
                rule.causeId(), rule.category(), rule.title(), rule.explanation(), evidenceId);
        contribution.addCheck(rule.checkId(), rule.check(), evidenceId);
    }

    private void addMessagePatternMatches(
            ContributionBuilder contribution, AnalyzerSupport.NodeFact fact) {
        if (AnalyzerSupport.messageContains(fact.node(), "required a bean of type")
                || AnalyzerSupport.messageContains(fact.node(), "could not autowire")) {
            addPattern(
                    contribution,
                    fact,
                    "required-bean-type",
                    "cause.spring.missing-bean-message",
                    DiagnosticCategory.SPRING_DEPENDENCY_INJECTION,
                    "A required Spring bean may be unavailable",
                    "The captured message matches a missing-bean pattern; no exact Spring exception type was captured.",
                    "check.spring.bean-registration",
                    "Check component scanning, conditional configuration, qualifiers, and bean declarations.");
        }
        if (AnalyzerSupport.messageContains(fact.node(), "could not resolve placeholder")) {
            addPattern(
                    contribution,
                    fact,
                    "unresolved-placeholder",
                    "cause.spring.unresolved-placeholder-message",
                    DiagnosticCategory.SPRING_CONFIGURATION,
                    "A configuration placeholder may be unresolved",
                    "The captured message matches an unresolved-placeholder pattern; no exact Spring exception type was captured.",
                    "check.spring.placeholder",
                    "Check the active property sources and the placeholder key.");
        }
        if (AnalyzerSupport.messageContains(fact.node(), "failed to bind properties under")) {
            addPattern(
                    contribution,
                    fact,
                    "configuration-bind-failure",
                    "cause.spring.configuration-binding-message",
                    DiagnosticCategory.SPRING_CONFIGURATION,
                    "Configuration properties may have failed to bind",
                    "The captured message matches a configuration-binding pattern; no exact Spring exception type was captured.",
                    "check.spring.configuration-properties",
                    "Check property names, value types, active profiles, and configuration sources.");
        }
        if (AnalyzerSupport.messageContains(fact.node(), "error processing condition on")
                || AnalyzerSupport.messageContains(fact.node(), "failed to evaluate condition")) {
            addPattern(
                    contribution,
                    fact,
                    "condition-evaluation-failure",
                    "cause.spring.condition-evaluation-message",
                    DiagnosticCategory.SPRING_CONFIGURATION,
                    "A Spring configuration condition may have failed",
                    "The captured message matches a condition-evaluation failure pattern.",
                    "check.spring.condition-evaluation",
                    "Inspect the condition, referenced class or property, active profiles, and deeper cause.");
        }
        if (AnalyzerSupport.messageContains(fact.node(), "failed to convert property value")
                || AnalyzerSupport.messageContains(fact.node(), "cannot convert value of type")) {
            addPattern(
                    contribution,
                    fact,
                    "property-conversion-failure",
                    "cause.spring.property-conversion-message",
                    DiagnosticCategory.SPRING_CONFIGURATION,
                    "A Spring property value may have failed conversion",
                    "The captured message matches a property-conversion failure pattern.",
                    "check.spring.property-conversion-message",
                    "Check the configured value, declared property type, and registered converters.");
        }
    }

    private void addPattern(
            ContributionBuilder contribution,
            AnalyzerSupport.NodeFact fact,
            String patternId,
            String causeId,
            DiagnosticCategory category,
            String title,
            String explanation,
            String checkId,
            String check) {
        String evidenceId = contribution.addMessagePatternEvidence("spring", fact, patternId);
        contribution.addCause(causeId, category, title, explanation, evidenceId);
        contribution.addCheck(checkId, check, evidenceId);
    }

    private void addStructuredMessageEvidence(
            ContributionBuilder contribution, AnalyzerSupport.NodeFact fact) {
        for (Pattern pattern : PROPERTY_PATTERNS) {
            var matcher = pattern.matcher(fact.node().message());
            if (matcher.find()) {
                contribution.addEvidence(
                        "spring.property." + fact.ordinal(),
                        EvidenceType.PROPERTY_NAME,
                        fact.path() + ".message",
                        matcher.group(1),
                        EvidenceSensitivity.POTENTIALLY_SENSITIVE,
                        EvidenceReliability.MEDIUM);
                break;
            }
        }
        var expectedType = EXPECTED_TYPE.matcher(fact.node().message());
        if (expectedType.find()) {
            contribution.addEvidence(
                    "spring.expected-type." + fact.ordinal(),
                    EvidenceType.EXPECTED_TYPE,
                    fact.path() + ".message",
                    expectedType.group(1),
                    EvidenceSensitivity.PUBLIC,
                    EvidenceReliability.MEDIUM);
        }
    }

    private record TypeRule(
            String simpleType,
            String causeId,
            DiagnosticCategory category,
            String title,
            String explanation,
            String checkId,
            String check) {}
}
