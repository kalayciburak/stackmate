package io.github.kalayciburak.stackmate.inference.jvm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kalayciburak.stackmate.inference.InferenceCauseCandidate;
import io.github.kalayciburak.stackmate.inference.InferenceCheckCandidate;
import io.github.kalayciburak.stackmate.inference.InferenceFact;
import io.github.kalayciburak.stackmate.inference.InferenceLanguage;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import io.github.kalayciburak.stackmate.inference.StructuredCause;
import io.github.kalayciburak.stackmate.inference.StructuredCheck;
import io.github.kalayciburak.stackmate.inference.StructuredInferenceOutput;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;

/** Executable sample fixture for packaged parent/child protocol integration tests. */
public final class SampleFakeInferenceChildMain {
    private SampleFakeInferenceChildMain() {}

    public static void main(String[] arguments) throws Exception {
        PrintStream protocolStream = System.out;
        System.setOut(System.err);
        ObjectMapper mapper = Protocol.strictMapper();
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(System.in));
                DataOutputStream output =
                        new DataOutputStream(new BufferedOutputStream(protocolStream))) {
            while (true) {
                Protocol.Request message =
                        Protocol.readFrame(
                                input,
                                Protocol.Request.class,
                                mapper,
                                Protocol.MAXIMUM_REQUEST_BYTES);
                if ("shutdown".equals(message.operation())) {
                    write(output, mapper, message, mapper.createObjectNode().put("closed", true));
                    return;
                }
                InferenceRequest request =
                        mapper.treeToValue(message.payload(), InferenceRequest.class);
                String scenario = message.payload().toString().toLowerCase(Locale.ROOT);
                if (scenario.contains("fakeinvalidjson")) {
                    raw(output, new byte[] {'{'});
                    return;
                }
                if (scenario.contains("fakeinvalidutf8")) {
                    raw(output, new byte[] {(byte) 0xC3, (byte) 0x28});
                    return;
                }
                if (scenario.contains("fakeoversized")) {
                    output.writeInt(Protocol.MAXIMUM_RESPONSE_BYTES + 1);
                    output.flush();
                    return;
                }
                if (scenario.contains("fakeexit")) {
                    Runtime.getRuntime().halt(17);
                }
                if (scenario.contains("faketimeout") || scenario.contains("fakequeue")) {
                    Thread.sleep(5_000);
                }
                System.err.print(
                        "fixture stderr password=hunter2 Authorization: Bearer abc.def.ghi\u001B[31m\n");
                write(output, mapper, message, output(request, scenario));
            }
        }
    }

    private static StructuredInferenceOutput output(InferenceRequest request, String scenario) {
        InferenceFact fact = request.facts().getFirst();
        List<String> factIds =
                scenario.contains("fakeunknownevidence")
                        ? List.of("fact.unknown")
                        : List.of(fact.id());
        List<String> sourceIds =
                request.sourceLocations().isEmpty()
                        ? List.of()
                        : List.of(request.sourceLocations().getFirst().id());
        String summary =
                scenario.contains("fakefabricatedsource")
                        ? "Inspect InventedSource.java:42 before continuing."
                        : request.language() == InferenceLanguage.TURKISH
                                ? "Bu hata yalnızca sağlanan kanıtlarla açıklanmıştır."
                                : "This failure is explained only by the supplied evidence.";
        List<StructuredCause> causes =
                request.causes().isEmpty()
                        ? List.of()
                        : List.of(cause(request.causes().getFirst(), factIds, sourceIds));
        List<StructuredCheck> checks =
                request.checks().isEmpty()
                        ? List.of()
                        : List.of(check(request.checks().getFirst(), factIds, sourceIds));
        return new StructuredInferenceOutput(request.language(), summary, causes, checks);
    }

    private static StructuredCause cause(
            InferenceCauseCandidate candidate, List<String> factIds, List<String> sourceIds) {
        return new StructuredCause(
                candidate.id(),
                "The supplied cause is supported by the selected evidence.",
                factIds,
                sourceIds);
    }

    private static StructuredCheck check(
            InferenceCheckCandidate candidate, List<String> factIds, List<String> sourceIds) {
        return new StructuredCheck(
                candidate.id(),
                "Check the supplied candidate before changing application behavior.",
                factIds,
                sourceIds);
    }

    private static void write(
            DataOutputStream output, ObjectMapper mapper, Protocol.Request request, Object payload)
            throws IOException {
        Protocol.writeFrame(
                output,
                new Protocol.Response(
                        Protocol.VERSION,
                        request.requestId(),
                        true,
                        mapper.valueToTree(payload),
                        ""),
                mapper,
                Protocol.MAXIMUM_RESPONSE_BYTES);
    }

    private static void raw(DataOutputStream output, byte[] payload) throws IOException {
        output.writeInt(payload.length);
        output.write(payload);
        output.flush();
    }
}
