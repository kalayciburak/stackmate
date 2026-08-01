package io.github.kalayciburak.stackmate.fingerprint;

import java.util.Locale;
import java.util.regex.Pattern;

final class MessagePatternNormalizer {
    static final int MAX_MESSAGE_CHARS = 4_096;

    private static final Pattern BEARER_TOKEN =
            Pattern.compile("(?i)\\bbearer\\s+[a-z0-9._~+/=-]+");
    private static final Pattern SECRET_ASSIGNMENT =
            Pattern.compile(
                    "(?i)\\b(token|access[_ -]?token|refresh[_ -]?token|api[_ -]?key|password|secret|authorization)"
                            + "\\s*[:=]\\s*([^\\s,;]+)");
    private static final Pattern REQUEST_ID_ASSIGNMENT =
            Pattern.compile("(?i)\\b(request|correlation|trace|span)[_ -]?id\\s*[:=]\\s*[^\\s,;]+");
    private static final Pattern UUID =
            Pattern.compile(
                    "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b");
    private static final Pattern ENCODED_TOKEN =
            Pattern.compile(
                    "(?i)\\b(?:[a-z0-9_-]{12,}\\.){2}[a-z0-9_-]{12,}\\b|\\b[a-z0-9_-]{32,}\\b");
    private static final Pattern TIMESTAMP =
            Pattern.compile(
                    "(?i)\\b\\d{4}-\\d{2}-\\d{2}(?:[t\\s]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?(?:z|[+-]\\d{2}:?\\d{2})?)?\\b");
    private static final Pattern EPOCH_TIMESTAMP =
            Pattern.compile("(?<![\\p{Alnum}_])1[5-9]\\d{8,11}(?![\\p{Alnum}_])");
    private static final Pattern HEX_ADDRESS = Pattern.compile("(?i)\\b0x[0-9a-f]+\\b");
    private static final Pattern LONG_HEX = Pattern.compile("(?i)\\b[0-9a-f]{16,}\\b");
    private static final Pattern EMAIL =
            Pattern.compile("(?i)\\b[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b");
    private static final Pattern IP_ADDRESS =
            Pattern.compile(
                    "(?<!\\d)(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}(?!\\d)");
    private static final Pattern ID_ASSIGNMENT =
            Pattern.compile("(?i)\\b([a-z][a-z0-9_-]*id)\\s*[:=]\\s*[-+]?\\d+\\b");
    private static final Pattern DATABASE_GENERATED_NAME =
            Pattern.compile(
                    "(?i)\\b((?:pk|fk|uk|idx|seq)(?:_[a-z][a-z0-9]*)*_)(?:[a-z0-9]{8,}|\\d{4,})\\b");
    private static final Pattern LONG_NUMBER =
            Pattern.compile("(?<![\\p{Alnum}_])-?\\d{4,}(?![\\p{Alnum}_])");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private MessagePatternNormalizer() {}

    static String normalize(String message) {
        if (message == null || message.isBlank()) {
            return "<empty>";
        }

        String normalized = message.substring(0, Math.min(message.length(), MAX_MESSAGE_CHARS));
        normalized = BEARER_TOKEN.matcher(normalized).replaceAll("bearer <token>");
        normalized = SECRET_ASSIGNMENT.matcher(normalized).replaceAll("$1=<secret>");
        normalized = REQUEST_ID_ASSIGNMENT.matcher(normalized).replaceAll("$1-id=<id>");
        normalized = UUID.matcher(normalized).replaceAll("<uuid>");
        normalized = ENCODED_TOKEN.matcher(normalized).replaceAll("<token>");
        normalized = TIMESTAMP.matcher(normalized).replaceAll("<timestamp>");
        normalized = EPOCH_TIMESTAMP.matcher(normalized).replaceAll("<timestamp>");
        normalized = HEX_ADDRESS.matcher(normalized).replaceAll("<hex>");
        normalized = LONG_HEX.matcher(normalized).replaceAll("<hex>");
        normalized = EMAIL.matcher(normalized).replaceAll("<email>");
        normalized = IP_ADDRESS.matcher(normalized).replaceAll("<ip>");
        normalized = ID_ASSIGNMENT.matcher(normalized).replaceAll("$1=<id>");
        normalized = DATABASE_GENERATED_NAME.matcher(normalized).replaceAll("$1<generated>");
        normalized = LONG_NUMBER.matcher(normalized).replaceAll("<number>");
        return WHITESPACE.matcher(normalized.strip().toLowerCase(Locale.ROOT)).replaceAll(" ");
    }
}
