package io.github.kalayciburak.stackmate.inference;

import io.github.kalayciburak.stackmate.output.RenderOptions;
import java.time.Duration;

/** Extension point for validated asynchronous local-model explanations. */
public interface LocalExplanationRenderer {

    String render(
            ValidatedInferenceOutput output,
            ModelDescriptor model,
            Duration elapsed,
            RenderOptions options);
}
