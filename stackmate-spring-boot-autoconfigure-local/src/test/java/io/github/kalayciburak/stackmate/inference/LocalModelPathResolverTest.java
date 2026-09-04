package io.github.kalayciburak.stackmate.inference;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LocalModelPathResolverTest {

    @Test
    void appliesExplicitEnvironmentAndHomePrecedence() {
        assertThat(LocalModelPathResolver.resolve(" /explicit/model ", "/env/model", "/home/user"))
                .isEqualTo(Path.of("/explicit/model"));
        assertThat(LocalModelPathResolver.resolve("", " /env/model ", "/home/user"))
                .isEqualTo(Path.of("/env/model"));
        assertThat(LocalModelPathResolver.resolve("", "", "/home/user"))
                .isEqualTo(Path.of("/home/user", ".stackmate", "models", "Qwen2.5-0.5B-Instruct"));
    }
}
