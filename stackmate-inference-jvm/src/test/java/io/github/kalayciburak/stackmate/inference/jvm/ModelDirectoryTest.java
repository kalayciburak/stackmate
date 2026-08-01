package io.github.kalayciburak.stackmate.inference.jvm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelDirectoryTest {
    @TempDir Path temporaryDirectory;

    @Test
    void validatesAndNormalizesExplicitQwen2Directory() throws Exception {
        Path supplied =
                TestModelDirectory.create(
                        temporaryDirectory.resolve("a").resolve("..").resolve("model"));

        ModelDirectory model = ModelDirectory.validate(supplied, new ObjectMapper());

        assertThat(model.path()).isAbsolute().isEqualTo(supplied.toRealPath());
        assertThat(model.descriptor().id()).isEqualTo("model");
        assertThat(model.descriptor().capabilities().english()).isTrue();
        assertThat(model.descriptor().capabilities().turkish()).isFalse();
    }

    @Test
    void rejectsMissingTokenizerAndUnsupportedMetadata() throws Exception {
        Path model = TestModelDirectory.create(temporaryDirectory.resolve("missing-tokenizer"));
        Files.delete(model.resolve("tokenizer.json"));

        assertThatThrownBy(() -> ModelDirectory.validate(model, new ObjectMapper()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokenizer.json");

        Path unsupported = TestModelDirectory.create(temporaryDirectory.resolve("unsupported"));
        Files.writeString(unsupported.resolve("config.json"), "{\"model_type\":\"llama\"}");
        assertThatThrownBy(() -> ModelDirectory.validate(unsupported, new ObjectMapper()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Qwen2");
    }

    @Test
    void rejectsUnsafeIndexShardAndMalformedTensorHeader() throws Exception {
        Path indexed = TestModelDirectory.create(temporaryDirectory.resolve("indexed"));
        Files.delete(indexed.resolve("model.safetensors"));
        Files.writeString(
                indexed.resolve("model.safetensors.index.json"),
                "{\"weight_map\":{\"x\":\"../outside.safetensors\"}}");
        assertThatThrownBy(() -> ModelDirectory.validate(indexed, new ObjectMapper()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe shard");

        Path malformed = TestModelDirectory.create(temporaryDirectory.resolve("malformed"));
        Files.write(malformed.resolve("model.safetensors"), new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9});
        assertThatThrownBy(() -> ModelDirectory.validate(malformed, new ObjectMapper()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SafeTensors header");
    }
}
