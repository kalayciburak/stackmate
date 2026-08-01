package io.github.kalayciburak.stackmate.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshotter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DefaultSanitizerTest {

    private final DefaultSanitizer sanitizer = DefaultSanitizer.defaults();

    @Test
    void redactsSecretFieldsAndMessageLevelCredentialForms() {
        assertThat(sanitizer.sanitize("Authorization", "Bearer abc.def.ghi"))
                .isEqualTo("[REDACTED]");
        assertThat(
                        sanitizer.sanitize(
                                "message", "Bearer abc.def.ghi password=hunter2 jwt=abc.def.ghi"))
                .isEqualTo("Bearer [REDACTED] password=[REDACTED] jwt=[REDACTED]");
        assertThat(
                        sanitizer.sanitize(
                                "message", "token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature"))
                .doesNotContain("eyJhbGci");
    }

    @Test
    void redactsLongHexBase64AndJdbcCredentials() {
        String value =
                "hex=0123456789abcdef0123456789abcdef "
                        + "blob=QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo0123456789 "
                        + "jdbc:postgresql://burak:secret@localhost/db "
                        + "jdbc:postgresql://localhost/db?user=burak&password=secret";

        String sanitized = sanitizer.sanitize(value);

        assertThat(sanitized)
                .doesNotContain("0123456789abcdef")
                .doesNotContain("QUJDREVGR0")
                .doesNotContain("burak:secret")
                .doesNotContain("user=burak")
                .doesNotContain("password=secret");
    }

    @Test
    void removesTerminalAndControlInjectionWhilePreservingUnicode() {
        String value = "İstanbul 👩‍💻\u001B[31m Kırmızı\u001B[0m\nson\u0000\uD800";

        assertThat(sanitizer.sanitize(value)).isEqualTo("İstanbul 👩💻 Kırmızı son �");
    }

    @Test
    void safeDefaultsRedactValidatedPiiButNotArbitraryNumbers() {
        String value =
                "mail burak@example.com tc 10000000146 card 4111 1111 1111 1111 "
                        + "ordinary 12345678901";

        String sanitized = sanitizer.sanitize(value);

        assertThat(sanitized)
                .doesNotContain("burak@example.com")
                .doesNotContain("10000000146")
                .doesNotContain("4111 1111 1111 1111")
                .contains("12345678901");
    }

    @Test
    void canDisablePiiAndApplyBoundedCustomPatternsWithoutOverRedactingCodeIdentity() {
        SanitizerConfig config =
                new SanitizerConfig(
                        64,
                        "***",
                        false,
                        false,
                        false,
                        Set.of("password"),
                        List.of(new SanitizerPattern(Pattern.compile("tenant-\\d+"), "tenant-*")));
        DefaultSanitizer custom = new DefaultSanitizer(config);
        String longClassHash = "com.example.Token" + "a".repeat(50);

        assertThat(custom.sanitize("message", "burak@example.com tenant-42"))
                .isEqualTo("burak@example.com tenant-*");
        assertThat(custom.sanitize("className", longClassHash))
                .isEqualTo(longClassHash.substring(0, 64))
                .doesNotContain("***");
    }

    @Test
    void truncatesByUnicodeCodePointWithoutProducingBrokenSurrogates() {
        SanitizerConfig config =
                new SanitizerConfig(2, "X", false, false, false, Set.of(), List.of());

        assertThat(new DefaultSanitizer(config).sanitize("🙂🙂🙂")).isEqualTo("🙂🙂");
    }

    @Test
    void rejectsControlCharactersInConfiguredReplacements() {
        assertThatThrownBy(
                        () ->
                                new SanitizerConfig(
                                        64, "\u001B[31m", true, true, true, Set.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SanitizerPattern(Pattern.compile("x"), "\n"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void redactsDefiniteSecretsAndCustomPatternsInEveryCodeIdentityField() {
        SanitizerConfig config =
                new SanitizerConfig(
                        256,
                        "***",
                        true,
                        true,
                        true,
                        Set.of("authorization"),
                        List.of(new SanitizerPattern(Pattern.compile("tenant-\\d+"), "tenant-*")));
        DefaultSanitizer custom = new DefaultSanitizer(config);
        String highEntropy = "0123456789abcdef0123456789abcdef";

        for (String fieldName :
                List.of(
                        "className",
                        "sourceName",
                        "fileName",
                        "methodName",
                        "packageName",
                        "moduleName",
                        "moduleVersion",
                        "classLoaderName")) {
            String sanitized =
                    custom.sanitize(
                            fieldName,
                            "Bearer abc.def.ghi password=hunter2 tenant-42 " + highEntropy);
            assertThat(sanitized)
                    .contains("Bearer ***", "password=***", "tenant-*", highEntropy)
                    .doesNotContain("hunter2", "abc.def.ghi");
        }
    }

    @Test
    void sanitizesHostileStackFrameIdentityValuesAtSnapshotBoundary() {
        RuntimeException exception = new RuntimeException("failed");
        exception.setStackTrace(
                new StackTraceElement[] {
                    new StackTraceElement(
                            "com.example.password=hunter2",
                            "Bearer abc.def.ghi",
                            "jdbc:postgresql://user:secret@localhost/db",
                            7)
                });

        var frame = ThrowableSnapshotter.defaults().capture(exception).outer().frames().getFirst();

        assertThat(frame.className()).doesNotContain("hunter2");
        assertThat(frame.methodName()).doesNotContain("abc.def.ghi");
        assertThat(frame.fileName()).doesNotContain("user:secret");
    }

    @Test
    void supportsSafeReplacementMetacharactersAndSanitizerMapContract() {
        DefaultSanitizer dollar =
                new DefaultSanitizer(
                        new SanitizerConfig(
                                128, "$1", false, false, false, Set.of("password"), List.of()));

        assertThat(dollar.sanitize("message", "password=secret Bearer abc"))
                .isEqualTo("password=$1 Bearer $1");
        assertThat(dollar.sanitize(Map.of("password", "secret", "message", "safe")))
                .containsEntry("password", "$1")
                .containsEntry("message", "safe");
        assertThatThrownBy(() -> dollar.sanitize((Map<String, String>) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void validatesNullBlankAndUnsafeConfigurationInputs() {
        assertThatThrownBy(() -> new DefaultSanitizer(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> sanitizer.sanitize(null, "value"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> sanitizer.sanitize("field", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SanitizerConfig(0, "X", true, true, true, Set.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SanitizerConfig(1, "", true, true, true, Set.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new SanitizerConfig(1, "X", true, true, true, Set.of(" "), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SanitizerConfig(1, "X", true, true, true, null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SanitizerConfig(1, "X", true, true, true, Set.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void validatesPiiChecksumsAndIndependentPiiToggles() {
        SanitizerConfig noPii = SanitizerConfig.defaults().withPiiRedaction(false, false, false);
        SanitizerConfig cardOnly = noPii.withPiiRedaction(false, false, true);
        String value = "burak@example.com 10000000146 12345678901";

        assertThat(new DefaultSanitizer(noPii).sanitize(value)).isEqualTo(value);
        assertThat(new DefaultSanitizer(cardOnly).sanitize("5555 5555 5555 4444"))
                .doesNotContain("5555 5555 5555 4444");
        assertThat(new DefaultSanitizer(cardOnly).sanitize("4111 1111 1111 1112"))
                .isEqualTo("4111 1111 1111 1112");
        assertThat(sanitizer.sanitize("10000000147")).isEqualTo("10000000147");
    }

    @Test
    void handlesUnsafeFormatControlsAndUnicodeThatFitsByCodePoint() {
        DefaultSanitizer threeCodePoints =
                new DefaultSanitizer(
                        new SanitizerConfig(3, "X", false, false, false, Set.of(), List.of()));

        assertThat(sanitizer.sanitize("a\uFEFFb\u202Ac\u2066d")).isEqualTo("ab c d");
        assertThat(threeCodePoints.sanitize("🙂🙂")).isEqualTo("🙂🙂");
    }

    @Test
    void removesZeroWidthRedactionBypassesAndRejectsTruncatedJwtPrefixes() {
        DefaultSanitizer shortSanitizer =
                new DefaultSanitizer(
                        new SanitizerConfig(
                                16, "[REDACTED]", true, true, true, Set.of(), List.of()));

        assertThat(sanitizer.sanitize("pass\u200Bword=hunter2"))
                .doesNotContain("hunter2")
                .contains("[REDACTED]");
        assertThat(shortSanitizer.sanitize("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature"))
                .isEqualTo("[REDACTED]");
    }

    @Test
    void keepsAlreadyRedactedAndRepeatedSanitizationStable() {
        String input = "password=hunter2 Authorization: Bearer abc.def.ghi already=[REDACTED]";

        String sanitized = sanitizer.sanitize("message", input);

        assertThat(sanitized)
                .contains("password=[REDACTED]", "Authorization: [REDACTED]", "already=[REDACTED]")
                .doesNotContain("hunter2", "abc.def.ghi", "Bearer");
        assertThat(sanitizer.sanitize("message", sanitized)).isEqualTo(sanitized);
    }

    @Test
    void rejectsEveryUnsafeReplacementControlRangeWithoutRejectingAdjacentCodePoints() {
        assertThatThrownBy(() -> new SanitizerPattern(Pattern.compile("x"), "\uFEFF"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SanitizerPattern(Pattern.compile("x"), "\u202A"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SanitizerPattern(Pattern.compile("x"), "\u2066"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(new SanitizerPattern(Pattern.compile("x"), "\u2029").replacement())
                .isEqualTo("\u2029");
        assertThat(new SanitizerPattern(Pattern.compile("x"), "\u202F").replacement())
                .isEqualTo("\u202F");
        assertThat(new SanitizerPattern(Pattern.compile("x"), "\u206A").replacement())
                .isEqualTo("\u206A");
    }
}
