package io.github.kalayciburak.stackmate.privacy;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Default deterministic sanitizer for logs, evidence, prompts, and exports. */
public final class DefaultSanitizer implements Sanitizer {

    private static final Pattern TERMINAL_ESCAPE =
            Pattern.compile(
                    "\\u001B(?:\\[[0-?]*[ -/]*[@-~]|\\][^\\u0007]*(?:\\u0007|\\u001B\\\\)|[@-_])");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT =
            Pattern.compile(
                    "(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]{2,}\\.[A-Za-z0-9_-]{2,}\\.[A-Za-z0-9_-]{2,}(?![A-Za-z0-9_-])");
    private static final Pattern TRUNCATED_JWT_PREFIX =
            Pattern.compile("(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]{2,}(?:\\.[A-Za-z0-9_-]*){0,2}$");
    private static final Pattern SECRET_ASSIGNMENT =
            Pattern.compile(
                    "(?i)([\\\"]?(?:authorization|proxy[_-]?authorization|cookie|set[_-]?cookie|password|passwd|pwd|secret|client[_-]?secret|api[_-]?key|access[_-]?token|refresh[_-]?token|id[_-]?token|jwt|bearer|session|private[_-]?key|credential)[\\\"]?\\s*[:=]\\s*[\\\"]?)([^\\s,;&\\\"]+)");
    private static final Pattern LONG_HEX =
            Pattern.compile("(?i)(?<![A-F0-9])[A-F0-9]{32,}(?![A-F0-9])");
    private static final Pattern LONG_BASE64 =
            Pattern.compile("(?<![A-Za-z0-9+/_-])[A-Za-z0-9+/_-]{40,}={0,2}(?![A-Za-z0-9+/_-])");
    private static final Pattern JDBC_AUTHORITY =
            Pattern.compile("(?i)(jdbc:[^\\s]+?://)([^:/@\\s]+):([^@\\s]+)@");
    private static final Pattern JDBC_USER_PROPERTY =
            Pattern.compile("(?i)(jdbc:[^\\s]*?[?;&]user(?:name)?=)([^;&\\s]+)");
    private static final Pattern EMAIL =
            Pattern.compile(
                    "(?iu)(?<![\\p{L}\\p{N}._%+-])[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}.-]+\\.[\\p{L}]{2,}(?![\\p{L}\\p{N}._%+-])");
    private static final Pattern TURKISH_ID = Pattern.compile("(?<!\\d)[1-9]\\d{10}(?!\\d)");
    private static final Pattern CARD = Pattern.compile("(?<!\\d)(?:\\d[ -]?){12,18}\\d(?!\\d)");

    private final SanitizerConfig config;

    public DefaultSanitizer(SanitizerConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public static DefaultSanitizer defaults() {
        return new DefaultSanitizer(SanitizerConfig.defaults());
    }

    @Override
    public String sanitize(String value) {
        return sanitize("", value);
    }

    @Override
    public String sanitize(String fieldName, String value) {
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(value, "value");
        String bounded = truncateCodePoints(value, config.maxLength());
        String safe = stripTerminalAndControlCharacters(bounded);
        if (config.sensitiveFieldNames().contains(SanitizerConfig.normalizeFieldName(fieldName))) {
            return config.replacement();
        }
        safe = redactDefiniteSecrets(safe);
        if (!isCodeIdentityField(fieldName)) {
            safe = redactHighEntropySecrets(safe);
            if (config.redactEmail()) {
                safe =
                        EMAIL.matcher(safe)
                                .replaceAll(Matcher.quoteReplacement(config.replacement()));
            }
            if (config.redactTurkishIdentityNumber()) {
                safe = redactValidatedMatches(safe, TURKISH_ID, this::isValidTurkishIdentityNumber);
            }
            if (config.redactPaymentCard()) {
                safe = redactValidatedMatches(safe, CARD, this::isValidPaymentCard);
            }
        }
        for (SanitizerPattern customPattern : config.customPatterns()) {
            safe =
                    customPattern
                            .pattern()
                            .matcher(safe)
                            .replaceAll(Matcher.quoteReplacement(customPattern.replacement()));
        }
        return truncateCodePoints(safe, config.maxLength());
    }

    private String redactDefiniteSecrets(String value) {
        String replacement = Matcher.quoteReplacement(config.replacement());
        String sanitized = BEARER.matcher(value).replaceAll("Bearer " + replacement);
        sanitized = JWT.matcher(sanitized).replaceAll(replacement);
        sanitized = TRUNCATED_JWT_PREFIX.matcher(sanitized).replaceAll(replacement);
        sanitized = SECRET_ASSIGNMENT.matcher(sanitized).replaceAll("$1" + replacement);
        sanitized =
                JDBC_AUTHORITY
                        .matcher(sanitized)
                        .replaceAll("$1" + replacement + ':' + replacement + '@');
        sanitized = JDBC_USER_PROPERTY.matcher(sanitized).replaceAll("$1" + replacement);
        return sanitized;
    }

    private String redactHighEntropySecrets(String value) {
        String replacement = Matcher.quoteReplacement(config.replacement());
        String sanitized = LONG_HEX.matcher(value).replaceAll(replacement);
        return LONG_BASE64.matcher(sanitized).replaceAll(replacement);
    }

    private String stripTerminalAndControlCharacters(String value) {
        String withoutEscapes = TERMINAL_ESCAPE.matcher(value).replaceAll("");
        StringBuilder safe = new StringBuilder(withoutEscapes.length());
        withoutEscapes
                .codePoints()
                .forEach(
                        codePoint -> {
                            if (isZeroWidthControl(codePoint)) {
                                return;
                            }
                            if (codePoint >= Character.MIN_SURROGATE
                                    && codePoint <= Character.MAX_SURROGATE) {
                                safe.append('\uFFFD');
                            } else if (Character.isISOControl(codePoint)
                                    || isUnsafeFormatControl(codePoint)) {
                                safe.append(' ');
                            } else {
                                safe.appendCodePoint(codePoint);
                            }
                        });
        return safe.toString();
    }

    private String redactValidatedMatches(
            String value, Pattern pattern, CandidateValidator validator) {
        Matcher matcher = pattern.matcher(value);
        StringBuilder result = new StringBuilder(value.length());
        while (matcher.find()) {
            String candidate = matcher.group();
            if (validator.isValid(candidate)) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(config.replacement()));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private boolean isValidTurkishIdentityNumber(String candidate) {
        int[] digits = candidate.chars().map(character -> character - '0').toArray();
        int oddSum = digits[0] + digits[2] + digits[4] + digits[6] + digits[8];
        int evenSum = digits[1] + digits[3] + digits[5] + digits[7];
        int tenth = Math.floorMod(oddSum * 7 - evenSum, 10);
        int firstTenSum = 0;
        for (int index = 0; index < 10; index++) {
            firstTenSum += digits[index];
        }
        return digits[9] == tenth && digits[10] == firstTenSum % 10;
    }

    private boolean isValidPaymentCard(String candidate) {
        String digits = candidate.replace(" ", "").replace("-", "");
        if (digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean doubleDigit = false;
        for (int index = digits.length() - 1; index >= 0; index--) {
            int digit = digits.charAt(index) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    private static boolean isCodeIdentityField(String fieldName) {
        String normalized = SanitizerConfig.normalizeFieldName(fieldName);
        return normalized.equals("classname")
                || normalized.equals("sourcename")
                || normalized.equals("filename")
                || normalized.equals("methodname")
                || normalized.equals("packagename")
                || normalized.equals("modulename")
                || normalized.equals("moduleversion")
                || normalized.equals("classloadername");
    }

    private static boolean isUnsafeFormatControl(int codePoint) {
        return (codePoint >= 0x202A && codePoint <= 0x202E)
                || (codePoint >= 0x2066 && codePoint <= 0x2069);
    }

    private static boolean isZeroWidthControl(int codePoint) {
        return codePoint == 0x200B
                || codePoint == 0x200C
                || codePoint == 0x200D
                || codePoint == 0x2060
                || codePoint == 0xFEFF;
    }

    private static String truncateCodePoints(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        int end = 0;
        int count = 0;
        while (end < value.length() && count < maxLength) {
            end += Character.charCount(value.codePointAt(end));
            count++;
        }
        if (end == value.length()) {
            return value;
        }
        return value.substring(0, end);
    }

    @FunctionalInterface
    private interface CandidateValidator {
        boolean isValid(String candidate);
    }
}
