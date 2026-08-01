package io.github.kalayciburak.stackmate.inference.jvm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class PromptRenderer {
    static final String VERSION = "v2";
    private static final String RESOURCE =
            "/io/github/kalayciburak/stackmate/inference/jvm/prompts/" + VERSION + "/system.txt";
    private static final String REPAIR_RESOURCE =
            "/io/github/kalayciburak/stackmate/inference/jvm/prompts/" + VERSION + "/repair.txt";
    private static final String SYSTEM_PROMPT = loadSystemPrompt();
    private static final String REPAIR_PROMPT =
            loadPrompt(REPAIR_RESOURCE, "Return exactly one JSON object");

    private final ObjectMapper mapper;

    PromptRenderer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    String userPrompt(InferenceRequest request) {
        PromptInput input =
                new PromptInput(
                        VERSION,
                        request.id(),
                        request.language().name(),
                        request.facts().stream()
                                .map(
                                        fact ->
                                                new Fact(
                                                        fact.id(),
                                                        normalize(fact.text()),
                                                        fact.sourceLocationIds()))
                                .toList(),
                        request.causes().stream()
                                .map(
                                        cause ->
                                                new Cause(
                                                        cause.id(),
                                                        normalize(cause.title()),
                                                        normalize(cause.explanation()),
                                                        cause.factIds()))
                                .toList(),
                        request.checks().stream()
                                .map(
                                        check ->
                                                new Check(
                                                        check.id(),
                                                        normalize(check.instruction()),
                                                        check.factIds()))
                                .toList(),
                        request.sourceLocations().stream()
                                .map(
                                        location ->
                                                new Location(
                                                        location.id(),
                                                        normalize(location.className()),
                                                        normalize(location.methodName()),
                                                        normalize(location.fileName()),
                                                        location.lineNumber()))
                                .toList());
        try {
            return "<STACKMATE_UNTRUSTED_INCIDENT version=\""
                    + VERSION
                    + "\">\n"
                    + mapper.writeValueAsString(input)
                    + "\n</STACKMATE_UNTRUSTED_INCIDENT>";
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "cannot encode bounded inference request", exception);
        }
    }

    String repairSystemPrompt() {
        return REPAIR_PROMPT;
    }

    String repairUserPrompt(InferenceRequest request, String previousOutput) {
        String bounded = previousOutput.substring(0, Math.min(previousOutput.length(), 8_192));
        return userPrompt(request)
                + "\n<STACKMATE_UNTRUSTED_PREVIOUS_OUTPUT>\n"
                + normalize(bounded)
                + "\n</STACKMATE_UNTRUSTED_PREVIOUS_OUTPUT>";
    }

    static String normalize(String value) {
        StringBuilder result = new StringBuilder(value.length());
        char previous = 0;
        int repeated = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == previous) {
                repeated++;
            } else {
                previous = current;
                repeated = 1;
            }
            if (repeated > 16) {
                continue;
            }
            if (current < 0x20 || current == 0x7f) {
                result.append(String.format("\\u%04x", (int) current));
            } else if (current == '`') {
                result.append("\\u0060");
            } else if (current == '<') {
                result.append("\\u003c");
            } else if (current == '>') {
                result.append("\\u003e");
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    private static String loadSystemPrompt() {
        return loadPrompt(RESOURCE, "Required top-level keys");
    }

    private static String loadPrompt(String resource, String requiredText) {
        try (InputStream input = PromptRenderer.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new ExceptionInInitializerError(
                        "missing versioned prompt resource: " + resource);
            }
            String prompt = new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
            if (prompt.isBlank() || !prompt.contains(requiredText)) {
                throw new ExceptionInInitializerError(
                        "invalid versioned prompt resource: " + resource);
            }
            return prompt;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record PromptInput(
            String promptVersion,
            String requestId,
            String language,
            List<Fact> facts,
            List<Cause> causes,
            List<Check> checks,
            List<Location> sourceLocations) {}

    private record Fact(String id, String text, List<String> sourceLocationIds) {}

    private record Cause(String id, String title, String explanation, List<String> factIds) {}

    private record Check(String id, String instruction, List<String> factIds) {}

    private record Location(
            String id, String className, String methodName, String fileName, int lineNumber) {}
}
