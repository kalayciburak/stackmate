package io.github.kalayciburak.stackmate.inference.jvm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProtocolTest {
    private final ObjectMapper mapper = Protocol.strictMapper();

    @Test
    void roundTripsBoundedFrame() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Protocol.Request request =
                new Protocol.Request(1, "health", "request-1", mapper.createObjectNode());

        Protocol.writeFrame(new DataOutputStream(bytes), request, mapper, 1_024);
        Protocol.Request decoded =
                Protocol.readFrame(
                        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),
                        Protocol.Request.class,
                        mapper,
                        1_024);

        assertThat(decoded).isEqualTo(request);
    }

    @Test
    void rejectsOversizedInvalidAndTruncatedFrames() throws Exception {
        ByteArrayOutputStream oversized = new ByteArrayOutputStream();
        assertThatThrownBy(
                        () ->
                                Protocol.writeFrame(
                                        new DataOutputStream(oversized),
                                        "x".repeat(100),
                                        mapper,
                                        10))
                .isInstanceOf(Protocol.ProtocolException.class);

        ByteArrayOutputStream invalid = new ByteArrayOutputStream();
        new DataOutputStream(invalid).writeInt(1_025);
        assertThatThrownBy(
                        () ->
                                Protocol.readFrame(
                                        new DataInputStream(
                                                new ByteArrayInputStream(invalid.toByteArray())),
                                        Protocol.Response.class,
                                        mapper,
                                        1_024))
                .isInstanceOf(Protocol.ProtocolException.class)
                .hasMessageContaining("length");

        ByteArrayOutputStream truncated = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(truncated);
        output.writeInt(5);
        output.writeByte('{');
        assertThatThrownBy(
                        () ->
                                Protocol.readFrame(
                                        new DataInputStream(
                                                new ByteArrayInputStream(truncated.toByteArray())),
                                        Protocol.Response.class,
                                        mapper,
                                        1_024))
                .isInstanceOf(Protocol.ProtocolException.class)
                .hasMessageContaining("truncated");

        ByteArrayOutputStream malformed = new ByteArrayOutputStream();
        DataOutputStream malformedOutput = new DataOutputStream(malformed);
        malformedOutput.writeInt(1);
        malformedOutput.writeByte('{');
        assertThatThrownBy(
                        () ->
                                Protocol.readFrame(
                                        new DataInputStream(
                                                new ByteArrayInputStream(malformed.toByteArray())),
                                        Protocol.Response.class,
                                        mapper,
                                        1_024))
                .isInstanceOf(Protocol.ProtocolException.class)
                .hasMessageContaining("malformed");
    }

    @Test
    void rejectsDuplicateUnknownDeepAndInvalidUtf8Content() throws Exception {
        assertMalformed(
                "{\"version\":1,\"version\":1,\"operation\":\"health\",\"requestId\":\"1\",\"payload\":{}}");
        assertMalformed(
                "{\"version\":1,\"operation\":\"health\",\"requestId\":\"1\",\"payload\":{},\"extra\":true}");
        assertMalformed("[".repeat(65) + "0" + "]".repeat(65));
        assertMalformed(new byte[] {(byte) 0xC3, (byte) 0x28});
    }

    @Test
    void sanitizesProcessFailuresBeforeReturningDetails() {
        String failure =
                Protocol.boundedError(
                        new IOException(
                                "password=hunter2 Authorization: Bearer abc.def.ghi\u001B[31m"));

        assertThat(failure)
                .doesNotContain("hunter2", "abc.def.ghi", "\u001B")
                .contains("[REDACTED]");
    }

    private void assertMalformed(String json) throws Exception {
        assertMalformed(json.getBytes(StandardCharsets.UTF_8));
    }

    private void assertMalformed(byte[] payload) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(payload.length);
        output.write(payload);
        assertThatThrownBy(
                        () ->
                                Protocol.readFrame(
                                        new DataInputStream(
                                                new ByteArrayInputStream(bytes.toByteArray())),
                                        Protocol.Request.class,
                                        mapper,
                                        1_024))
                .isInstanceOf(Protocol.ProtocolException.class)
                .hasMessageContaining("malformed");
    }
}
