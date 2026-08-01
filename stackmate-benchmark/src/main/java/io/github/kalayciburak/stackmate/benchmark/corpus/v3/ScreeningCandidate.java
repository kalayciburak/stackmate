package io.github.kalayciburak.stackmate.benchmark.corpus.v3;

import java.util.Objects;
import java.util.regex.Pattern;

/** One immutable Phase 4 screening artifact identity. */
public record ScreeningCandidate(
        String id,
        Tier tier,
        boolean eligible,
        String eligibilityNote,
        String officialRepository,
        String officialRevision,
        String architecture,
        long totalParameters,
        int contextCapability,
        String license,
        String thinkingBehavior,
        ChatTemplate chatTemplate,
        String ggufRepository,
        String ggufRevision,
        String artifactFile,
        long expectedSize,
        String sha256,
        String quantization,
        Provenance provenance) {

    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9.-]{2,63}");
    private static final Pattern REVISION = Pattern.compile("[a-f0-9]{40}");
    private static final Pattern SHA_256 = Pattern.compile("[a-f0-9]{64}");

    public ScreeningCandidate {
        id = matching(id, ID, "id");
        tier = Objects.requireNonNull(tier, "tier");
        eligibilityNote = text(eligibilityNote, "eligibilityNote");
        officialRepository = repository(officialRepository, "officialRepository");
        officialRevision = matching(officialRevision, REVISION, "officialRevision");
        architecture = text(architecture, "architecture");
        if (totalParameters <= 0 || contextCapability <= 0 || expectedSize <= 0) {
            throw new IllegalArgumentException("numeric model metadata must be positive");
        }
        license = exact(license, "Apache-2.0", "license");
        thinkingBehavior = text(thinkingBehavior, "thinkingBehavior");
        chatTemplate = Objects.requireNonNull(chatTemplate, "chatTemplate");
        ggufRepository = repository(ggufRepository, "ggufRepository");
        ggufRevision = matching(ggufRevision, REVISION, "ggufRevision");
        artifactFile = safeFile(artifactFile);
        sha256 = matching(sha256, SHA_256, "sha256");
        quantization = exact(quantization, "Q4_K_M", "quantization");
        provenance = Objects.requireNonNull(provenance, "provenance");
    }

    private static String repository(String value, String field) {
        String result = text(value, field);
        if (result.startsWith("/")
                || result.endsWith("/")
                || result.chars().filter(c -> c == '/').count() != 1) {
            throw new IllegalArgumentException(field + " must be an owner/repository identity");
        }
        return result;
    }

    private static String safeFile(String value) {
        String result = text(value, "artifactFile");
        if (!result.toLowerCase(java.util.Locale.ROOT).endsWith(".gguf")
                || result.contains("/")
                || result.contains("\\")
                || result.contains("..")) {
            throw new IllegalArgumentException("artifactFile must be a safe GGUF filename");
        }
        return result;
    }

    private static String matching(String value, Pattern pattern, String field) {
        String result = text(value, field);
        if (!pattern.matcher(result).matches()) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return result;
    }

    private static String exact(String value, String expected, String field) {
        if (!expected.equals(Objects.requireNonNull(value, field))) {
            throw new IllegalArgumentException(field + " must be " + expected);
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

    public enum Tier {
        MICRO,
        COMPACT,
        MID,
        INELIGIBLE_CONTROL
    }

    public enum ChatTemplate {
        CHATML,
        QWEN_NON_THINKING
    }

    public enum Provenance {
        OFFICIAL_GGUF,
        THIRD_PARTY_FEASIBILITY,
        THIRD_PARTY_HISTORICAL_CONTROL
    }
}
