package io.github.kalayciburak.stackmate.inference.jvm;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kalayciburak.stackmate.inference.InferenceFailure;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

final class Protocol {
    static final int VERSION = 2;
    static final int MAXIMUM_REQUEST_BYTES = 65_536;
    static final int MAXIMUM_RESPONSE_BYTES = 32_768;

    private Protocol() {}

    static ObjectMapper strictMapper() {
        StreamReadConstraints constraints =
                StreamReadConstraints.builder()
                        .maxNestingDepth(64)
                        .maxDocumentLength(64L * 1024 * 1024)
                        .maxStringLength(64 * 1024 * 1024)
                        .maxNameLength(512)
                        .maxNumberLength(128)
                        .build();
        JsonFactory factory =
                JsonFactory.builder()
                        .streamReadConstraints(constraints)
                        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                        .build();
        return new ObjectMapper(factory)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }

    static void writeFrame(
            DataOutputStream output, Object value, ObjectMapper mapper, int maximumBytes)
            throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(value);
        if (bytes.length == 0 || bytes.length > maximumBytes) {
            throw new ProtocolException("frame exceeds protocol limit");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
        output.flush();
    }

    static <T> T readFrame(
            DataInputStream input, Class<T> type, ObjectMapper mapper, int maximumBytes)
            throws IOException {
        final int length;
        try {
            length = input.readInt();
        } catch (EOFException exception) {
            throw new ProtocolException("protocol stream ended", exception);
        }
        if (length <= 0 || length > maximumBytes) {
            throw new ProtocolException("invalid protocol frame length: " + length);
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new ProtocolException("truncated protocol frame");
        }
        try {
            return mapper.readValue(bytes, type);
        } catch (IOException | RuntimeException exception) {
            throw new ProtocolException("malformed protocol frame", exception);
        }
    }

    static String boundedError(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        String sanitized = DefaultSanitizer.defaults().sanitize("processError", message).strip();
        if (sanitized.isBlank()) {
            sanitized = "local inference process failed";
        }
        return sanitized.substring(0, Math.min(sanitized.length(), 400));
    }

    record Request(int version, String operation, String requestId, JsonNode payload) {}

    record Response(
            int version,
            String requestId,
            boolean successful,
            JsonNode payload,
            String errorKind,
            String error) {

        Response(
                int version, String requestId, boolean successful, JsonNode payload, String error) {
            this(
                    version,
                    requestId,
                    successful,
                    payload,
                    successful ? "" : InferenceFailure.Kind.ENGINE_ERROR.name(),
                    error);
        }
    }

    record RepairPayload(InferenceRequest request, String previousOutput) {
        RepairPayload {
            if (request == null || previousOutput == null || previousOutput.isBlank()) {
                throw new IllegalArgumentException("repair payload is incomplete");
            }
            if (previousOutput.length() > InferenceRequest.MAXIMUM_OUTPUT_CHARACTERS) {
                throw new IllegalArgumentException("repair output exceeds protocol limit");
            }
        }
    }

    static final class ProtocolException extends IOException {
        private static final long serialVersionUID = 1L;

        ProtocolException(String message) {
            super(message);
        }

        ProtocolException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
