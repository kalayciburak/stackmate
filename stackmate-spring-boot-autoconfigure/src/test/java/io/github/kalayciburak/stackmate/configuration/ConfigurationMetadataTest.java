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
                    .contains("stackmate.privacy.redact-values")
                    .contains("stackmate.deduplication.maximum-entries")
                    .contains("stackmate.console.maximum-width")
                    .contains("stackmate.console.debug-logging")
                    .contains("stackmate.export.directory")
                    .contains("stackmate.safety.allow-in-production")
                    .doesNotContain(
                            "stackmate.mode",
                            "stackmate.inference",
                            "stackmate.console.ansi",
                            "stackmate.privacy.include-request-body",
                            "stackmate.privacy.custom-redaction-patterns");
            assertThat(metadata.split("\\\"description\\\"")).hasSizeGreaterThan(25);
            assertThat(metadata)
                    .contains("Additional literal values redacted")
                    .contains("Regular expressions are not accepted")
                    .contains("Multi-line boxed diagnostic output")
                    .contains("Short compact diagnostic output");
        }
    }
}
