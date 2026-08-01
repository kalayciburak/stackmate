package io.github.kalayciburak.stackmate.autoconfigure;

import io.github.kalayciburak.stackmate.configuration.StackMateProperties;
import io.github.kalayciburak.stackmate.inference.DefaultAsyncLocalInferenceCoordinator;
import io.github.kalayciburak.stackmate.inference.DefaultInferenceOutputValidator;
import io.github.kalayciburak.stackmate.inference.DefaultLocalExplanationRenderer;
import io.github.kalayciburak.stackmate.inference.DeterministicInferenceRequestFactory;
import io.github.kalayciburak.stackmate.inference.InMemoryInferenceCircuitBreaker;
import io.github.kalayciburak.stackmate.inference.InferenceCircuitBreaker;
import io.github.kalayciburak.stackmate.inference.InferenceOutputValidator;
import io.github.kalayciburak.stackmate.inference.LocalExplanationRenderer;
import io.github.kalayciburak.stackmate.inference.LocalInferenceEngine;
import io.github.kalayciburak.stackmate.inference.LocalInferenceMetrics;
import io.github.kalayciburak.stackmate.privacy.Sanitizer;
import io.github.kalayciburak.stackmate.runtime.AsyncLocalInferenceCoordinator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** Isolated optional auto-configuration for asynchronous local inference. */
@AutoConfiguration(after = StackMateAutoConfiguration.class)
@ConditionalOnClass(name = "io.github.kalayciburak.stackmate.inference.LocalInferenceEngine")
@ConditionalOnBean(StackMateProperties.class)
@ConditionalOnProperty(prefix = "stackmate", name = "enabled", matchIfMissing = true)
@ConditionalOnProperty(prefix = "stackmate", name = "mode", havingValue = "HYBRID")
@ConditionalOnProperty(
        prefix = "stackmate.inference",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class StackMateInferenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    InferenceOutputValidator stackMateInferenceOutputValidator(Sanitizer sanitizer) {
        return new DefaultInferenceOutputValidator(sanitizer);
    }

    @Bean
    @ConditionalOnMissingBean
    InferenceCircuitBreaker stackMateInferenceCircuitBreaker(StackMateProperties properties) {
        var breaker = properties.getInference().getCircuitBreaker();
        return new InMemoryInferenceCircuitBreaker(
                breaker.getFailureThreshold(), breaker.getCooldown());
    }

    @Bean
    @ConditionalOnMissingBean
    DeterministicInferenceRequestFactory stackMateInferenceRequestFactory(Sanitizer sanitizer) {
        return new DeterministicInferenceRequestFactory(sanitizer);
    }

    @Bean
    @ConditionalOnMissingBean
    LocalExplanationRenderer stackMateLocalExplanationRenderer(Sanitizer sanitizer) {
        return new DefaultLocalExplanationRenderer(sanitizer);
    }

    @Bean
    @ConditionalOnMissingBean
    LocalInferenceMetrics stackMateLocalInferenceMetrics() {
        return new LocalInferenceMetrics();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(AsyncLocalInferenceCoordinator.class)
    DefaultAsyncLocalInferenceCoordinator stackMateAsyncLocalInferenceCoordinator(
            ObjectProvider<LocalInferenceEngine> engineProvider,
            InferenceOutputValidator outputValidator,
            InferenceCircuitBreaker circuitBreaker,
            DeterministicInferenceRequestFactory requestFactory,
            LocalExplanationRenderer renderer,
            LocalInferenceMetrics metrics,
            StackMateProperties properties) {
        return new DefaultAsyncLocalInferenceCoordinator(
                engineProvider,
                outputValidator,
                circuitBreaker,
                requestFactory,
                renderer,
                metrics,
                properties);
    }
}
