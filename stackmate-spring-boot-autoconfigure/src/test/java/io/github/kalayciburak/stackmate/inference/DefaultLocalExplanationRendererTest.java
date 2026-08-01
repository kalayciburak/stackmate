package io.github.kalayciburak.stackmate.inference;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.output.RenderLanguage;
import io.github.kalayciburak.stackmate.output.RenderOptions;
import io.github.kalayciburak.stackmate.output.RenderStyle;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultLocalExplanationRendererTest {

    @Test
    void renders_bounded_terminal_safe_english_and_turkish_output() {
        DefaultLocalExplanationRenderer renderer =
                new DefaultLocalExplanationRenderer(DefaultSanitizer.defaults());
        ValidatedInferenceOutput output =
                new ValidatedInferenceOutput(
                        new StructuredInferenceOutput(
                                InferenceLanguage.ENGLISH,
                                "This summary has password=local-secret\u001B[2J",
                                List.of(
                                        new StructuredCause(
                                                "cause.one",
                                                "This cause uses evidence.",
                                                List.of("fact.one"),
                                                List.of("source.one"))),
                                List.of(
                                        new StructuredCheck(
                                                "check.one",
                                                "Check this evidence.",
                                                List.of("fact.one"),
                                                List.of("source.one")))));
        ModelDescriptor model =
                new ModelDescriptor(
                        "test/local",
                        "Local\u001B[31m Model",
                        "1",
                        new ModelCapabilities(true, true, true, 16_000, 8_192));

        String english =
                renderer.render(
                        output,
                        model,
                        Duration.ofMillis(12),
                        new RenderOptions(
                                RenderLanguage.ENGLISH, RenderStyle.BOX, 60, false, false, false));
        String turkish =
                renderer.render(
                        output,
                        model,
                        Duration.ofMillis(12),
                        new RenderOptions(
                                RenderLanguage.TURKISH,
                                RenderStyle.COMPACT,
                                80,
                                false,
                                false,
                                false));

        assertThat(english.lines()).allMatch(line -> line.codePointCount(0, line.length()) <= 60);
        assertThat(english)
                .contains("StackMate Local Explanation")
                .contains("Evidence: 1")
                .contains("Duration: 12 ms")
                .contains("Local JVM model")
                .contains("Network disabled")
                .doesNotContain("local-secret")
                .doesNotContain("\u001B");
        assertThat(turkish)
                .contains("StackMate Yerel Açıklama")
                .contains("Ağ devre dışı")
                .doesNotContain("\u001B");
    }
}
