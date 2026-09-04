package external.consumer.stackmate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kalayciburak.stackmate.analysis.AnalysisContribution;
import io.github.kalayciburak.stackmate.analysis.DiagnosisAggregator;
import io.github.kalayciburak.stackmate.analysis.DiagnosticAnalyzer;
import io.github.kalayciburak.stackmate.capture.ServletExceptionCaptureHandler;
import io.github.kalayciburak.stackmate.configuration.StackMateProperties;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ConsumerStarterContractTest {

    private final WebApplicationContextRunner webContext =
            new WebApplicationContextRunner().withUserConfiguration(AutoConfiguredConsumer.class);

    @Test
    void enabledStarterIsPresentByDefault() {
        webContext.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(StackMateProperties.class);
                    assertThat(context).hasSingleBean(ServletExceptionCaptureHandler.class);
                    assertThat(context).hasBean("stackMateCaptureFilter");
                });
    }

    @Test
    void disabledStarterIsInert() {
        webContext
                .withPropertyValues("stackmate.enabled=false")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).doesNotHaveBean(StackMateProperties.class);
                            assertThat(context)
                                    .doesNotHaveBean(ServletExceptionCaptureHandler.class);
                            assertThat(context).doesNotHaveBean("stackMateCaptureFilter");
                        });
    }

    @Test
    void nonWebApplicationDoesNotActivateStarter() {
        new ApplicationContextRunner()
                .withUserConfiguration(AutoConfiguredConsumer.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).doesNotHaveBean(StackMateProperties.class);
                            assertThat(context)
                                    .doesNotHaveBean(ServletExceptionCaptureHandler.class);
                        });
    }

    @Test
    void removedOrUnknownStackMatePropertyFailsClearly() {
        assertInvalidProperty("stackmate.mode=HYBRID", "stackmate.mode");
        assertInvalidProperty(
                "stackmate.unrecognized-consumer-option=true",
                "stackmate.unrecognized-consumer-option");
    }

    @Test
    void userDiagnosticAnalyzerBeanIsHonored() {
        webContext
                .withUserConfiguration(CustomAnalyzerConfiguration.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean(DiagnosisAggregator.class).analyzers())
                                    .extracting(DiagnosticAnalyzer::id)
                                    .contains("external-consumer");
                        });
    }

    @Test
    void inferenceClassesAreAbsentFromPublicStarter() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        assertThatThrownBy(
                        () ->
                                Class.forName(
                                        "io.github.kalayciburak.stackmate.configuration.InferenceProperties",
                                        false,
                                        classLoader))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(
                        () ->
                                Class.forName(
                                        "io.github.kalayciburak.stackmate.runtime.AsyncLocalInferenceCoordinator",
                                        false,
                                        classLoader))
                .isInstanceOf(ClassNotFoundException.class);
    }

    private static String allMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append('\n');
            }
        }
        return messages.toString();
    }

    private void assertInvalidProperty(String property, String expectedName) {
        webContext
                .withPropertyValues(property)
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(allMessages(context.getStartupFailure()))
                                    .contains(expectedName);
                        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class AutoConfiguredConsumer {}

    @Configuration(proxyBeanMethods = false)
    static class CustomAnalyzerConfiguration {

        @Bean
        DiagnosticAnalyzer externalConsumerAnalyzer() {
            return new DiagnosticAnalyzer() {
                @Override
                public String id() {
                    return "external-consumer";
                }

                @Override
                public int order() {
                    return 1;
                }

                @Override
                public AnalysisContribution analyze(IncidentSnapshot incident) {
                    return AnalysisContribution.empty(id());
                }
            };
        }
    }
}
