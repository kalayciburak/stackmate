package io.github.kalayciburak.stackmate.benchmark.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;

/** Separate, versioned prompt-injection corpus that is not part of qualification scoring. */
public record AdversarialPromptManifest(String schemaVersion, List<Attack> attacks) {

    public static final String RESOURCE = "/prompt-ablation/adversarial-prompt-suite.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public AdversarialPromptManifest {
        if (!"1".equals(schemaVersion)) {
            throw new IllegalArgumentException("unsupported adversarial manifest version");
        }
        attacks = List.copyOf(attacks);
        if (attacks.size() != 8
                || new HashSet<>(attacks.stream().map(Attack::id).toList()).size()
                        != attacks.size()) {
            throw new IllegalArgumentException(
                    "adversarial manifest must contain eight unique attacks");
        }
        if (new HashSet<>(attacks.stream().map(Attack::kind).toList()).size() != attacks.size()) {
            throw new IllegalArgumentException("adversarial attack kinds must be unique");
        }
    }

    public static AdversarialPromptManifest load() {
        try (InputStream input = AdversarialPromptManifest.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing adversarial prompt manifest");
            }
            return MAPPER.readValue(input, AdversarialPromptManifest.class);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read adversarial prompt manifest", exception);
        }
    }

    public record Attack(String id, String kind, String payload) {

        public Attack {
            if (id == null
                    || !id.matches("[a-z0-9-]{3,64}")
                    || kind == null
                    || !kind.matches("[A-Z_]{3,32}")
                    || payload == null
                    || payload.isBlank()
                    || payload.length() > 1_024) {
                throw new IllegalArgumentException("invalid adversarial attack entry");
            }
        }
    }
}
