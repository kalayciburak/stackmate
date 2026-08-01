package io.github.kalayciburak.stackmate.autoconfigure;

import io.github.kalayciburak.stackmate.analysis.DatabaseAnalyzer;
import io.github.kalayciburak.stackmate.analysis.DeterministicDiagnosticEngine;
import io.github.kalayciburak.stackmate.analysis.DiagnosisAggregator;
import io.github.kalayciburak.stackmate.analysis.DiagnosticAnalyzer;
import io.github.kalayciburak.stackmate.analysis.GeneralJavaAnalyzer;
import io.github.kalayciburak.stackmate.analysis.InfrastructureAnalyzer;
import io.github.kalayciburak.stackmate.analysis.SpringDependencyConfigurationAnalyzer;
import io.github.kalayciburak.stackmate.analysis.SpringSecurityAnalyzer;
import io.github.kalayciburak.stackmate.analysis.WebRequestAnalyzer;
import io.github.kalayciburak.stackmate.capture.ServletExceptionCaptureHandler;
import io.github.kalayciburak.stackmate.capture.StackMateExceptionCaptureFilter;
import io.github.kalayciburak.stackmate.configuration.ProductionProfileGuard;
import io.github.kalayciburak.stackmate.configuration.StackMateProperties;
import io.github.kalayciburak.stackmate.context.ServletContextExtractor;
import io.github.kalayciburak.stackmate.deduplication.BoundedInMemoryDeduplicationStore;
import io.github.kalayciburak.stackmate.deduplication.DeduplicationStore;
import io.github.kalayciburak.stackmate.fingerprint.IncidentFingerprinter;
import io.github.kalayciburak.stackmate.output.ConsoleRenderer;
import io.github.kalayciburak.stackmate.output.DefaultConsoleRenderer;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import io.github.kalayciburak.stackmate.privacy.Sanitizer;
import io.github.kalayciburak.stackmate.privacy.SanitizerConfig;
import io.github.kalayciburak.stackmate.privacy.SanitizerPattern;
import io.github.kalayciburak.stackmate.runtime.DefaultServletExceptionCaptureHandler;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotLimits;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotter;
import jakarta.servlet.Servlet;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.DispatcherServlet;

/** Servlet-only auto-configuration for deterministic StackMate diagnostics. */
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({Servlet.class, DispatcherServlet.class})
@ConditionalOnProperty(prefix = "stackmate", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(StackMateProperties.class)
public class StackMateAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Sanitizer stackMateSanitizer(StackMateProperties properties) {
        SanitizerConfig defaults = SanitizerConfig.defaults();
        List<SanitizerPattern> customPatterns =
                properties.getPrivacy().getCustomRedactionPatterns().stream()
                        .map(
                                value ->
                                        new SanitizerPattern(
                                                Pattern.compile(value), defaults.replacement()))
                        .toList();
        boolean redactPii = properties.getPrivacy().isPiiRedaction();
        SanitizerConfig config =
                new SanitizerConfig(
                        properties.getDiagnostics().getMaximumMessageLength(),
                        defaults.replacement(),
                        redactPii,
                        redactPii,
                        redactPii,
                        defaults.sensitiveFieldNames(),
                        customPatterns);
        return new DefaultSanitizer(config);
    }

    @Bean
    @ConditionalOnMissingBean
    ThrowableSnapshotter stackMateThrowableSnapshotter(
            StackMateProperties properties, Sanitizer sanitizer) {
        var diagnostics = properties.getDiagnostics();
        int maximumNodes =
                Math.min(
                        512,
                        diagnostics.getMaximumCauseDepth()
                                * Math.max(1, diagnostics.getMaximumSuppressedExceptions() + 1));
        ThrowableSnapshotLimits limits =
                new ThrowableSnapshotLimits(
                        diagnostics.getMaximumCauseDepth(),
                        diagnostics.getMaximumStackFrames(),
                        diagnostics.getMaximumSuppressedExceptions(),
                        maximumNodes,
                        diagnostics.getMaximumMessageLength(),
                        8);
        return new ThrowableSnapshotter(limits, sanitizer);
    }

    @Bean
    @ConditionalOnMissingBean(GeneralJavaAnalyzer.class)
    GeneralJavaAnalyzer stackMateGeneralJavaAnalyzer() {
        return new GeneralJavaAnalyzer();
    }

    @Bean
    @ConditionalOnMissingBean(SpringDependencyConfigurationAnalyzer.class)
    SpringDependencyConfigurationAnalyzer stackMateSpringConfigurationAnalyzer() {
        return new SpringDependencyConfigurationAnalyzer();
    }

    @Bean
    @ConditionalOnMissingBean(WebRequestAnalyzer.class)
    WebRequestAnalyzer stackMateWebRequestAnalyzer() {
        return new WebRequestAnalyzer();
    }

    @Bean
    @ConditionalOnMissingBean(DatabaseAnalyzer.class)
    DatabaseAnalyzer stackMateDatabaseAnalyzer() {
        return new DatabaseAnalyzer();
    }

    @Bean
    @ConditionalOnMissingBean(SpringSecurityAnalyzer.class)
    SpringSecurityAnalyzer stackMateSpringSecurityAnalyzer() {
        return new SpringSecurityAnalyzer();
    }

    @Bean
    @ConditionalOnMissingBean(InfrastructureAnalyzer.class)
    InfrastructureAnalyzer stackMateInfrastructureAnalyzer() {
        return new InfrastructureAnalyzer();
    }

    @Bean
    @ConditionalOnMissingBean
    DiagnosisAggregator stackMateDiagnosisAggregator(ObjectProvider<DiagnosticAnalyzer> analyzers) {
        return new DiagnosisAggregator(analyzers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    IncidentFingerprinter stackMateIncidentFingerprinter() {
        return new IncidentFingerprinter();
    }

    @Bean
    @ConditionalOnMissingBean
    DeterministicDiagnosticEngine stackMateDeterministicDiagnosticEngine(
            DiagnosisAggregator aggregator, IncidentFingerprinter fingerprinter) {
        return new DeterministicDiagnosticEngine(aggregator, fingerprinter);
    }

    @Bean
    @ConditionalOnMissingBean
    DeduplicationStore stackMateDeduplicationStore(StackMateProperties properties) {
        return new BoundedInMemoryDeduplicationStore(
                properties.getDeduplication().getMaximumEntries(),
                properties.getDeduplication().getTtl());
    }

    @Bean
    @ConditionalOnMissingBean
    ConsoleRenderer stackMateConsoleRenderer(Sanitizer sanitizer) {
        return new DefaultConsoleRenderer(sanitizer);
    }

    @Bean
    @ConditionalOnMissingBean
    ProductionProfileGuard stackMateProductionProfileGuard(
            Environment environment, StackMateProperties properties) {
        return new ProductionProfileGuard(environment, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    ServletContextExtractor stackMateServletContextExtractor(
            Environment environment,
            BeanFactory beanFactory,
            StackMateProperties properties,
            Sanitizer sanitizer) {
        return new ServletContextExtractor(environment, beanFactory, properties, sanitizer);
    }

    @Bean
    @ConditionalOnMissingBean(ServletExceptionCaptureHandler.class)
    ServletExceptionCaptureHandler stackMateServletExceptionCaptureHandler(
            ThrowableSnapshotter snapshotter,
            DeterministicDiagnosticEngine diagnosticEngine,
            DeduplicationStore deduplicationStore,
            ConsoleRenderer consoleRenderer,
            StackMateProperties properties,
            ProductionProfileGuard productionGuard) {
        return new DefaultServletExceptionCaptureHandler(
                snapshotter,
                diagnosticEngine,
                deduplicationStore,
                consoleRenderer,
                properties,
                productionGuard);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "stackmate.capture",
            name = "mvc-unhandled",
            havingValue = "true",
            matchIfMissing = true)
    FilterRegistrationBean<StackMateExceptionCaptureFilter> stackMateCaptureFilter(
            ServletContextExtractor contextExtractor,
            ServletExceptionCaptureHandler captureHandler) {
        var registration = new FilterRegistrationBean<StackMateExceptionCaptureFilter>();
        registration.setFilter(
                new StackMateExceptionCaptureFilter(contextExtractor, captureHandler));
        registration.setName("stackMateExceptionCaptureFilter");
        registration.setDispatcherTypes(jakarta.servlet.DispatcherType.REQUEST);
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return registration;
    }
}
