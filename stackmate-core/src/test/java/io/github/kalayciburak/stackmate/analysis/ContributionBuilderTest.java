package io.github.kalayciburak.stackmate.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kalayciburak.stackmate.evidence.EvidenceReliability;
import io.github.kalayciburak.stackmate.evidence.EvidenceSensitivity;
import io.github.kalayciburak.stackmate.evidence.EvidenceType;
import org.junit.jupiter.api.Test;

class ContributionBuilderTest {

    @Test
    void mergesCompatibleCauseAndCheckEvidenceInInsertionOrder() {
        ContributionBuilder builder = new ContributionBuilder("test-builder");
        addEvidence(builder, "evidence.one", "one");
        addEvidence(builder, "evidence.two", "two");

        builder.addCause(
                "cause.test",
                DiagnosticCategory.GENERAL_JAVA,
                "Title",
                "Explanation",
                "evidence.one");
        builder.addCause(
                "cause.test",
                DiagnosticCategory.GENERAL_JAVA,
                "Title",
                "Explanation",
                "evidence.two");
        builder.addCheck("check.test", "Check", "evidence.one");
        builder.addCheck("check.test", "Check", "evidence.two");

        AnalysisContribution contribution = builder.build();

        assertThat(contribution.causes().getFirst().evidenceIds())
                .containsExactly("evidence.one", "evidence.two");
        assertThat(contribution.checks().getFirst().evidenceIds())
                .containsExactly("evidence.one", "evidence.two");
    }

    @Test
    void rejectsConflictingEvidenceCauseAndCheckDefinitions() {
        ContributionBuilder evidenceBuilder = new ContributionBuilder("evidence-builder");
        addEvidence(evidenceBuilder, "evidence.same", "one");
        assertThatThrownBy(() -> addEvidence(evidenceBuilder, "evidence.same", "two"))
                .isInstanceOf(IllegalArgumentException.class);

        ContributionBuilder causeBuilder = new ContributionBuilder("cause-builder");
        addEvidence(causeBuilder, "evidence.one", "one");
        causeBuilder.addCause(
                "cause.same",
                DiagnosticCategory.GENERAL_JAVA,
                "Title",
                "Explanation",
                "evidence.one");
        assertThatThrownBy(
                        () ->
                                causeBuilder.addCause(
                                        "cause.same",
                                        DiagnosticCategory.TIMEOUT,
                                        "Other",
                                        "Other",
                                        "evidence.one"))
                .isInstanceOf(IllegalArgumentException.class);

        ContributionBuilder checkBuilder = new ContributionBuilder("check-builder");
        addEvidence(checkBuilder, "evidence.one", "one");
        checkBuilder.addCheck("check.same", "First", "evidence.one");
        assertThatThrownBy(() -> checkBuilder.addCheck("check.same", "Different", "evidence.one"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsClassWrapperAndMessagePatternEvidenceWithoutCopyingMessage() {
        ContributionBuilder builder = new ContributionBuilder("fact-builder");
        AnalyzerSupport.NodeFact fact =
                new AnalyzerSupport.NodeFact(
                        0,
                        "throwable.causeChain[0]",
                        IncidentFixtures.wrapper(
                                "java.util.concurrent.CompletionException", "secret text", 0));

        builder.addClassEvidence("fact", fact);
        builder.addWrapperEvidence("fact", fact);
        builder.addMessagePatternEvidence("fact", fact, "safe-pattern");

        assertThat(builder.build().evidence())
                .extracting(item -> item.type())
                .containsExactly(
                        EvidenceType.EXCEPTION_CLASS,
                        EvidenceType.WRAPPER_CLASS,
                        EvidenceType.MESSAGE_PATTERN);
        assertThat(builder.build().evidence())
                .extracting(item -> item.value())
                .doesNotContain("secret text");
    }

    private String addEvidence(ContributionBuilder builder, String id, String value) {
        return builder.addEvidence(
                id,
                EvidenceType.ANALYZER_RESULT,
                "test",
                value,
                EvidenceSensitivity.PUBLIC,
                EvidenceReliability.MEDIUM);
    }
}
