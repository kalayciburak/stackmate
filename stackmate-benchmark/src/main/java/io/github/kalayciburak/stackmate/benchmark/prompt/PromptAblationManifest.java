package io.github.kalayciburak.stackmate.benchmark.prompt;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Frozen Phase 5.1 development, validation, and locked-final case assignments. */
public record PromptAblationManifest(
        String schemaVersion,
        String corpusId,
        String frozenAt,
        SourceManifests sourceManifests,
        String groundTruthSha256,
        Splits splits,
        List<String> abstentionRequiredCaseIds,
        List<String> unsupportedClaimTrapCaseIds,
        List<String> unknownOrInsufficientCaseIds) {

    public static final String RESOURCE = "/prompt-ablation/split-manifest.json";

    public PromptAblationManifest {
        schemaVersion = exact(schemaVersion, "1", "schemaVersion");
        corpusId = exact(corpusId, "stackmate-phase-5-1-prompt-ablation", "corpusId");
        frozenAt = text(frozenAt, "frozenAt");
        sourceManifests = Objects.requireNonNull(sourceManifests, "sourceManifests");
        groundTruthSha256 = sha256(groundTruthSha256, "groundTruthSha256");
        splits = Objects.requireNonNull(splits, "splits");
        abstentionRequiredCaseIds =
                immutable(abstentionRequiredCaseIds, "abstentionRequiredCaseIds");
        unsupportedClaimTrapCaseIds =
                immutable(unsupportedClaimTrapCaseIds, "unsupportedClaimTrapCaseIds");
        unknownOrInsufficientCaseIds =
                immutable(unknownOrInsufficientCaseIds, "unknownOrInsufficientCaseIds");

        Set<String> all = new HashSet<>();
        addUnique(all, splits.promptDevelopment(), "promptDevelopment");
        addUnique(all, splits.selectionValidation(), "selectionValidation");
        addUnique(all, splits.lockedFinal(), "lockedFinal");
        if (all.size() != 100) {
            throw new IllegalArgumentException(
                    "prompt-ablation split must contain 100 unique cases");
        }
        requireSubset(all, abstentionRequiredCaseIds, "abstentionRequiredCaseIds");
        requireSubset(all, unsupportedClaimTrapCaseIds, "unsupportedClaimTrapCaseIds");
        requireSubset(all, unknownOrInsufficientCaseIds, "unknownOrInsufficientCaseIds");
        if (!Set.copyOf(abstentionRequiredCaseIds)
                .equals(Set.copyOf(unknownOrInsufficientCaseIds))) {
            throw new IllegalArgumentException(
                    "restraint and unknown/insufficient flags must match");
        }
    }

    /** Loads the immutable split with strict JSON handling. */
    public static PromptAblationManifest load() {
        ObjectMapper mapper =
                new ObjectMapper()
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        try (InputStream input = PromptAblationManifest.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing prompt-ablation manifest: " + RESOURCE);
            }
            return mapper.readValue(input, PromptAblationManifest.class);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read prompt-ablation manifest", failure);
        }
    }

    /** Returns the immutable case IDs for one execution mode. */
    public List<String> caseIds(Dataset dataset) {
        return switch (dataset) {
            case PROMPT_DEVELOPMENT -> splits.promptDevelopment();
            case SELECTION_VALIDATION -> splits.selectionValidation();
            case LOCKED_FINAL -> splits.lockedFinal();
            case FULL_CONFIRMATION ->
                    java.util.stream.Stream.of(
                                    splits.promptDevelopment(),
                                    splits.selectionValidation(),
                                    splits.lockedFinal())
                            .flatMap(List::stream)
                            .toList();
        };
    }

    public enum Dataset {
        PROMPT_DEVELOPMENT,
        SELECTION_VALIDATION,
        LOCKED_FINAL,
        FULL_CONFIRMATION
    }

    public record SourceManifests(SourceManifest v2, SourceManifest blind) {
        public SourceManifests {
            v2 = Objects.requireNonNull(v2, "v2");
            blind = Objects.requireNonNull(blind, "blind");
        }
    }

    public record SourceManifest(String resource, String sha256) {
        public SourceManifest {
            resource = text(resource, "resource");
            sha256 = PromptAblationManifest.sha256(sha256, "sha256");
        }
    }

    public record Splits(
            List<String> promptDevelopment,
            List<String> selectionValidation,
            List<String> lockedFinal) {
        public Splits {
            promptDevelopment = immutable(promptDevelopment, "promptDevelopment");
            selectionValidation = immutable(selectionValidation, "selectionValidation");
            lockedFinal = immutable(lockedFinal, "lockedFinal");
            if (promptDevelopment.size() != 40
                    || selectionValidation.size() != 20
                    || lockedFinal.size() != 40) {
                throw new IllegalArgumentException("prompt-ablation split sizes must be 40/20/40");
            }
        }
    }

    private static void addUnique(Set<String> all, List<String> values, String field) {
        for (String value : values) {
            if (!all.add(value)) {
                throw new IllegalArgumentException("duplicate case across splits: " + field);
            }
        }
    }

    private static void requireSubset(Set<String> all, List<String> values, String field) {
        if (!all.containsAll(values) || new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException(field + " must contain unique known case IDs");
        }
    }

    private static List<String> immutable(List<String> values, String field) {
        Objects.requireNonNull(values, field);
        return values.stream().map(value -> text(value, field)).toList();
    }

    private static String exact(String value, String expected, String field) {
        if (!expected.equals(Objects.requireNonNull(value, field))) {
            throw new IllegalArgumentException(field + " must be " + expected);
        }
        return value;
    }

    private static String sha256(String value, String field) {
        value = text(value, field);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256");
        }
        return value;
    }

    private static String text(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
