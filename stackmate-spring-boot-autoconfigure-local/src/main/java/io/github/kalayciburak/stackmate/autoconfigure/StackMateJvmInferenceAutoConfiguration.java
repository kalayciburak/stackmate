package io.github.kalayciburak.stackmate.autoconfigure;

import io.github.kalayciburak.stackmate.configuration.InferenceProperties;
import io.github.kalayciburak.stackmate.configuration.StackMateProperties;
import io.github.kalayciburak.stackmate.inference.LazyLocalInferenceEngine;
import io.github.kalayciburak.stackmate.inference.LocalInferenceEngine;
import io.github.kalayciburak.stackmate.inference.LocalModelPathResolver;
import io.github.kalayciburak.stackmate.inference.jvm.JlamaLocalInferenceEngine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Historical bridge from Spring configuration to the unsupported JVM inference adapter. */
@AutoConfiguration(
        after = StackMateAutoConfiguration.class,
        before = StackMateInferenceAutoConfiguration.class)
@ConditionalOnClass(
        name = {
            "io.github.kalayciburak.stackmate.inference.LocalInferenceEngine",
            "io.github.kalayciburak.stackmate.inference.jvm.JlamaLocalInferenceEngine"
        })
@ConditionalOnBean(StackMateProperties.class)
@ConditionalOnProperty(prefix = "stackmate", name = "enabled", matchIfMissing = true)
@ConditionalOnProperty(
        prefix = "stackmate-research.inference",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(InferenceProperties.class)
public class StackMateJvmInferenceAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(LocalInferenceEngine.class)
    LazyLocalInferenceEngine stackMateLocalInferenceEngine(
            InferenceProperties inferenceProperties) {
        LazyLocalInferenceEngine engine =
                new LazyLocalInferenceEngine(
                        () ->
                                new JlamaLocalInferenceEngine(
                                        LocalModelPathResolver.resolve(inferenceProperties),
                                        inferenceProperties.getTimeout(),
                                        inferenceProperties.getRepairAttempts()));
        if (!inferenceProperties.isLazyLoad()) {
            engine.initialize();
        }
        return engine;
    }
}
