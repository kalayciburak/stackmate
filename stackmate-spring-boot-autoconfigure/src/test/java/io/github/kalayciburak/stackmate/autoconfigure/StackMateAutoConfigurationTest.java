package io.github.kalayciburak.stackmate.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.analysis.AnalysisContribution;
import io.github.kalayciburak.stackmate.analysis.DiagnosisAggregator;
import io.github.kalayciburak.stackmate.analysis.DiagnosticAnalyzer;
import io.github.kalayciburak.stackmate.capture.ServletExceptionCaptureHandler;
import io.github.kalayciburak.stackmate.capture.StackMateExceptionCaptureFilter;
import io.github.kalayciburak.stackmate.configuration.ProductionProfileGuard;
import io.github.kalayciburak.stackmate.configuration.StackMateProperties;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.privacy.Sanitizer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;

class StackMateAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(StackMateAutoConfiguration.class));

    @Test
    void should_enable_deterministic_servlet_diagnostics_with_safe_defaults() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(StackMateProperties.class);
                    assertThat(context).hasSingleBean(ServletExceptionCaptureHandler.class);
                    assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                    assertThat(context).hasSingleBean(DiagnosisAggregator.class);
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
    void should_remain_inert_when_disabled_even_with_irrelevant_invalid_configuration() {
        contextRunner
                .withPropertyValues("stackmate.enabled=false", "stackmate.console.maximum-width=10")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).doesNotHaveBean(StackMateProperties.class);
                        });
    }

    @Test
    void should_not_turn_a_non_web_application_into_a_web_application() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StackMateAutoConfiguration.class))
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).doesNotHaveBean(StackMateProperties.class);
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
    void should_keep_mandatory_sanitizer_primary_and_honor_custom_analyzers() {
        contextRunner
                .withUserConfiguration(UserExtensions.class)
                .run(
                        context -> {
                            assertThat(context).hasBean("stackMateSanitizer");
                            assertThat(context).hasBean("customSanitizer");
                            assertThat(context.getBean(Sanitizer.class))
                                    .isSameAs(context.getBean("stackMateSanitizer"))
                                    .isNotSameAs(context.getBean("customSanitizer"));
                            assertThat(
                                            context.getBean(Sanitizer.class)
                                                    .sanitize("password=synthetic-secret"))
                                    .doesNotContain("synthetic-secret");
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
    void should_reject_removed_or_unknown_stackmate_properties() {
        contextRunner
                .withPropertyValues("stackmate.mode=HYBRID")
                .run(context -> assertThat(context).hasFailed());
        contextRunner
                .withPropertyValues("stackmate.privacy.include-request-body=true")
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
    void should_back_off_when_the_application_supplies_the_capture_filter() {
        contextRunner
                .withUserConfiguration(UserCaptureFilter.class)
                .run(
                        context -> {
                            assertThat(context)
                                    .hasSingleBean(StackMateExceptionCaptureFilter.class);
                            assertThat(context).doesNotHaveBean("stackMateCaptureFilter");
                        });
    }

    @Configuration(proxyBeanMethods = false)
    static class UserExtensions {

        @Bean
        Sanitizer customSanitizer() {
            return new Sanitizer() {
                @Override
                public String sanitize(String value) {
                    return value;
                }

                @Override
                public String sanitize(String fieldName, String value) {
                    return value;
                }
            };
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
    static class UserCaptureFilter {

        @Bean
        StackMateExceptionCaptureFilter userStackMateCaptureFilter(
                io.github.kalayciburak.stackmate.context.ServletContextExtractor extractor,
                ServletExceptionCaptureHandler handler) {
            return new StackMateExceptionCaptureFilter(extractor, handler);
        }
    }
}
