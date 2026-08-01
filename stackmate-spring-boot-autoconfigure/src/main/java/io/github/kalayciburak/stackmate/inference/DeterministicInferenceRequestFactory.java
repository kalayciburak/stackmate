package io.github.kalayciburak.stackmate.inference;

import io.github.kalayciburak.stackmate.analysis.DeterministicResult;
import io.github.kalayciburak.stackmate.evidence.EvidenceItem;
import io.github.kalayciburak.stackmate.incident.StackFrameSnapshot;
import io.github.kalayciburak.stackmate.privacy.Sanitizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Converts deterministic output into a bounded, sanitized, reference-only inference request. */
public final class DeterministicInferenceRequestFactory {

    private static final int MAX_FACT_TEXT = 2_048;
    private static final int MAX_CAUSE_TITLE = 256;
    private static final int MAX_CAUSE_EXPLANATION = 2_048;
    private static final int MAX_CHECK_INSTRUCTION = 1_024;

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

        List<FactCandidate> candidates = factCandidates(result);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("deterministic result has no inference facts");
        }

        List<SourceLocation> locations = new ArrayList<>();
        int used = 0;
        int minimumFactCost = candidates.getFirst().id().length() + 1;
        for (int index = 0;
                index < result.incident().applicationFrames().size()
                        && locations.size() < InferenceRequest.MAXIMUM_SOURCE_LOCATIONS;
                index++) {
            SourceLocation location =
                    location(index, result.incident().applicationFrames().get(index));
            int cost = locationCost(location);
            if (used + cost + minimumFactCost > budget) {
                break;
            }
            locations.add(location);
            used += cost;
        }

        List<InferenceFact> facts = new ArrayList<>();
        List<String> locationIds =
                locations.isEmpty() ? List.of() : List.of(locations.getFirst().id());
        for (FactCandidate candidate : candidates) {
            if (facts.size() >= InferenceRequest.MAXIMUM_FACTS) {
                break;
            }
            int remaining = budget - used - candidate.id().length();
            if (remaining < 1) {
                break;
            }
            String text = truncate(candidate.text(), Math.min(MAX_FACT_TEXT, remaining));
            if (text.isBlank()) {
                break;
            }
            InferenceFact fact = new InferenceFact(candidate.id(), text, locationIds);
            facts.add(fact);
            used += fact.id().length() + fact.text().length();
        }
        if (facts.isEmpty()) {
            throw new IllegalArgumentException("maximumInputCharacters is too small for one fact");
        }

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
            String title = bounded("causeTitle", cause.title(), MAX_CAUSE_TITLE);
            String explanation =
                    bounded("causeExplanation", cause.explanation(), MAX_CAUSE_EXPLANATION);
            int cost = cause.id().length() + title.length() + explanation.length();
            if (used + cost > budget) {
                continue;
            }
            causes.add(new InferenceCauseCandidate(cause.id(), title, explanation, references));
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
            String instruction =
                    bounded("checkInstruction", check.instruction(), MAX_CHECK_INSTRUCTION);
            int cost = check.id().length() + instruction.length();
            if (used + cost > budget) {
                continue;
            }
            checks.add(new InferenceCheckCandidate(check.id(), instruction, references));
            used += cost;
        }

        return new InferenceRequest(
                result.fingerprint().value(),
                language,
                facts,
                causes,
                checks,
                locations,
                Math.min(
                        Math.max(1, maximumOutputCharacters),
                        InferenceRequest.MAXIMUM_OUTPUT_CHARACTERS));
    }

    private List<FactCandidate> factCandidates(DeterministicResult result) {
        List<FactCandidate> candidates = new ArrayList<>();
        for (EvidenceItem evidence : result.diagnosis().evidence()) {
            String text =
                    evidence.type().name()
                            + " | "
                            + safe("evidenceSource", evidence.source())
                            + " | "
                            + safe("evidenceValue", evidence.value());
            candidates.add(new FactCandidate(evidence.id(), truncate(text, MAX_FACT_TEXT)));
        }
        return candidates;
    }

    private SourceLocation location(int index, StackFrameSnapshot frame) {
        return new SourceLocation(
                "source.app." + index,
                bounded("className", frame.className(), 256),
                bounded("methodName", frame.methodName(), 128),
                bounded("fileName", frame.fileName(), 256),
                Math.max(-1, frame.lineNumber()));
    }

    private int locationCost(SourceLocation location) {
        return location.id().length()
                + location.className().length()
                + location.methodName().length()
                + location.fileName().length();
    }

    private String bounded(String field, String value, int maximum) {
        String safe = safe(field, value);
        if (safe.isBlank()) {
            safe = "unknown";
        }
        return truncate(safe, maximum);
    }

    private String safe(String field, String value) {
        return sanitizer.sanitize(field, Objects.requireNonNull(value, field));
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

    private record FactCandidate(String id, String text) {}
}
