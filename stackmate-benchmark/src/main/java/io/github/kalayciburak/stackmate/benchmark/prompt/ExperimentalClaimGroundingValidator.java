package io.github.kalayciburak.stackmate.benchmark.prompt;

import io.github.kalayciburak.stackmate.inference.InferenceRequest;
import io.github.kalayciburak.stackmate.inference.StructuredInferenceOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Research-only lexical claim check; deliberately not part of the production validator. */
public final class ExperimentalClaimGroundingValidator {

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+", Pattern.UNICODE_CASE);
    private static final Pattern QUANTIFIED_CAUSAL_CLAIM =
            Pattern.compile(
                    "(?iu)\\b(?:due to|caused by|because of)\\s+(?:an?\\s+)?(?:high|low|increased|decreased|excessive)\\s+[\\p{L}\\p{N}-]+(?:\\s+[\\p{L}\\p{N}-]+){0,2}");
    private static final Set<String> ALTERNATIVE_STOP_WORDS =
            Set.of(
                    "a", "an", "and", "as", "at", "be", "because", "by", "caused", "due", "for",
                    "from", "in", "is", "it", "of", "on", "or", "the", "this", "to", "was", "with");

    /** Returns true when causal prose exceeds selected-cause and used-evidence vocabulary. */
    public boolean hasUnsupportedClaim(InferenceRequest request, StructuredInferenceOutput output) {
        if (output.selectedCauseIds().isEmpty()) {
            return false;
        }
        String text = output.summary() + ' ' + output.explanation();
        String grounded = selectedCausalGrounding(request, output);
        return containsUngroundedAlternative(text, grounded)
                || containsUngroundedQuantifiedClaim(text, grounded);
    }

    private String selectedCausalGrounding(
            InferenceRequest request, StructuredInferenceOutput output) {
        StringBuilder text = new StringBuilder();
        Set<String> usedEvidence = Set.copyOf(output.usedEvidenceIds());
        request.facts().stream()
                .filter(fact -> usedEvidence.contains(fact.id()))
                .forEach(fact -> text.append(fact.value()).append(' '));
        request.candidateCauses().stream()
                .filter(candidate -> output.selectedCauseIds().contains(candidate.id()))
                .forEach(
                        candidate ->
                                text.append(candidate.id())
                                        .append(' ')
                                        .append(candidate.text())
                                        .append(' '));
        request.terminology()
                .forEach(
                        term ->
                                text.append(term.term())
                                        .append(' ')
                                        .append(term.definition())
                                        .append(' '));
        return text.toString();
    }

    private boolean containsUngroundedAlternative(String text, String grounded) {
        List<String> outputTokens = words(text);
        Set<String> groundedTokens = Set.copyOf(words(grounded));
        for (int index = 0; index < outputTokens.size(); index++) {
            if (!outputTokens.get(index).equals("or")) {
                continue;
            }
            int left = Math.max(0, index - 3);
            int right = Math.min(outputTokens.size(), index + 4);
            for (int cursor = left; cursor < right; cursor++) {
                String token = outputTokens.get(cursor);
                if (cursor != index
                        && !ALTERNATIVE_STOP_WORDS.contains(token)
                        && !groundedTokens.contains(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsUngroundedQuantifiedClaim(String text, String grounded) {
        Matcher matcher = QUANTIFIED_CAUSAL_CLAIM.matcher(text);
        String normalizedGrounding = grounded.toLowerCase(Locale.ROOT);
        while (matcher.find()) {
            if (!normalizedGrounding.contains(matcher.group().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private List<String> words(String value) {
        List<String> words = new ArrayList<>();
        Matcher matcher = WORD.matcher(splitCamelCase(value).toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            words.add(matcher.group());
        }
        return words;
    }

    private String splitCamelCase(String value) {
        return value.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
    }
}
