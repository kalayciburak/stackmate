package io.github.kalayciburak.stackmate.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class StackMatePropertiesTest {

    @Test
    void suppliesTheDocumentedSafeDefaults() {
        StackMateProperties properties = new StackMateProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getMode()).isEqualTo(StackMateProperties.Mode.DETERMINISTIC);
        assertThat(properties.getLanguage()).isEqualTo(StackMateProperties.Language.EN);
        assertThat(properties.getApplicationPackages()).isEmpty();
        assertThat(properties.getCapture().isMvcUnhandled()).isTrue();
        assertThat(properties.getCapture().getSelectedMdcKeys())
                .containsExactly("traceId", "spanId", "correlationId");
        assertThat(properties.getDiagnostics().getMaximumCauseDepth()).isEqualTo(32);
        assertThat(properties.getDiagnostics().getMaximumSuppressedExceptions()).isEqualTo(8);
        assertThat(properties.getDiagnostics().getMaximumStackFrames()).isEqualTo(120);
        assertThat(properties.getDiagnostics().getMaximumApplicationFrames()).isEqualTo(8);
        assertThat(properties.getDiagnostics().getMaximumMessageLength()).isEqualTo(2_000);
        assertThat(properties.getPrivacy().isIncludeRequestBody()).isFalse();
        assertThat(properties.getPrivacy().isIncludeQueryValues()).isFalse();
        assertThat(properties.getPrivacy().isIncludeHeaders()).isFalse();
        assertThat(properties.getPrivacy().isPiiRedaction()).isTrue();
        assertThat(properties.getPrivacy().getCustomRedactionPatterns()).isEmpty();
        assertThat(properties.getInference().isEnabled()).isTrue();
        assertThat(properties.getInference().getTriggerPolicy())
                .isEqualTo(InferenceTriggerPolicy.WHEN_ENHANCEMENT_IS_USEFUL);
        assertThat(properties.getInference().getEngine())
                .isEqualTo(InferenceProperties.Engine.AUTO);
        assertThat(properties.getInference().getDeployment())
                .isEqualTo(InferenceProperties.Deployment.AUTO);
        assertThat(properties.getInference().getModelPath()).isBlank();
        assertThat(properties.getInference().isLazyLoad()).isTrue();
        assertThat(properties.getInference().getTemperature()).isZero();
        assertThat(properties.getInference().getMaximumInputCharacters()).isEqualTo(16_000);
        assertThat(properties.getInference().getMaximumOutputTokens()).isEqualTo(256);
        assertThat(properties.getInference().getTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getInference().getRepairAttempts()).isEqualTo(1);
        assertThat(properties.getInference().getExecutor().getThreads()).isEqualTo(1);
        assertThat(properties.getInference().getExecutor().getQueueCapacity()).isEqualTo(8);
        assertThat(properties.getInference().getCircuitBreaker().getFailureThreshold())
                .isEqualTo(3);
        assertThat(properties.getInference().getCircuitBreaker().getCooldown())
                .isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.getDeduplication().isEnabled()).isTrue();
        assertThat(properties.getDeduplication().getTtl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.getDeduplication().getMaximumEntries()).isEqualTo(512);
        assertThat(properties.getConsole().isEnabled()).isTrue();
        assertThat(properties.getConsole().getStyle()).isEqualTo(ConsoleProperties.Style.BOX);
        assertThat(properties.getConsole().getAnsi()).isEqualTo(ConsoleProperties.Ansi.AUTO);
        assertThat(properties.getConsole().getMaximumWidth()).isEqualTo(100);
        assertThat(properties.getConsole().isShowApplicationTrace()).isTrue();
        assertThat(properties.getConsole().isShowTechnicalDetails()).isTrue();
        assertThat(properties.getConsole().isShowCauseChain()).isFalse();
        assertThat(properties.getConsole().isDebugLogging()).isFalse();
        assertThat(properties.getExport().isEnabled()).isFalse();
        assertThat(properties.getExport().getDirectory())
                .isEqualTo(Path.of(".stackmate", "incidents"));
        assertThat(properties.getSafety().isAllowInProduction()).isFalse();
        assertThat(properties.getSafety().getProductionProfiles())
                .containsExactly("prod", "production");
    }

    @Test
    void bindsTheNestedKebabCaseConfigurationTree() {
        StackMateProperties properties =
                bind(
                        Map.ofEntries(
                                Map.entry("stackmate.enabled", "false"),
                                Map.entry("stackmate.mode", "deterministic"),
                                Map.entry("stackmate.language", "tr"),
                                Map.entry("stackmate.application-packages[0]", "com.example"),
                                Map.entry("stackmate.capture.mvc-unhandled", "false"),
                                Map.entry("stackmate.capture.selected-mdc-keys[0]", "requestId"),
                                Map.entry("stackmate.diagnostics.maximum-cause-depth", "12"),
                                Map.entry("stackmate.privacy.pii-redaction", "false"),
                                Map.entry("stackmate.inference.timeout", "3s"),
                                Map.entry("stackmate.inference.trigger-policy", "always"),
                                Map.entry("stackmate.inference.executor.threads", "1"),
                                Map.entry("stackmate.inference.circuit-breaker.cooldown", "30s"),
                                Map.entry("stackmate.deduplication.ttl", "2m"),
                                Map.entry("stackmate.console.ansi", "never"),
                                Map.entry("stackmate.console.debug-logging", "true"),
                                Map.entry("stackmate.export.directory", "var/incidents"),
                                Map.entry("stackmate.safety.production-profiles[0]", "live")));

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getMode()).isEqualTo(StackMateProperties.Mode.DETERMINISTIC);
        assertThat(properties.getLanguage()).isEqualTo(StackMateProperties.Language.TR);
        assertThat(properties.getApplicationPackages()).containsExactly("com.example");
        assertThat(properties.getCapture().isMvcUnhandled()).isFalse();
        assertThat(properties.getCapture().getSelectedMdcKeys()).containsExactly("requestId");
        assertThat(properties.getDiagnostics().getMaximumCauseDepth()).isEqualTo(12);
        assertThat(properties.getPrivacy().isPiiRedaction()).isFalse();
        assertThat(properties.getInference().getTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.getInference().getTriggerPolicy())
                .isEqualTo(InferenceTriggerPolicy.ALWAYS);
        assertThat(properties.getInference().getExecutor().getThreads()).isEqualTo(1);
        assertThat(properties.getInference().getCircuitBreaker().getCooldown())
                .isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getDeduplication().getTtl()).isEqualTo(Duration.ofMinutes(2));
        assertThat(properties.getConsole().getAnsi()).isEqualTo(ConsoleProperties.Ansi.NEVER);
        assertThat(properties.getConsole().isDebugLogging()).isTrue();
        assertThat(properties.getExport().getDirectory()).isEqualTo(Path.of("var/incidents"));
        assertThat(properties.getSafety().getProductionProfiles()).containsExactly("live");
    }

    @Test
    void rejectsUnavailableRequestCollectionOptions() {
        assertThatThrownBy(() -> bind(Map.of("stackmate.privacy.include-request-body", "true")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("privacy.includeRequestBody");
        assertThatThrownBy(() -> bind(Map.of("stackmate.privacy.include-query-values", "true")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("privacy.includeQueryValues");
        assertThatThrownBy(() -> bind(Map.of("stackmate.privacy.include-headers", "true")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("privacy.includeHeaders");
    }

    @Test
    void rejectsInvalidWorkBounds() {
        assertThatThrownBy(() -> bind(Map.of("stackmate.diagnostics.maximum-cause-depth", "0")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bind(Map.of("stackmate.inference.timeout", "0s")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bind(Map.of("stackmate.inference.timeout", "2h")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("one hour");
        assertThatThrownBy(() -> bind(Map.of("stackmate.inference.executor.threads", "2")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("must be 1");
        assertThatThrownBy(() -> bind(Map.of("stackmate.diagnostics.maximum-message-length", "0")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bind(Map.of("stackmate.inference.temperature", "0.1")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("must be 0.0");
        assertThatThrownBy(() -> bind(Map.of("stackmate.deduplication.maximum-entries", "-1")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    private StackMateProperties bind(Map<String, String> values) {
        Binder binder = new Binder(new MapConfigurationPropertySource(values));
        return binder.bind("stackmate", Bindable.of(StackMateProperties.class)).get();
    }
}
