package io.github.kalayciburak.stackmate.inference.jvm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import org.junit.jupiter.api.Test;

class ProtocolTest {
    private final ObjectMapper mapper = new ObjectMapper();

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
}
