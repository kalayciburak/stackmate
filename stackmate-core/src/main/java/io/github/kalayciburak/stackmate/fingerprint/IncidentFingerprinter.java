package io.github.kalayciburak.stackmate.fingerprint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Produces deterministic fingerprints without retaining incident text. */
public final class IncidentFingerprinter {

    /** Creates the deterministic incident fingerprinter. */
    public IncidentFingerprinter() {}

    static final int MAX_COMPONENT_CHARS = 256;
    static final int MAX_FRAME_CHARS = 512;

    /**
     * Hashes normalized incident characteristics into a stable privacy-safe identifier.
     *
     * @param input bounded framework-neutral fingerprint input
     * @return lowercase SHA-256 incident fingerprint
     */
    public ExceptionFingerprint fingerprint(FingerprintInput input) {
        Objects.requireNonNull(input, "input");
        MessageDigest digest = sha256();
        add(digest, normalizeComponent(input.analyzerVersion(), "analyzerVersion"));
        add(digest, normalizeComponent(input.rootType(), "rootType"));
        add(digest, normalizeComponent(input.category(), "category"));
        add(digest, MessagePatternNormalizer.normalize(input.rootMessage()));

        List<String> frames =
                input.topApplicationFrames().stream()
                        .map(IncidentFingerprinter::normalizeFrame)
                        .toList();
        add(digest, Integer.toString(frames.size()));
        frames.forEach(frame -> add(digest, frame));

        List<String> factTypes =
                input.stableFactTypes().stream()
                        .map(fact -> normalizeComponent(fact, "stableFactType"))
                        .distinct()
                        .sorted(Comparator.naturalOrder())
                        .toList();
        add(digest, Integer.toString(factTypes.size()));
        factTypes.forEach(fact -> add(digest, fact));
        return new ExceptionFingerprint(HexFormat.of().formatHex(digest.digest()));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java runtime does not provide SHA-256", exception);
        }
    }

    private static void add(MessageDigest digest, String component) {
        byte[] bytes = component.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static String normalizeComponent(String value, String name) {
        Objects.requireNonNull(value, name);
        String bounded = value.substring(0, Math.min(value.length(), MAX_COMPONENT_CHARS));
        String normalized = bounded.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeFrame(String frame) {
        Objects.requireNonNull(frame, "topApplicationFrame");
        String bounded = frame.substring(0, Math.min(frame.length(), MAX_FRAME_CHARS));
        String normalized = bounded.strip().replaceFirst("(?i)^at\\s+", "");
        normalized = normalized.replaceFirst("\\([^)]*\\)$", "");
        normalized = normalized.replaceAll("(?i)/0x[0-9a-f]+", "/<generated>");
        normalized = normalized.replaceAll("\\$\\$Lambda\\$\\d+", "<lambda>");
        return normalizeComponent(normalized, "topApplicationFrame");
    }
}
