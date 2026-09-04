package io.github.kalayciburak.stackmate.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.capture.ServletExceptionCaptureHandler;
import io.github.kalayciburak.stackmate.configuration.StackMateProperties;
import io.github.kalayciburak.stackmate.inference.InferenceAvailability;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import io.github.kalayciburak.stackmate.inference.InferenceResult;
import io.github.kalayciburak.stackmate.inference.LazyLocalInferenceEngine;
import io.github.kalayciburak.stackmate.inference.LocalInferenceEngine;
import io.github.kalayciburak.stackmate.inference.LocalInferenceMetrics;
import io.github.kalayciburak.stackmate.inference.ModelCapabilities;
import io.github.kalayciburak.stackmate.inference.ModelDescriptor;
import io.github.kalayciburak.stackmate.runtime.AsyncLocalInferenceCoordinator;
import io.github.kalayciburak.stackmate.runtime.DefaultServletExceptionCaptureHandler;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class StackMateInferenceAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    StackMateAutoConfiguration.class,
                                    StackMateJvmInferenceAutoConfiguration.class,
                                    StackMateInferenceAutoConfiguration.class));

    @Test
    void createsHistoricalLazyCoordinatorWithOrWithoutCustomEngine() {
        contextRunner
                .withPropertyValues("stackmate-research.inference.model-path=missing-local-model")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(LocalInferenceEngine.class);
                            assertThat(context.getBean(LocalInferenceEngine.class))
                                    .isInstanceOf(LazyLocalInferenceEngine.class);
                            assertThat(context)
                                    .hasSingleBean(AsyncLocalInferenceCoordinator.class)
                                    .hasSingleBean(LocalInferenceMetrics.class);
                        });

        contextRunner
                .withUserConfiguration(LocalEngineConfiguration.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(LocalInferenceEngine.class);
                            assertThat(context.getBean(LocalInferenceEngine.class))
                                    .isSameAs(context.getBean("localInferenceEngine"));
                            assertThat(context).hasSingleBean(AsyncLocalInferenceCoordinator.class);
                        });
    }

    @Test
    void backsOffWhenHistoricalInferenceIsDisabledOrUnavailable() {
        contextRunner
                .withPropertyValues("stackmate-research.inference.enabled=false")
                .run(
                        context ->
                                assertThat(context)
                                        .doesNotHaveBean(AsyncLocalInferenceCoordinator.class));

        contextRunner
                .withPropertyValues("stackmate.enabled=false")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StackMateProperties.class);
                            assertThat(context)
                                    .doesNotHaveBean(AsyncLocalInferenceCoordinator.class);
                        });

        contextRunner
                .withClassLoader(new FilteredClassLoader(LocalInferenceEngine.class))
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(ServletExceptionCaptureHandler.class);
                            assertThat(context)
                                    .doesNotHaveBean(AsyncLocalInferenceCoordinator.class);
                        });
    }

    @Test
    void keepsHistoricalCoordinatorDetachedFromDeterministicCaptureHandler() {
        assertThat(
                        Arrays.stream(
                                        DefaultServletExceptionCaptureHandler.class
                                                .getDeclaredFields())
                                .map(field -> field.getType().getName()))
                .doesNotContain(AsyncLocalInferenceCoordinator.class.getName());
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
                            new ModelCapabilities(true, 16_000, 8_192));
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
