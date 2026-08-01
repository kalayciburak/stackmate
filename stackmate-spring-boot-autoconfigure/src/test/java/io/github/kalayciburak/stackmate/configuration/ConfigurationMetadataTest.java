package io.github.kalayciburak.stackmate.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ConfigurationMetadataTest {

    @Test
    void generatesMetadataForNestedUserFacingProperties() throws IOException {
        try (InputStream input =
                StackMateProperties.class
                        .getClassLoader()
                        .getResourceAsStream("META-INF/spring-configuration-metadata.json")) {
            assertThat(input).isNotNull();
            String metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(metadata)
                    .contains("stackmate.capture.mvc-unhandled")
                    .contains("stackmate.diagnostics.maximum-cause-depth")
                    .contains("stackmate.privacy.include-request-body")
                    .contains("stackmate.inference.executor.queue-capacity")
                    .contains("stackmate.inference.trigger-policy")
                    .contains("stackmate.inference.circuit-breaker.cooldown")
                    .contains("stackmate.deduplication.maximum-entries")
                    .contains("stackmate.console.maximum-width")
                    .contains("stackmate.console.debug-logging")
                    .contains("stackmate.export.directory")
                    .contains("stackmate.safety.allow-in-production");
        }
    }
}
