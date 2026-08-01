package io.github.kalayciburak.stackmate.inference.jvm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelDirectoryTest {
    @TempDir Path temporaryDirectory;

    @Test
    void validatesAndNormalizesExplicitQwen2Directory() throws Exception {
        Path supplied =
                TestModelDirectory.create(
                        temporaryDirectory.resolve("a").resolve("..").resolve("model alanı"));

        ModelDirectory model = ModelDirectory.validate(supplied, new ObjectMapper());

        assertThat(model.path()).isAbsolute().isEqualTo(supplied.toRealPath());
        assertThat(model.descriptor().id()).isEqualTo("model-alan");
        assertThat(model.descriptor().capabilities().english()).isTrue();
        assertThat(model.descriptor().capabilities().turkish()).isTrue();
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

    @Test
    void rejectsUnsafeDimensionsAndTensorMetadataBeforeStartingTheWorker() throws Exception {
        Path dimensions = TestModelDirectory.create(temporaryDirectory.resolve("dimensions"));
        Files.writeString(
                dimensions.resolve("config.json"),
                """
                {"model_type":"qwen2","hidden_size":63,"intermediate_size":128,
                "num_hidden_layers":1,"num_attention_heads":8,"num_key_value_heads":1,
                "vocab_size":256,"max_position_embeddings":128}
                """);
        assertThatThrownBy(() -> ModelDirectory.validate(dimensions, Protocol.strictMapper()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe hidden_size");

        Path empty = TestModelDirectory.create(temporaryDirectory.resolve("empty-metadata"));
        writeTensor(empty.resolve("model.safetensors"), "{}", new byte[] {0});
        assertThatThrownBy(() -> ModelDirectory.validate(empty, Protocol.strictMapper()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no tensors");

        Path offset = TestModelDirectory.create(temporaryDirectory.resolve("escaping-offset"));
        writeTensor(
                offset.resolve("model.safetensors"),
                "{\"weight\":{\"dtype\":\"U8\",\"shape\":[1],\"data_offsets\":[0,2]}}",
                new byte[] {0});
        assertThatThrownBy(() -> ModelDirectory.validate(offset, Protocol.strictMapper()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("offsets escape");

        Path rank = TestModelDirectory.create(temporaryDirectory.resolve("unsafe-rank"));
        writeTensor(
                rank.resolve("model.safetensors"),
                "{\"weight\":{\"dtype\":\"U8\",\"shape\":[1,1,1,1,1,1,1,1,1],\"data_offsets\":[0,1]}}",
                new byte[] {0});
        assertThatThrownBy(() -> ModelDirectory.validate(rank, Protocol.strictMapper()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rank");
    }

    @Test
    void acceptsAnExplicitRelativePathAndRejectsAPathSymlinkWhenSupported() throws Exception {
        Path model = TestModelDirectory.create(temporaryDirectory.resolve("relative-model"));
        Path relative = Path.of("").toAbsolutePath().relativize(model.toAbsolutePath());

        assertThat(ModelDirectory.validate(relative, Protocol.strictMapper()).path())
                .isEqualTo(model.toRealPath());

        Path link = temporaryDirectory.resolve("linked-model");
        try {
            Files.createSymbolicLink(link, model);
        } catch (UnsupportedOperationException | java.io.IOException exception) {
            Assumptions.abort("symbolic links are unavailable: " + exception.getMessage());
        }
        assertThatThrownBy(() -> ModelDirectory.validate(link, Protocol.strictMapper()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("symbolic links");
    }

    @Test
    void rejectsTensorOverlapGapsUnsupportedDtypesAndByteSizeMismatch() throws Exception {
        Path overlap = TestModelDirectory.create(temporaryDirectory.resolve("overlap"));
        writeTensor(
                overlap.resolve("model.safetensors"),
                "{\"one\":{\"dtype\":\"U8\",\"shape\":[1],\"data_offsets\":[0,1]},"
                        + "\"two\":{\"dtype\":\"U8\",\"shape\":[1],\"data_offsets\":[0,1]}}",
                new byte[] {0});
        assertThatThrownBy(() -> ModelDirectory.validate(overlap, Protocol.strictMapper()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");

        Path gap = TestModelDirectory.create(temporaryDirectory.resolve("gap"));
        writeTensor(
                gap.resolve("model.safetensors"),
                "{\"one\":{\"dtype\":\"U8\",\"shape\":[1],\"data_offsets\":[0,1]},"
                        + "\"two\":{\"dtype\":\"U8\",\"shape\":[1],\"data_offsets\":[2,3]}}",
                new byte[] {0, 0, 0});
        assertThatThrownBy(() -> ModelDirectory.validate(gap, Protocol.strictMapper()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gap");

        Path dtype = TestModelDirectory.create(temporaryDirectory.resolve("dtype"));
        writeTensor(
                dtype.resolve("model.safetensors"),
                "{\"weight\":{\"dtype\":\"Q0\",\"shape\":[1],\"data_offsets\":[0,1]}}",
                new byte[] {0});
        assertThatThrownBy(() -> ModelDirectory.validate(dtype, Protocol.strictMapper()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dtype");

        Path size = TestModelDirectory.create(temporaryDirectory.resolve("byte-size"));
        writeTensor(
                size.resolve("model.safetensors"),
                "{\"weight\":{\"dtype\":\"F32\",\"shape\":[1],\"data_offsets\":[0,1]}}",
                new byte[] {0});
        assertThatThrownBy(() -> ModelDirectory.validate(size, Protocol.strictMapper()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("byte size");
    }

    @Test
    void rejectsMissingOversizedAndUnexpectedModelArtifacts() throws Exception {
        Path missing = temporaryDirectory.resolve("missing-model");
        assertThatThrownBy(() -> ModelDirectory.validate(missing, Protocol.strictMapper()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a directory");

        Path oversized = TestModelDirectory.create(temporaryDirectory.resolve("oversized"));
        Files.writeString(oversized.resolve("config.json"), " ".repeat(2 * 1024 * 1024 + 1));
        assertThatThrownBy(() -> ModelDirectory.validate(oversized, Protocol.strictMapper()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("suspicious size");

        Path unexpected = TestModelDirectory.create(temporaryDirectory.resolve("unexpected"));
        Files.delete(unexpected.resolve("model.safetensors"));
        Files.writeString(unexpected.resolve("model.gguf"), "not a supported model weight");
        assertThatThrownBy(() -> ModelDirectory.validate(unexpected, Protocol.strictMapper()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no SafeTensors weights");
    }

    @Test
    void validatesReadOnlyFilesInALongUnicodePath() throws Exception {
        String directoryName = "model alanı with spaces-" + "x".repeat(112);
        Path model = TestModelDirectory.create(temporaryDirectory.resolve(directoryName));
        Path config = model.resolve("config.json");
        Path tokenizer = model.resolve("tokenizer.json");
        Path tokenizerConfig = model.resolve("tokenizer_config.json");
        Path weights = model.resolve("model.safetensors");

        assertThat(config.toFile().setReadOnly()).isTrue();
        assertThat(tokenizer.toFile().setReadOnly()).isTrue();
        assertThat(tokenizerConfig.toFile().setReadOnly()).isTrue();
        assertThat(weights.toFile().setReadOnly()).isTrue();
        try {
            ModelDirectory validated = ModelDirectory.validate(model, Protocol.strictMapper());

            assertThat(validated.path()).isEqualTo(model.toRealPath());
            assertThat(validated.descriptor().id()).hasSize(128);
        } finally {
            assertThat(config.toFile().setWritable(true)).isTrue();
            assertThat(tokenizer.toFile().setWritable(true)).isTrue();
            assertThat(tokenizerConfig.toFile().setWritable(true)).isTrue();
            assertThat(weights.toFile().setWritable(true)).isTrue();
        }
    }

    private void writeTensor(Path path, String metadata, byte[] data) throws Exception {
        byte[] header = metadata.getBytes(StandardCharsets.UTF_8);
        ByteBuffer tensor =
                ByteBuffer.allocate(Long.BYTES + header.length + data.length)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putLong(header.length)
                        .put(header)
                        .put(data);
        Files.write(path, tensor.array());
    }
}
