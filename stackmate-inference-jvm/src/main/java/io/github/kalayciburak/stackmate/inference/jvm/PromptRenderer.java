package io.github.kalayciburak.stackmate.inference.jvm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class PromptRenderer {
    static final String VERSION = InferenceRequest.PROMPT_VERSION;
    private static final String BASE =
            "/io/github/kalayciburak/stackmate/inference/jvm/prompts/schema-v2/";
    private static final String ENGLISH_SYSTEM = loadPrompt("en/system.txt", "Required schema");
    private static final String ENGLISH_REPAIR =
            loadPrompt("en/repair.txt", "Return exactly one JSON object");

    private final ObjectMapper mapper;

    PromptRenderer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    String systemPrompt() {
        return ENGLISH_SYSTEM;
    }

    String userPrompt(InferenceRequest request) {
        PromptInput input =
                new PromptInput(
                        request.schemaVersion(),
                        request.promptVersion(),
                        new Incident(
                                request.incident().category().name(),
                                request.incident().evidenceStrength().name()),
                        request.facts().stream()
                                .map(
                                        fact ->
                                                new Fact(
                                                        fact.id(),
                                                        normalize(fact.type()),
                                                        normalize(fact.value())))
                                .toList(),
                        request.candidateCauses().stream()
                                .map(
                                        cause ->
                                                new Candidate(
                                                        cause.id(),
                                                        normalize(cause.text()),
                                                        cause.evidenceIds()))
                                .toList(),
                        request.candidateChecks().stream()
                                .map(
                                        check ->
                                                new Candidate(
                                                        check.id(),
                                                        normalize(check.text()),
                                                        check.evidenceIds()))
                                .toList(),
                        request.terminology().stream()
                                .map(
                                        term ->
                                                new Term(
                                                        normalize(term.term()),
                                                        normalize(term.definition())))
                                .toList());
        try {
            return "<STACKMATE_UNTRUSTED_INCIDENT schemaVersion=\""
                    + request.schemaVersion()
                    + "\">\n"
                    + mapper.writeValueAsString(input)
                    + "\n</STACKMATE_UNTRUSTED_INCIDENT>";
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "cannot encode bounded inference request", exception);
        }
    }

    String repairSystemPrompt() {
        return ENGLISH_REPAIR;
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

    private static String loadPrompt(String relative, String requiredText) {
        String resource = BASE + relative;
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
            String schemaVersion,
            String promptVersion,
            Incident incident,
            List<Fact> facts,
            List<Candidate> candidateCauses,
            List<Candidate> candidateChecks,
            List<Term> terminology) {}

    private record Incident(String category, String evidenceStrength) {}

    private record Fact(String id, String type, String value) {}

    private record Candidate(String id, String text, List<String> evidenceIds) {}

    private record Term(String term, String definition) {}
}
