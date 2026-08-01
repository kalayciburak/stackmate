package io.github.kalayciburak.stackmate.inference;

import io.github.kalayciburak.stackmate.analysis.DeterministicResult;
import io.github.kalayciburak.stackmate.analysis.DiagnosticCategory;
import io.github.kalayciburak.stackmate.evidence.EvidenceItem;
import io.github.kalayciburak.stackmate.incident.StackFrameSnapshot;
import io.github.kalayciburak.stackmate.privacy.Sanitizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Converts deterministic output into a bounded, evidence-only inference request. */
public final class DeterministicInferenceRequestFactory {

    private static final int MAX_FACT_VALUE = 2_048;
    private static final int MAX_CAUSE_TEXT = 2_048;
    private static final int MAX_CHECK_TEXT = 1_024;

    private final Sanitizer sanitizer;

    public DeterministicInferenceRequestFactory(Sanitizer sanitizer) {
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
    }

    public InferenceRequest create(
            DeterministicResult result,
            InferenceLanguage language,
            int maximumInputCharacters,
            int maximumOutputCharacters) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(language, "language");
        int budget = Math.min(maximumInputCharacters, InferenceRequest.MAXIMUM_TOTAL_CHARACTERS);
        if (budget < 1) {
            throw new IllegalArgumentException("maximumInputCharacters must be positive");
        }

        List<InferenceFact> facts = facts(result, budget);
        if (facts.isEmpty()) {
            throw new IllegalArgumentException("maximumInputCharacters is too small for one fact");
        }
        int used = facts.stream().mapToInt(this::factCost).sum();
        Set<String> factIds = new HashSet<>();
        facts.forEach(fact -> factIds.add(fact.id()));

        List<InferenceCauseCandidate> causes = new ArrayList<>();
        for (var cause : result.diagnosis().causes()) {
            if (causes.size() >= InferenceRequest.MAXIMUM_CAUSES) {
                break;
            }
            List<String> references =
                    cause.evidenceIds().stream().filter(factIds::contains).limit(32).toList();
            if (references.isEmpty()) {
                continue;
            }
            String text =
                    bounded(
                            "causeText",
                            cause.title() + ". " + cause.explanation(),
                            MAX_CAUSE_TEXT);
            int cost = cause.id().length() + text.length();
            if (used + cost > budget) {
                continue;
            }
            causes.add(new InferenceCauseCandidate(cause.id(), text, references));
            used += cost;
        }

        List<InferenceCheckCandidate> checks = new ArrayList<>();
        for (var check : result.diagnosis().checks()) {
            if (checks.size() >= InferenceRequest.MAXIMUM_CHECKS) {
                break;
            }
            List<String> references =
                    check.evidenceIds().stream().filter(factIds::contains).limit(32).toList();
            if (references.isEmpty()) {
                continue;
            }
            String text = bounded("checkText", check.instruction(), MAX_CHECK_TEXT);
            int cost = check.id().length() + text.length();
            if (used + cost > budget) {
                continue;
            }
            checks.add(new InferenceCheckCandidate(check.id(), text, references));
            used += cost;
        }

        var primaryCause = result.diagnosis().causes().getFirst();
        List<InferenceTerminology> terminology = terminology(primaryCause.category(), language);
        return new InferenceRequest(
                InferenceRequest.SCHEMA_VERSION,
                InferenceRequest.PROMPT_VERSION,
                result.fingerprint().value(),
                language,
                new InferenceIncident(primaryCause.category(), primaryCause.strength()),
                facts,
                causes,
                checks,
                terminology,
                Math.min(
                        Math.max(1, maximumOutputCharacters),
                        InferenceRequest.MAXIMUM_OUTPUT_CHARACTERS));
    }

    private List<InferenceFact> facts(DeterministicResult result, int budget) {
        List<InferenceFact> facts = new ArrayList<>();
        int used = 0;
        for (EvidenceItem evidence : result.diagnosis().evidence()) {
            if (facts.size() >= InferenceRequest.MAXIMUM_FACTS) {
                break;
            }
            String type = evidence.type().name();
            String value = bounded("evidenceValue", evidence.value(), MAX_FACT_VALUE);
            int remaining = budget - used - evidence.id().length() - type.length();
            if (remaining < 1) {
                break;
            }
            value = truncate(value, Math.min(value.length(), remaining));
            if (value.isBlank()) {
                break;
            }
            InferenceFact fact = new InferenceFact(evidence.id(), type, value);
            facts.add(fact);
            used += factCost(fact);
        }
        for (int index = 0;
                index < result.incident().applicationFrames().size()
                        && facts.size() < InferenceRequest.MAXIMUM_FACTS;
                index++) {
            InferenceFact fact =
                    applicationFact(index, result.incident().applicationFrames().get(index));
            if (used + factCost(fact) > budget) {
                break;
            }
            facts.add(fact);
            used += factCost(fact);
        }
        return facts;
    }

    private InferenceFact applicationFact(int index, StackFrameSnapshot frame) {
        String value =
                bounded("className", frame.className(), 256)
                        + "#"
                        + bounded("methodName", frame.methodName(), 128)
                        + "("
                        + bounded(
                                "fileName",
                                frame.fileName().isBlank() ? "unknown" : frame.fileName(),
                                256)
                        + (frame.lineNumber() >= 0 ? ":" + frame.lineNumber() : "")
                        + ")";
        return new InferenceFact("fact.application." + index, "APPLICATION_LOCATION", value);
    }

    private List<InferenceTerminology> terminology(
            DiagnosticCategory category, InferenceLanguage language) {
        if (category != DiagnosticCategory.DATABASE_CONSTRAINT) {
            return List.of();
        }
        return language == InferenceLanguage.TURKISH
                ? List.of(
                        new InferenceTerminology(
                                "veritabanı kısıtı",
                                "Verinin uyması gereken ve veritabanının uyguladığı bütünlük kuralı"))
                : List.of(
                        new InferenceTerminology(
                                "database constraint",
                                "An integrity rule enforced by the database for stored data"));
    }

    private int factCost(InferenceFact fact) {
        return fact.id().length() + fact.type().length() + fact.value().length();
    }

    private String bounded(String field, String value, int maximum) {
        String safe = sanitizer.sanitize(field, Objects.requireNonNull(value, field));
        if (safe.isBlank()) {
            safe = "unknown";
        }
        return truncate(safe, maximum);
    }

    private static String truncate(String value, int maximum) {
        if (value.length() <= maximum) {
            return value;
        }
        int end = maximum;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }
}
