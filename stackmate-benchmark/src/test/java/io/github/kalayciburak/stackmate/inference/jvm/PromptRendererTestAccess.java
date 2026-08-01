package io.github.kalayciburak.stackmate.inference.jvm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;

/** Test-only access to the package-private production prompt renderer. */
public final class PromptRendererTestAccess {

    private PromptRendererTestAccess() {}

    public static RenderedPrompt initial(InferenceRequest request) {
        PromptRenderer renderer = new PromptRenderer(new ObjectMapper());
        return new RenderedPrompt(renderer.systemPrompt(), renderer.userPrompt(request));
    }

    public static RenderedPrompt repair(InferenceRequest request, String previousOutput) {
        PromptRenderer renderer = new PromptRenderer(new ObjectMapper());
        return new RenderedPrompt(
                renderer.repairSystemPrompt(), renderer.repairUserPrompt(request, previousOutput));
    }

    public record RenderedPrompt(String system, String user) {}
}
