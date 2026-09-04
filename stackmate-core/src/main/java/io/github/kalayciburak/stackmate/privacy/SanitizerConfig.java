package io.github.kalayciburak.stackmate.privacy;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable privacy controls. Expensive patterns only ever receive {@code maxLength} input.
 *
 * @param maxLength maximum number of input characters processed by the sanitizer
 * @param replacement safe text substituted for redacted values
 * @param redactEmail whether email-address redaction is enabled
 * @param redactTurkishIdentityNumber whether Turkish identity-number redaction is enabled
 * @param redactPaymentCard whether payment-card redaction is enabled
 * @param sensitiveFieldNames field names whose values are always redacted
 * @param customPatterns additional bounded-input redaction rules
 */
public record SanitizerConfig(
        int maxLength,
        String replacement,
        boolean redactEmail,
        boolean redactTurkishIdentityNumber,
        boolean redactPaymentCard,
        Set<String> sensitiveFieldNames,
        List<SanitizerPattern> customPatterns) {

    private static final Set<String> DEFAULT_SENSITIVE_FIELDS =
            Set.of(
                    "authorization",
                    "proxyauthorization",
                    "cookie",
                    "setcookie",
                    "password",
                    "passwd",
                    "pwd",
                    "secret",
                    "clientsecret",
                    "apikey",
                    "accesstoken",
                    "refreshtoken",
                    "idtoken",
                    "token",
                    "jwt",
                    "bearer",
                    "session",
                    "privatekey",
                    "credential");

    /**
     * Validates and creates privacy controls.
     *
     * @param maxLength maximum number of input characters processed by the sanitizer
     * @param replacement safe text substituted for redacted values
     * @param redactEmail whether email-address redaction is enabled
     * @param redactTurkishIdentityNumber whether Turkish identity-number redaction is enabled
     * @param redactPaymentCard whether payment-card redaction is enabled
     * @param sensitiveFieldNames field names whose values are always redacted
     * @param customPatterns additional bounded-input redaction rules
     */
    public SanitizerConfig {
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be positive");
        }
        replacement = requireSafeReplacement(replacement, "replacement");
        if (replacement.isEmpty()) {
            throw new IllegalArgumentException("replacement must not be empty");
        }
        Objects.requireNonNull(sensitiveFieldNames, "sensitiveFieldNames");
        Set<String> normalizedSensitiveFieldNames =
                sensitiveFieldNames.stream()
                        .map(SanitizerConfig::normalizeFieldName)
                        .collect(Collectors.toUnmodifiableSet());
        if (normalizedSensitiveFieldNames.contains("")) {
            throw new IllegalArgumentException("sensitiveFieldNames must not contain blank names");
        }
        sensitiveFieldNames = normalizedSensitiveFieldNames;
        customPatterns = List.copyOf(Objects.requireNonNull(customPatterns, "customPatterns"));
    }

    /**
     * Creates the standard privacy configuration.
     *
     * @return conservative default privacy controls
     */
    public static SanitizerConfig defaults() {
        return new SanitizerConfig(
                4_096, "[REDACTED]", true, true, true, DEFAULT_SENSITIVE_FIELDS, List.of());
    }

    /**
     * Copies these controls with selected built-in PII rules enabled or disabled.
     *
     * @param email whether email-address redaction is enabled
     * @param turkishIdentityNumber whether Turkish identity-number redaction is enabled
     * @param paymentCard whether payment-card redaction is enabled
     * @return copied sanitizer controls
     */
    public SanitizerConfig withPiiRedaction(
            boolean email, boolean turkishIdentityNumber, boolean paymentCard) {
        return new SanitizerConfig(
                maxLength,
                replacement,
                email,
                turkishIdentityNumber,
                paymentCard,
                sensitiveFieldNames,
                customPatterns);
    }

    static String normalizeFieldName(String fieldName) {
        Objects.requireNonNull(fieldName, "fieldName");
        return fieldName.toLowerCase(Locale.ROOT).replaceAll("[-_.\\s]", "");
    }

    static String requireSafeReplacement(String replacement, String name) {
        Objects.requireNonNull(replacement, name);
        replacement
                .codePoints()
                .forEach(
                        codePoint -> {
                            if (Character.isISOControl(codePoint)
                                    || codePoint == 0x061C
                                    || codePoint == 0x200E
                                    || codePoint == 0x200F
                                    || codePoint == 0xFEFF
                                    || (codePoint >= 0x202A && codePoint <= 0x202E)
                                    || (codePoint >= 0x2066 && codePoint <= 0x206F)) {
                                throw new IllegalArgumentException(
                                        name + " must not contain control characters");
                            }
                        });
        return replacement;
    }
}
