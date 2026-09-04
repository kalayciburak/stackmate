package io.github.kalayciburak.stackmate.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InferenceConfigurationMetadataTest {

    @Test
    void generatesMetadataForStandaloneHistoricalInferenceProperties() throws Exception {
        Path classes =
                Path.of(
                        InferenceProperties.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toURI());
        String metadata =
                Files.readString(
                        classes.resolve("META-INF/spring-configuration-metadata.json"),
                        StandardCharsets.UTF_8);

        assertThat(metadata)
                .contains("stackmate-research.inference.executor.queue-capacity")
                .contains("stackmate-research.inference.trigger-policy")
                .contains("stackmate-research.inference.circuit-breaker.cooldown");
    }
}
