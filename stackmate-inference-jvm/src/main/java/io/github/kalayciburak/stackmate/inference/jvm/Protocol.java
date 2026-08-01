package io.github.kalayciburak.stackmate.inference.jvm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

final class Protocol {
    static final int VERSION = 1;
    static final int MAXIMUM_REQUEST_BYTES = 65_536;
    static final int MAXIMUM_RESPONSE_BYTES = 32_768;

    private Protocol() {}

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
        message = message.replaceAll("[\\p{Cc}&&[^\\t]]", " ").strip();
        return message.substring(0, Math.min(message.length(), 400));
    }

    record Request(int version, String operation, String requestId, JsonNode payload) {}

    record Response(
            int version, String requestId, boolean successful, JsonNode payload, String error) {}

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
