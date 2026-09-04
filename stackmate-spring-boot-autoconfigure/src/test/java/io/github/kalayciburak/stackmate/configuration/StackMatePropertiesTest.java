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
        assertThat(properties.getApplicationPackages()).isEmpty();
        assertThat(properties.getCapture().isMvcUnhandled()).isTrue();
        assertThat(properties.getCapture().getSelectedMdcKeys())
                .containsExactly("traceId", "spanId", "correlationId");
        assertThat(properties.getDiagnostics().getMaximumCauseDepth()).isEqualTo(32);
        assertThat(properties.getDiagnostics().getMaximumSuppressedExceptions()).isEqualTo(8);
        assertThat(properties.getDiagnostics().getMaximumStackFrames()).isEqualTo(120);
        assertThat(properties.getDiagnostics().getMaximumApplicationFrames()).isEqualTo(8);
        assertThat(properties.getDiagnostics().getMaximumMessageLength()).isEqualTo(2_000);
        assertThat(properties.getPrivacy().getRedactValues()).isEmpty();
        assertThat(properties.getDeduplication().isEnabled()).isTrue();
        assertThat(properties.getDeduplication().getTtl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.getDeduplication().getMaximumEntries()).isEqualTo(512);
        assertThat(properties.getConsole().isEnabled()).isTrue();
        assertThat(properties.getConsole().getStyle()).isEqualTo(ConsoleProperties.Style.BOX);
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
                                Map.entry("stackmate.application-packages[0]", "com.example"),
                                Map.entry("stackmate.capture.mvc-unhandled", "false"),
                                Map.entry("stackmate.capture.selected-mdc-keys[0]", "requestId"),
                                Map.entry("stackmate.diagnostics.maximum-cause-depth", "12"),
                                Map.entry("stackmate.privacy.redact-values[0]", "tenant-secret"),
                                Map.entry("stackmate.deduplication.ttl", "2m"),
                                Map.entry("stackmate.console.debug-logging", "true"),
                                Map.entry("stackmate.export.directory", "var/incidents"),
                                Map.entry("stackmate.safety.production-profiles[0]", "live")));

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getApplicationPackages()).containsExactly("com.example");
        assertThat(properties.getCapture().isMvcUnhandled()).isFalse();
        assertThat(properties.getCapture().getSelectedMdcKeys()).containsExactly("requestId");
        assertThat(properties.getDiagnostics().getMaximumCauseDepth()).isEqualTo(12);
        assertThat(properties.getPrivacy().getRedactValues()).containsExactly("tenant-secret");
        assertThat(properties.getDeduplication().getTtl()).isEqualTo(Duration.ofMinutes(2));
        assertThat(properties.getConsole().isDebugLogging()).isTrue();
        assertThat(properties.getExport().getDirectory()).isEqualTo(Path.of("var/incidents"));
        assertThat(properties.getSafety().getProductionProfiles()).containsExactly("live");
    }

    @Test
    void rejectsUnsafeOrRemovedPrivacyOptions() {
        assertThatThrownBy(() -> bind(Map.of("stackmate.privacy.redact-values[0]", "x")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("at least three characters");
    }

    @Test
    void rejectsInvalidWorkBounds() {
        assertThatThrownBy(() -> bind(Map.of("stackmate.diagnostics.maximum-cause-depth", "0")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bind(Map.of("stackmate.diagnostics.maximum-message-length", "0")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bind(Map.of("stackmate.deduplication.maximum-entries", "-1")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bind(Map.of("stackmate.deduplication.ttl", "2d")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bind(Map.of("stackmate.diagnostics.maximum-stack-frames", "257")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    private StackMateProperties bind(Map<String, String> values) {
        Binder binder = new Binder(new MapConfigurationPropertySource(values));
        return binder.bind("stackmate", Bindable.of(StackMateProperties.class)).get();
    }
}
