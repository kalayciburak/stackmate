package io.github.kalayciburak.stackmate.inference.jvm;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

final class TestModelDirectory {
    private TestModelDirectory() {}

    static Path create(Path directory) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(
                directory.resolve("config.json"),
                """
                {"model_type":"qwen2","transformers_version":"test","hidden_size":64,
                "intermediate_size":128,"num_hidden_layers":1,"num_attention_heads":1,
                "num_key_value_heads":1,"vocab_size":256,"max_position_embeddings":128}
                """);
        Files.writeString(directory.resolve("tokenizer.json"), "{\"model\":{}}");
        Files.writeString(
                directory.resolve("tokenizer_config.json"),
                "{\"chat_template\":\"{{ messages }}\"}");
        byte[] header =
                "{\"weight\":{\"dtype\":\"U8\",\"shape\":[1],\"data_offsets\":[0,1]}}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteBuffer tensor =
                ByteBuffer.allocate(Long.BYTES + header.length + 1)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putLong(header.length)
                        .put(header)
                        .put((byte) 0);
        Files.write(directory.resolve("model.safetensors"), tensor.array());
        return directory;
    }
}
