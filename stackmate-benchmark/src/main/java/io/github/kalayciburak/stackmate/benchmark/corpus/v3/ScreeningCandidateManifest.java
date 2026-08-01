package io.github.kalayciburak.stackmate.benchmark.corpus.v3;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Strict loader for the immutable Phase 4 screening candidate manifest. */
public record ScreeningCandidateManifest(
        String schemaVersion,
        String manifestId,
        String frozenAt,
        long maximumTotalParameters,
        RuntimeArtifact runtime,
        List<ScreeningCandidate> candidates) {

    public static final String RESOURCE = "/phase4-candidates/manifest.json";

    public ScreeningCandidateManifest {
        schemaVersion = exact(schemaVersion, "1", "schemaVersion");
        manifestId = exact(manifestId, "stackmate-phase4-candidates", "manifestId");
        frozenAt = text(frozenAt, "frozenAt");
        if (maximumTotalParameters != 2_000_000_000L) {
            throw new IllegalArgumentException("maximumTotalParameters must be 2000000000");
        }
        runtime = Objects.requireNonNull(runtime, "runtime");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        if (candidates.size() != 4) {
            throw new IllegalArgumentException("candidate manifest must contain four entries");
        }
        var ids = new HashSet<String>();
        for (ScreeningCandidate candidate : candidates) {
            if (!ids.add(candidate.id())) {
                throw new IllegalArgumentException("duplicate candidate ID");
            }
            if (candidate.eligible() && candidate.totalParameters() > maximumTotalParameters) {
                throw new IllegalArgumentException("eligible candidate exceeds the parameter cap");
            }
        }
    }

    /** Loads the frozen candidate and runtime identities. */
    public static ScreeningCandidateManifest load() {
        ObjectMapper mapper =
                new ObjectMapper()
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        try (InputStream input = ScreeningCandidateManifest.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing candidate manifest: " + RESOURCE);
            }
            return mapper.readValue(input, ScreeningCandidateManifest.class);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read candidate manifest", failure);
        }
    }

    /** Returns a candidate by its stable ID. */
    public ScreeningCandidate requireCandidate(String id) {
        return candidates.stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown candidate ID: " + id));
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

    /** One pinned official runtime archive identity. */
    public record RuntimeArtifact(
            String release,
            String commit,
            String windowsArtifact,
            long expectedSize,
            String sha256) {

        private static final Pattern REVISION = Pattern.compile("[a-f0-9]{40}");
        private static final Pattern SHA_256 = Pattern.compile("[a-f0-9]{64}");

        public RuntimeArtifact {
            release = exact(release, "b10221", "release");
            if (!REVISION.matcher(Objects.requireNonNull(commit, "commit")).matches()) {
                throw new IllegalArgumentException("commit is invalid");
            }
            windowsArtifact =
                    exact(windowsArtifact, "llama-b10221-bin-win-cpu-x64.zip", "windowsArtifact");
            if (expectedSize <= 0) {
                throw new IllegalArgumentException("runtime expectedSize must be positive");
            }
            if (!SHA_256.matcher(Objects.requireNonNull(sha256, "sha256")).matches()) {
                throw new IllegalArgumentException("runtime sha256 is invalid");
            }
        }
    }
}
