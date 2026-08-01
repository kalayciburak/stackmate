package io.github.kalayciburak.stackmate.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.analysis.AnalysisContribution;
import io.github.kalayciburak.stackmate.analysis.DiagnosisAggregator;
import io.github.kalayciburak.stackmate.analysis.DiagnosticAnalyzer;
import io.github.kalayciburak.stackmate.capture.ServletExceptionCaptureHandler;
import io.github.kalayciburak.stackmate.configuration.ProductionProfileGuard;
import io.github.kalayciburak.stackmate.configuration.StackMateProperties;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.inference.InferenceAvailability;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import io.github.kalayciburak.stackmate.inference.InferenceResult;
import io.github.kalayciburak.stackmate.inference.LocalInferenceEngine;
import io.github.kalayciburak.stackmate.inference.LocalInferenceMetrics;
import io.github.kalayciburak.stackmate.inference.ModelCapabilities;
import io.github.kalayciburak.stackmate.inference.ModelDescriptor;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import io.github.kalayciburak.stackmate.privacy.Sanitizer;
import io.github.kalayciburak.stackmate.runtime.AsyncLocalInferenceCoordinator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;

class StackMateAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(StackMateAutoConfiguration.class));

    private final WebApplicationContextRunner inferenceContextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    StackMateAutoConfiguration.class,
                                    StackMateJvmInferenceAutoConfiguration.class,
                                    StackMateInferenceAutoConfiguration.class));

    @Test
    void should_enable_deterministic_servlet_diagnostics_with_safe_defaults() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(StackMateProperties.class);
                    assertThat(context).hasSingleBean(ServletExceptionCaptureHandler.class);
                    assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                    assertThat(context).hasSingleBean(DiagnosisAggregator.class);
                    assertThat(context.getBean(StackMateProperties.class).getMode())
                            .isEqualTo(StackMateProperties.Mode.DETERMINISTIC);
                });
    }

    @Test
    void should_disable_every_stackmate_bean_when_root_switch_is_false() {
        contextRunner
                .withPropertyValues("stackmate.enabled=false")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StackMateProperties.class);
                            assertThat(context)
                                    .doesNotHaveBean(ServletExceptionCaptureHandler.class);
                            assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
                        });
    }

    @Test
    void should_back_off_without_spring_mvc() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(DispatcherServlet.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StackMateProperties.class);
                            assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
                        });
    }

    @Test
    void should_honor_user_sanitizer_and_custom_analyzer_beans() {
        contextRunner
                .withUserConfiguration(UserExtensions.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(Sanitizer.class);
                            assertThat(context.getBean(Sanitizer.class))
                                    .isSameAs(context.getBean("customSanitizer"));
                            assertThat(context.getBean(DiagnosisAggregator.class).analyzers())
                                    .extracting(DiagnosticAnalyzer::id)
                                    .contains("sample-custom");
                        });
    }

    @Test
    void should_fail_context_early_for_invalid_bounded_properties() {
        contextRunner
                .withPropertyValues("stackmate.console.maximum-width=10")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void should_disable_analysis_when_known_production_profile_is_active() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .run(
                        context -> {
                            ProductionProfileGuard.Decision decision =
                                    context.getBean(ProductionProfileGuard.class).decision();
                            assertThat(decision.analysisAllowed()).isFalse();
                            assertThat(decision.warningRequired()).isTrue();
                            assertThat(decision.matchedProductionProfiles())
                                    .containsExactly("prod");
                        });
    }

    @Test
    void should_start_without_any_local_inference_engine() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ServletExceptionCaptureHandler.class);
                    assertThat(context).doesNotHaveBean("localInferenceEngine");
                });
    }

    @Test
    void should_create_lazy_hybrid_coordinator_with_or_without_local_engine() {
        inferenceContextRunner
                .withPropertyValues(
                        "stackmate.mode=HYBRID",
                        "stackmate.inference.model-path=missing-local-model")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(LocalInferenceEngine.class);
                            assertThat(context.getBean(LocalInferenceEngine.class))
                                    .isInstanceOf(
                                            io.github.kalayciburak.stackmate.inference
                                                    .LazyLocalInferenceEngine.class);
                        });

        inferenceContextRunner
                .withPropertyValues("stackmate.mode=HYBRID")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(AsyncLocalInferenceCoordinator.class);
                            assertThat(context).hasSingleBean(LocalInferenceMetrics.class);
                            assertThat(context).hasSingleBean(LocalInferenceEngine.class);
                        });

        inferenceContextRunner
                .withPropertyValues("stackmate.mode=HYBRID")
                .withUserConfiguration(LocalEngineConfiguration.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(LocalInferenceEngine.class);
                            assertThat(context).hasSingleBean(AsyncLocalInferenceCoordinator.class);
                        });
    }

    @Test
    void should_not_create_inference_beans_when_disabled_or_api_is_absent() {
        inferenceContextRunner
                .withPropertyValues("stackmate.mode=HYBRID", "stackmate.inference.enabled=false")
                .run(
                        context ->
                                assertThat(context)
                                        .doesNotHaveBean(AsyncLocalInferenceCoordinator.class));

        inferenceContextRunner
                .withPropertyValues("stackmate.enabled=false", "stackmate.mode=HYBRID")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StackMateProperties.class);
                            assertThat(context)
                                    .doesNotHaveBean(AsyncLocalInferenceCoordinator.class);
                        });

        inferenceContextRunner
                .withClassLoader(new FilteredClassLoader(LocalInferenceEngine.class))
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(ServletExceptionCaptureHandler.class);
                            assertThat(context)
                                    .doesNotHaveBean(AsyncLocalInferenceCoordinator.class);
                        });
    }

    @Configuration(proxyBeanMethods = false)
    static class UserExtensions {

        @Bean
        Sanitizer customSanitizer() {
            return DefaultSanitizer.defaults();
        }

        @Bean
        DiagnosticAnalyzer sampleCustomAnalyzer() {
            return new DiagnosticAnalyzer() {
                @Override
                public String id() {
                    return "sample-custom";
                }

                @Override
                public int order() {
                    return 5;
                }

                @Override
                public AnalysisContribution analyze(IncidentSnapshot incident) {
                    return new AnalysisContribution(id(), List.of(), List.of(), List.of());
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LocalEngineConfiguration {

        @Bean
        LocalInferenceEngine localInferenceEngine() {
            return new LocalInferenceEngine() {
                @Override
                public InferenceAvailability availability() {
                    return InferenceAvailability.available();
                }

                @Override
                public ModelDescriptor model() {
                    return new ModelDescriptor(
                            "test/local",
                            "Test Local",
                            "1",
                            new ModelCapabilities(true, true, true, 16_000, 8_192));
                }

                @Override
                public InferenceResult infer(InferenceRequest request) {
                    throw new AssertionError("auto-configuration must stay lazy");
                }

                @Override
                public void close() {}
            };
        }
    }
}
