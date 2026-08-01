package io.github.kalayciburak.stackmate.inference.jvm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.model.functions.Generator;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.prompt.PromptContext;
import com.github.tjake.jlama.safetensors.prompt.PromptSupport;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.UUID;

/** Internal managed-process entry point. It is not an application-facing API. */
public final class JlamaChildMain {
    private static final int MAXIMUM_GENERATED_TOKENS = 1_024;

    private JlamaChildMain() {}

    /** Runs the bounded standard-stream protocol for one explicit local model directory. */
    public static void main(String[] args) {
        PrintStream protocolStream = System.out;
        System.setOut(System.err);
        if (args.length != 1) {
            System.err.println("StackMate child requires exactly one local model directory");
            return;
        }

        ObjectMapper mapper =
                new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(System.in));
                DataOutputStream output =
                        new DataOutputStream(new BufferedOutputStream(protocolStream))) {
            run(Path.of(args[0]), mapper, input, output);
        } catch (Exception failure) {
            System.err.println(
                    "StackMate inference child stopped: " + Protocol.boundedError(failure));
        }
    }

    private static void run(
            Path modelDirectory,
            ObjectMapper mapper,
            DataInputStream input,
            DataOutputStream output)
            throws Exception {
        AbstractModel model = null;
        try {
            while (true) {
                Protocol.Request request =
                        Protocol.readFrame(
                                input,
                                Protocol.Request.class,
                                mapper,
                                Protocol.MAXIMUM_REQUEST_BYTES);
                Protocol.Response response;
                if (request.version() != Protocol.VERSION) {
                    response = failed(request, "unsupported protocol version");
                } else if ("shutdown".equals(request.operation())) {
                    response = successful(request, mapper.createObjectNode().put("closed", true));
                    Protocol.writeFrame(output, response, mapper, Protocol.MAXIMUM_RESPONSE_BYTES);
                    return;
                } else if ("health".equals(request.operation())) {
                    response =
                            successful(
                                    request,
                                    mapper.createObjectNode().put("modelLoaded", model != null));
                } else if ("generate".equals(request.operation())) {
                    try {
                        if (model == null) {
                            model =
                                    ModelSupport.loadModel(
                                            modelDirectory.toFile(), DType.F32, DType.I8);
                        }
                        InferenceRequest inferenceRequest =
                                mapper.treeToValue(request.payload(), InferenceRequest.class);
                        String generated = generate(model, inferenceRequest, mapper);
                        if (generated.length() > inferenceRequest.maximumOutputCharacters()) {
                            throw new IllegalArgumentException(
                                    "model output exceeds requested character limit");
                        }
                        response = successful(request, mapper.getNodeFactory().textNode(generated));
                    } catch (Exception failure) {
                        response = failed(request, Protocol.boundedError(failure));
                    }
                } else if ("repair".equals(request.operation())) {
                    try {
                        if (model == null) {
                            model =
                                    ModelSupport.loadModel(
                                            modelDirectory.toFile(), DType.F32, DType.I8);
                        }
                        Protocol.RepairPayload repair =
                                mapper.treeToValue(request.payload(), Protocol.RepairPayload.class);
                        String generated =
                                repair(model, repair.request(), repair.previousOutput(), mapper);
                        if (generated.length() > repair.request().maximumOutputCharacters()) {
                            throw new IllegalArgumentException(
                                    "repaired model output exceeds requested character limit");
                        }
                        response = successful(request, mapper.getNodeFactory().textNode(generated));
                    } catch (Exception failure) {
                        response = failed(request, Protocol.boundedError(failure));
                    }
                } else {
                    response = failed(request, "unsupported operation");
                }
                Protocol.writeFrame(output, response, mapper, Protocol.MAXIMUM_RESPONSE_BYTES);
            }
        } finally {
            if (model != null) {
                model.close();
            }
        }
    }

    private static String generate(
            AbstractModel model, InferenceRequest request, ObjectMapper mapper) {
        PromptRenderer renderer = new PromptRenderer(mapper);
        PromptSupport promptSupport =
                model.promptSupport()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "local model has no compatible chat prompt template"));
        PromptContext prompt =
                promptSupport
                        .builder()
                        .addSystemMessage(renderer.systemPrompt())
                        .addUserMessage(renderer.userPrompt(request))
                        .build();
        int generatedTokens =
                Math.min(
                        MAXIMUM_GENERATED_TOKENS,
                        Math.max(1, (request.maximumOutputCharacters() + 3) / 4));
        int promptTokens = model.getTokenizer().encode(prompt.getPrompt()).length;
        try {
            Generator.Response response =
                    model.generate(
                            UUID.randomUUID(),
                            prompt,
                            0.0f,
                            Math.addExact(promptTokens, generatedTokens));
            return response.responseText.strip();
        } finally {
            JlamaSessionCleaner.clear(model);
        }
    }

    private static String repair(
            AbstractModel model,
            InferenceRequest request,
            String previousOutput,
            ObjectMapper mapper) {
        PromptRenderer renderer = new PromptRenderer(mapper);
        PromptSupport promptSupport =
                model.promptSupport()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "local model has no compatible chat prompt template"));
        PromptContext prompt =
                promptSupport
                        .builder()
                        .addSystemMessage(renderer.repairSystemPrompt())
                        .addUserMessage(renderer.repairUserPrompt(request, previousOutput))
                        .build();
        int generatedTokens =
                Math.min(
                        MAXIMUM_GENERATED_TOKENS,
                        Math.max(1, (request.maximumOutputCharacters() + 3) / 4));
        int promptTokens = model.getTokenizer().encode(prompt.getPrompt()).length;
        try {
            Generator.Response response =
                    model.generate(
                            UUID.randomUUID(),
                            prompt,
                            0.0f,
                            Math.addExact(promptTokens, generatedTokens));
            return response.responseText.strip();
        } finally {
            JlamaSessionCleaner.clear(model);
        }
    }

    private static Protocol.Response successful(Protocol.Request request, JsonNode payload) {
        return new Protocol.Response(Protocol.VERSION, request.requestId(), true, payload, "");
    }

    private static Protocol.Response failed(Protocol.Request request, String error) {
        return new Protocol.Response(Protocol.VERSION, request.requestId(), false, null, error);
    }
}
