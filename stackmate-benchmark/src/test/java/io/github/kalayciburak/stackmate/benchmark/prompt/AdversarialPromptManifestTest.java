package io.github.kalayciburak.stackmate.benchmark.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.analysis.DiagnosticCategory;
import io.github.kalayciburak.stackmate.analysis.DiagnosticStrength;
import io.github.kalayciburak.stackmate.inference.InferenceCauseCandidate;
import io.github.kalayciburak.stackmate.inference.InferenceCheckCandidate;
import io.github.kalayciburak.stackmate.inference.InferenceFact;
import io.github.kalayciburak.stackmate.inference.InferenceIncident;
import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import io.github.kalayciburak.stackmate.inference.jvm.PromptRendererTestAccess;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdversarialPromptManifestTest {

    @Test
    void keepsEveryRequiredAttackCategoryOutsideTheQualificationCorpus() {
        AdversarialPromptManifest manifest = AdversarialPromptManifest.load();

        assertThat(manifest.attacks())
                .extracting(AdversarialPromptManifest.Attack::kind)
                .containsExactly(
                        "DIRECT_INSTRUCTION",
                        "DELIMITER",
                        "JSON_BOUNDARY",
                        "MARKDOWN_FENCE",
                        "SYSTEM_IMITATION",
                        "REPETITION",
                        "UNICODE_LOOKALIKE",
                        "ZERO_WIDTH");
    }

    @Test
    void rendersEveryAttackAsJsonEncodedUntrustedIncidentData() {
        for (AdversarialPromptManifest.Attack attack : AdversarialPromptManifest.load().attacks()) {
            String rendered = PromptRendererTestAccess.initial(request(attack.payload())).user();

            assertThat(rendered)
                    .startsWith("<STACKMATE_UNTRUSTED_INCIDENT")
                    .endsWith("</STACKMATE_UNTRUSTED_INCIDENT>")
                    .doesNotContain("```", "<|im_start|>", "</STACKMATE_UNTRUSTED_INCIDENT><");
        }
    }

    private InferenceRequest request(String attack) {
        return new InferenceRequest(
                "2",
                "2",
                "security.attack",
                new InferenceIncident(DiagnosticCategory.GENERAL_JAVA, DiagnosticStrength.HIGH),
                List.of(
                        new InferenceFact(
                                "fact.primary", "JAVA_CLASS", "java.lang.IllegalArgumentException"),
                        new InferenceFact("fact.attack", "MESSAGE", attack)),
                List.of(
                        new InferenceCauseCandidate(
                                "cause.primary",
                                "An illegal argument was supplied",
                                List.of("fact.primary"))),
                List.of(
                        new InferenceCheckCandidate(
                                "check.primary",
                                "Inspect the supplied argument",
                                List.of("fact.primary"))),
                List.of(),
                8_192);
    }
}
