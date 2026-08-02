package io.github.kalayciburak.stackmate.benchmark.prompt;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Exactly four versioned prompt variants used by the Phase 5.1 primary comparison. */
public record PromptVariantManifest(
        String schemaVersion,
        String estimateMethod,
        int maximumCharacters,
        List<PromptVariant> variants) {

    public static final String RESOURCE = "/prompt-ablation/prompt-manifest.json";

    public PromptVariantManifest {
        schemaVersion = exact(schemaVersion, "1", "schemaVersion");
        estimateMethod = exact(estimateMethod, "ceil(characters/4)", "estimateMethod");
        if (maximumCharacters < 1 || maximumCharacters > 8_192) {
            throw new IllegalArgumentException("maximumCharacters is outside the research bound");
        }
        variants = List.copyOf(Objects.requireNonNull(variants, "variants"));
        if (variants.size() != 4
                || !variants.stream()
                        .map(PromptVariant::id)
                        .collect(java.util.stream.Collectors.toSet())
                        .equals(Set.of("P0", "P1", "P2", "P3"))) {
            throw new IllegalArgumentException("exactly P0, P1, P2, and P3 are required");
        }
        if (variants.stream().filter(PromptVariant::historicalControl).count() != 1
                || !variants.getFirst().id().equals("P0")
                || !variants.getFirst().historicalControl()) {
            throw new IllegalArgumentException("P0 must be the only historical control");
        }
        for (PromptVariant variant : variants) {
            String prompt = readPrompt(variant.resource());
            if (prompt.length() != variant.characters()
                    || variant.characters() > maximumCharacters
                    || estimate(prompt.length()) != variant.estimatedTokens()
                    || !sha256(prompt).equals(variant.sha256())) {
                throw new IllegalArgumentException("prompt metadata drift: " + variant.id());
            }
        }
    }

    /** Loads the immutable variant inventory with strict JSON handling. */
    public static PromptVariantManifest load() {
        ObjectMapper mapper =
                new ObjectMapper()
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        try (InputStream input = PromptVariantManifest.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing prompt variant manifest: " + RESOURCE);
            }
            return mapper.readValue(input, PromptVariantManifest.class);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read prompt variant manifest", failure);
        }
    }

    /** Returns one prompt's exact stripped UTF-8 text. */
    public String prompt(String id) {
        return readPrompt(require(id).resource());
    }

    /** Returns one declared variant or fails closed. */
    public PromptVariant require(String id) {
        return variants.stream()
                .filter(variant -> variant.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown prompt variant: " + id));
    }

    private static String readPrompt(String resource) {
        try (InputStream input = PromptVariantManifest.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("missing prompt resource: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read prompt resource: " + resource, failure);
        }
    }

    private static int estimate(int characters) {
        return (characters + 3) / 4;
    }

    private static String sha256(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    public record PromptVariant(
            String id,
            String version,
            String resource,
            String sha256,
            int characters,
            int estimatedTokens,
            int negativeExamples,
            boolean historicalControl) {
        public PromptVariant {
            if (!Objects.requireNonNull(id, "id").matches("P[0-3]")) {
                throw new IllegalArgumentException("prompt id is not P0-P3");
            }
            version = text(version, "version");
            resource = text(resource, "resource");
            if (!resource.startsWith("/prompt-ablation/prompts/") || !resource.endsWith(".txt")) {
                throw new IllegalArgumentException(
                        "prompt resource is outside the versioned directory");
            }
            sha256 = PromptVariantManifest.sha256(sha256, "sha256");
            if (characters < 1 || estimatedTokens < 1) {
                throw new IllegalArgumentException("prompt size metadata must be positive");
            }
            if (negativeExamples < 0 || negativeExamples > 4) {
                throw new IllegalArgumentException("no prompt may contain more than four examples");
            }
        }
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
