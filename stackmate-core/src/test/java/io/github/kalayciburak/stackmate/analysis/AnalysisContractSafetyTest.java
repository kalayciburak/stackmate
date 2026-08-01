package io.github.kalayciburak.stackmate.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kalayciburak.stackmate.evidence.EvidenceItem;
import io.github.kalayciburak.stackmate.evidence.EvidenceReliability;
import io.github.kalayciburak.stackmate.evidence.EvidenceSensitivity;
import io.github.kalayciburak.stackmate.evidence.EvidenceType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisContractSafetyTest {

    @Test
    void rejectsUnstableIdentifiersAndBlankValues() {
        assertThatThrownBy(
                        () ->
                                new EvidenceItem(
                                        "Not Stable",
                                        EvidenceType.EXCEPTION_CLASS,
                                        "throwable.nodes[0]",
                                        "java.lang.Exception",
                                        EvidenceSensitivity.PUBLIC,
                                        EvidenceReliability.HIGH))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new CheckCandidate("check.valid", " ", List.of("evidence.valid")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defensivelyCopiesCandidateEvidenceReferences() {
        List<String> mutableIds = new ArrayList<>(List.of("evidence.valid"));
        CauseCandidate candidate =
                new CauseCandidate(
                        "cause.valid",
                        DiagnosticCategory.GENERAL_JAVA,
                        "A supported cause",
                        "A deterministic explanation",
                        mutableIds);

        mutableIds.add("evidence.late");

        assertThat(candidate.evidenceIds()).containsExactly("evidence.valid");
        assertThatThrownBy(() -> candidate.evidenceIds().add("evidence.other"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void retainsEvidenceSensitivityForLaterOutputBoundaries() {
        EvidenceItem item =
                new EvidenceItem(
                        "jdbc.constraint.0",
                        EvidenceType.DATABASE_CONSTRAINT,
                        "throwable.nodes[0].database",
                        "uk_account_email",
                        EvidenceSensitivity.SENSITIVE,
                        EvidenceReliability.HIGH);

        assertThat(item.sensitivity()).isEqualTo(EvidenceSensitivity.SENSITIVE);
    }

    @Test
    void contributionRejectsDuplicateIdsAndUnresolvedEvidenceReferences() {
        EvidenceItem item = evidence("evidence.one", "one");
        CauseCandidate cause =
                new CauseCandidate(
                        "cause.one",
                        DiagnosticCategory.GENERAL_JAVA,
                        "Title",
                        "Explanation",
                        List.of("evidence.one"));

        assertThatThrownBy(
                        () ->
                                new AnalysisContribution(
                                        "test", List.of(item, item), List.of(cause), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new AnalysisContribution(
                                        "test",
                                        List.of(item),
                                        List.of(
                                                new CauseCandidate(
                                                        "cause.missing",
                                                        DiagnosticCategory.UNKNOWN,
                                                        "Title",
                                                        "Explanation",
                                                        List.of("evidence.missing"))),
                                        List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new AnalysisContribution(
                                        "test", List.of(item), List.of(cause, cause), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        CheckCandidate check = new CheckCandidate("check.one", "Check", List.of("evidence.one"));
        assertThatThrownBy(
                        () ->
                                new AnalysisContribution(
                                        "test",
                                        List.of(item),
                                        List.of(cause),
                                        List.of(check, check)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void diagnosisRejectsEmptyCausesDuplicateTokensAndUnresolvedReferences() {
        EvidenceItem item = evidence("evidence.one", "one");
        DiagnosedCause cause =
                new DiagnosedCause(
                        "cause.one",
                        DiagnosticCategory.GENERAL_JAVA,
                        "Title",
                        "Explanation",
                        DiagnosticStrength.LOW,
                        List.of("evidence.one"));

        assertThatThrownBy(
                        () -> new Diagnosis(List.of(item), List.of(), List.of(), List.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new Diagnosis(
                                        List.of(item),
                                        List.of(cause),
                                        List.of(),
                                        List.of("test@1", "test@1"),
                                        false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new Diagnosis(
                                        List.of(item),
                                        List.of(cause),
                                        List.of(
                                                new CheckCandidate(
                                                        "check.missing",
                                                        "Check",
                                                        List.of("evidence.missing"))),
                                        List.of("test@1"),
                                        false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new Diagnosis(
                                        List.of(item),
                                        List.of(cause),
                                        List.of(),
                                        List.of("bad token"),
                                        false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private EvidenceItem evidence(String id, String value) {
        return new EvidenceItem(
                id,
                EvidenceType.ANALYZER_RESULT,
                "test",
                value,
                EvidenceSensitivity.PUBLIC,
                EvidenceReliability.MEDIUM);
    }
}
