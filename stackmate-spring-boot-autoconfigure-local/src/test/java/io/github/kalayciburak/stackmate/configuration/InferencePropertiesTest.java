package io.github.kalayciburak.stackmate.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class InferencePropertiesTest {

    @Test
    void suppliesHistoricalBoundedDefaults() {
        InferenceProperties properties = new InferenceProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getTriggerPolicy())
                .isEqualTo(InferenceTriggerPolicy.WHEN_ENHANCEMENT_IS_USEFUL);
        assertThat(properties.getEngine()).isEqualTo(InferenceProperties.Engine.AUTO);
        assertThat(properties.getDeployment()).isEqualTo(InferenceProperties.Deployment.AUTO);
        assertThat(properties.getModelPath()).isBlank();
        assertThat(properties.isLazyLoad()).isTrue();
        assertThat(properties.getTemperature()).isZero();
        assertThat(properties.getMaximumInputCharacters()).isEqualTo(16_000);
        assertThat(properties.getMaximumOutputTokens()).isEqualTo(256);
        assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getRepairAttempts()).isEqualTo(1);
        assertThat(properties.getExecutor().getThreads()).isEqualTo(1);
        assertThat(properties.getExecutor().getQueueCapacity()).isEqualTo(8);
        assertThat(properties.getCircuitBreaker().getFailureThreshold()).isEqualTo(3);
        assertThat(properties.getCircuitBreaker().getCooldown()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void bindsStandaloneInferenceTree() {
        InferenceProperties properties =
                bind(
                        Map.of(
                                "stackmate-research.inference.timeout", "3s",
                                "stackmate-research.inference.trigger-policy", "always",
                                "stackmate-research.inference.executor.threads", "1",
                                "stackmate-research.inference.circuit-breaker.cooldown", "30s"));

        assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.getTriggerPolicy()).isEqualTo(InferenceTriggerPolicy.ALWAYS);
        assertThat(properties.getExecutor().getThreads()).isEqualTo(1);
        assertThat(properties.getCircuitBreaker().getCooldown()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void rejectsInvalidHistoricalInferenceBounds() {
        assertThatThrownBy(() -> bind(Map.of("stackmate-research.inference.timeout", "0s")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bind(Map.of("stackmate-research.inference.timeout", "2h")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("one hour");
        assertThatThrownBy(() -> bind(Map.of("stackmate-research.inference.executor.threads", "2")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("must be 1");
        assertThatThrownBy(() -> bind(Map.of("stackmate-research.inference.temperature", "0.1")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("must be 0.0");
    }

    private InferenceProperties bind(Map<String, String> values) {
        Binder binder = new Binder(new MapConfigurationPropertySource(values));
        return binder.bind("stackmate-research.inference", Bindable.of(InferenceProperties.class))
                .get();
    }
}
