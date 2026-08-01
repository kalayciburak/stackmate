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
        InferenceRequest request =
                new InferenceRequest(
                        "2",
                        "2",
                        "incident.one",
                        InferenceLanguage.ENGLISH,
                        new InferenceIncident(
                                io.github.kalayciburak.stackmate.analysis.DiagnosticCategory
                                        .UNKNOWN,
                                io.github.kalayciburak.stackmate.analysis.DiagnosticStrength
                                        .MEDIUM),
                        List.of(new InferenceFact("fact.one", "KNOWN_RULE", "supplied evidence")),
                        List.of(
                                new InferenceCauseCandidate(
                                        "cause.one",
                                        "This cause uses evidence.",
                                        List.of("fact.one"))),
                        List.of(
                                new InferenceCheckCandidate(
                                        "check.one", "Check this evidence.", List.of("fact.one"))),
                        List.of(),
                        512);
        ValidatedInferenceOutput output =
                new ValidatedInferenceOutput(
                        request,
                        new StructuredInferenceOutput(
                                "2",
                                InferenceLanguage.ENGLISH,
                                "This summary has password=local-secret\u001B[2J",
                                "The supplied evidence explains the failure.",
                                List.of("cause.one"),
                                List.of("check.one"),
                                List.of("fact.one"),
                                List.of()));
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
                .contains("Evidence used: 1")
                .contains("Duration: 12 ms")
                .contains("Experimental local JVM model")
                .contains("StackMate made no external network call")
                .doesNotContain("local-secret")
                .doesNotContain("\u001B");
        assertThat(turkish)
                .contains("StackMate Yerel Açıklama")
                .contains("StackMate harici ağ çağrısı")
                .contains("yapmadı")
                .doesNotContain("\u001B");
    }
}
