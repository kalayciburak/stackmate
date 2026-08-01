package io.github.kalayciburak.stackmate.inference.jvm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kalayciburak.stackmate.inference.ModelCapabilities;
import io.github.kalayciburak.stackmate.inference.ModelDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class ModelDirectory {
    private static final long MAXIMUM_CONFIG_BYTES = 2L * 1024 * 1024;
    private static final long MAXIMUM_TOKENIZER_BYTES = 64L * 1024 * 1024;
    private static final long MAXIMUM_TENSOR_BYTES = 4L * 1024 * 1024 * 1024;
    private static final long MAXIMUM_TOTAL_TENSOR_BYTES = 4L * 1024 * 1024 * 1024;
    private static final long MAXIMUM_TENSOR_HEADER_BYTES = 100L * 1024 * 1024;
    private static final Pattern SAFE_WEIGHT_NAME =
            Pattern.compile("[A-Za-z0-9._-]+\\.safetensors");

    private final Path path;
    private final ModelDescriptor descriptor;

    private ModelDirectory(Path path, ModelDescriptor descriptor) {
        this.path = path;
        this.descriptor = descriptor;
    }

    static ModelDirectory validate(Path supplied, ObjectMapper mapper) {
        if (supplied == null) {
            throw new IllegalArgumentException("modelDirectory must not be null");
        }
        try {
            Path absolute = supplied.toAbsolutePath().normalize();
            rejectSymbolicComponents(absolute);
            if (!Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("model path is not a directory: " + absolute);
            }
            Path root = absolute.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path config = requiredRegularFile(root, "config.json", MAXIMUM_CONFIG_BYTES);
            Path tokenizer = requiredRegularFile(root, "tokenizer.json", MAXIMUM_TOKENIZER_BYTES);
            Path tokenizerConfig =
                    requiredRegularFile(root, "tokenizer_config.json", MAXIMUM_CONFIG_BYTES);

            JsonNode configJson = readObject(config, mapper, "config.json");
            String modelType = requiredText(configJson, "model_type", "config.json");
            if (!"qwen2".equals(modelType)) {
                throw new IllegalArgumentException("only Qwen2-compatible models are supported");
            }
            JsonNode tokenizerJson = readObject(tokenizer, mapper, "tokenizer.json");
            if (!tokenizerJson.path("model").isObject()) {
                throw new IllegalArgumentException("tokenizer.json is missing model metadata");
            }
            JsonNode tokenizerConfigJson =
                    readObject(tokenizerConfig, mapper, "tokenizer_config.json");
            if (!tokenizerConfigJson.path("chat_template").isTextual()
                    || tokenizerConfigJson.path("chat_template").textValue().isBlank()) {
                throw new IllegalArgumentException(
                        "tokenizer_config.json is missing the Qwen2 chat template");
            }
            validateWeights(root, mapper);

            String id = stableId(root.getFileName().toString());
            String displayName =
                    configJson.path("_name_or_path").isTextual()
                            ? configJson.path("_name_or_path").textValue()
                            : root.getFileName().toString();
            if (!safeMetadataText(displayName, 256)) {
                displayName = root.getFileName().toString();
            }
            String version =
                    configJson.path("transformers_version").isTextual()
                            ? configJson.path("transformers_version").textValue()
                            : "local";
            if (!safeMetadataText(version, 128)) {
                version = "local";
            }
            return new ModelDirectory(
                    root,
                    new ModelDescriptor(
                            id,
                            displayName,
                            version,
                            new ModelCapabilities(true, true, false, 16_000, 8_192)));
        } catch (IOException exception) {
            throw new IllegalArgumentException("cannot validate local model directory", exception);
        }
    }

    Path path() {
        return path;
    }

    ModelDescriptor descriptor() {
        return descriptor;
    }

    private static void validateWeights(Path root, ObjectMapper mapper) throws IOException {
        Path index = root.resolve("model.safetensors.index.json");
        Set<Path> weights = new HashSet<>();
        if (Files.exists(index, LinkOption.NOFOLLOW_LINKS)) {
            requiredRegularFile(root, index.getFileName().toString(), MAXIMUM_CONFIG_BYTES);
            JsonNode indexJson = readObject(index, mapper, "model.safetensors.index.json");
            JsonNode weightMap = indexJson.path("weight_map");
            if (!weightMap.isObject() || weightMap.isEmpty()) {
                throw new IllegalArgumentException("SafeTensors index has no weight_map");
            }
            weightMap
                    .elements()
                    .forEachRemaining(
                            value -> {
                                if (!value.isTextual()
                                        || !SAFE_WEIGHT_NAME.matcher(value.textValue()).matches()) {
                                    throw new IllegalArgumentException(
                                            "SafeTensors index contains an unsafe shard name");
                                }
                                weights.add(root.resolve(value.textValue()).normalize());
                            });
        } else {
            try (Stream<Path> stream = Files.list(root)) {
                stream.filter(
                                path ->
                                        SAFE_WEIGHT_NAME
                                                .matcher(path.getFileName().toString())
                                                .matches())
                        .forEach(weights::add);
            }
        }
        if (weights.isEmpty()) {
            throw new IllegalArgumentException("model directory contains no SafeTensors weights");
        }
        long total = 0;
        for (Path weight : weights) {
            if (!weight.getParent().equals(root)) {
                throw new IllegalArgumentException("SafeTensors shard escapes model directory");
            }
            Path checked =
                    requiredRegularFile(
                            root, weight.getFileName().toString(), MAXIMUM_TENSOR_BYTES);
            long size = Files.size(checked);
            if (size <= 8) {
                throw new IllegalArgumentException("SafeTensors file is suspiciously small");
            }
            validateTensorHeader(checked, size, mapper);
            total = Math.addExact(total, size);
            if (total > MAXIMUM_TOTAL_TENSOR_BYTES) {
                throw new IllegalArgumentException("SafeTensors weights exceed the supported size");
            }
        }
    }

    private static void validateTensorHeader(Path file, long fileSize, ObjectMapper mapper)
            throws IOException {
        byte[] lengthBytes = new byte[Long.BYTES];
        try (InputStream input = Files.newInputStream(file)) {
            if (input.readNBytes(lengthBytes, 0, lengthBytes.length) != lengthBytes.length) {
                throw new IllegalArgumentException("truncated SafeTensors header");
            }
            long headerLength =
                    ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
            if (headerLength <= 1
                    || headerLength > MAXIMUM_TENSOR_HEADER_BYTES
                    || headerLength > fileSize - Long.BYTES
                    || headerLength > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("invalid SafeTensors header length");
            }
            byte[] header = input.readNBytes((int) headerLength);
            if (header.length != (int) headerLength || !mapper.readTree(header).isObject()) {
                throw new IllegalArgumentException("malformed SafeTensors metadata");
            }
        }
    }

    private static Path requiredRegularFile(Path root, String name, long maximumBytes)
            throws IOException {
        Path file = root.resolve(name).normalize();
        if (!file.getParent().equals(root)
                || Files.isSymbolicLink(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("required local model file is missing: " + name);
        }
        long size = Files.size(file);
        if (size <= 0 || size > maximumBytes) {
            throw new IllegalArgumentException("local model file has a suspicious size: " + name);
        }
        Path real = file.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!real.getParent().equals(root)) {
            throw new IllegalArgumentException("local model file escapes model directory: " + name);
        }
        return real;
    }

    private static JsonNode readObject(Path file, ObjectMapper mapper, String name)
            throws IOException {
        JsonNode node;
        try (InputStream input = Files.newInputStream(file)) {
            node = mapper.readTree(input);
        }
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(name + " must contain a JSON object");
        }
        return node;
    }

    private static String requiredText(JsonNode node, String field, String file) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(file + " is missing " + field);
        }
        return value.textValue();
    }

    private static void rejectSymbolicComponents(Path absolute) {
        Path current = absolute.getRoot();
        for (Path component : absolute) {
            current = current == null ? component : current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("model path must not contain symbolic links");
            }
        }
    }

    private static String stableId(String value) {
        String normalized =
                value.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9._/-]+", "-")
                        .replaceAll("^[^a-z0-9]+", "")
                        .replaceAll("-+$", "");
        if (normalized.isEmpty()) {
            normalized = "local-qwen2";
        }
        return normalized.substring(0, Math.min(normalized.length(), 128));
    }

    private static boolean safeMetadataText(String value, int maximumLength) {
        if (value.isBlank() || value.length() > maximumLength) {
            return false;
        }
        return value.codePoints().noneMatch(Character::isISOControl);
    }
}
