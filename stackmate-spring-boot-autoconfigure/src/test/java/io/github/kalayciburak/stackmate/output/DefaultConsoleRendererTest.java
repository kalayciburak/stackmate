package io.github.kalayciburak.stackmate.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kalayciburak.stackmate.analysis.CheckCandidate;
import io.github.kalayciburak.stackmate.analysis.DeterministicResult;
import io.github.kalayciburak.stackmate.analysis.DiagnosedCause;
import io.github.kalayciburak.stackmate.analysis.Diagnosis;
import io.github.kalayciburak.stackmate.analysis.DiagnosticCategory;
import io.github.kalayciburak.stackmate.analysis.DiagnosticStrength;
import io.github.kalayciburak.stackmate.deduplication.DeduplicationResult;
import io.github.kalayciburak.stackmate.evidence.EvidenceItem;
import io.github.kalayciburak.stackmate.evidence.EvidenceReliability;
import io.github.kalayciburak.stackmate.evidence.EvidenceSensitivity;
import io.github.kalayciburak.stackmate.evidence.EvidenceType;
import io.github.kalayciburak.stackmate.fingerprint.ExceptionFingerprint;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.incident.JvmRuntimeContext;
import io.github.kalayciburak.stackmate.incident.RequestContext;
import io.github.kalayciburak.stackmate.incident.SpringRuntimeContext;
import io.github.kalayciburak.stackmate.incident.StackFrameSnapshot;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import io.github.kalayciburak.stackmate.throwable.SqlExceptionDetails;
import io.github.kalayciburak.stackmate.throwable.ThrowableNode;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DefaultConsoleRendererTest {

    private static final ExceptionFingerprint FINGERPRINT =
            new ExceptionFingerprint(
                    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

    private final ConsoleRenderer renderer =
            new DefaultConsoleRenderer(DefaultSanitizer.defaults());

    @Test
    void rendersEnglishBoxSnapshotWithoutCaptureSpecificId() {
        String rendered =
                renderer.render(result(true), options(RenderLanguage.ENGLISH, RenderStyle.BOX))
                        .replace("\r\n", "\n");

        assertThat(rendered)
                .isEqualTo(
                        """
                        +----------------------------------------------------------+
                        | StackMate Quick Diagnosis                                |
                        | Incident: STM-01234567                                   |
                        | Category: GENERAL_JAVA                                   |
                        | Evidence: HIGH                                           |
                        | Summary: Null reference was dereferenced                 |
                        | Root: java.lang.NullPointerException: order was null     |
                        | First app location:                                      |
                        | OrderService#create(OrderService.java:42)                |
                        | First checks:                                            |
                        |   - Inspect the first application frame and the values   |
                        | dereferenced there.                                      |
                        | Application trace:                                       |
                        |   OrderService#create(OrderService.java:42)              |
                        | Source: Deterministic rules                              |
                        | Runtime: Java 21.0.12 | Spring Boot 4.1.0 | Network      |
                        | disabled                                                 |
                        +----------------------------------------------------------+\
                        """);
        assertThat(rendered).doesNotContain("capture-123");
    }

    @Test
    void rendersNaturalTurkishCompactOutput() {
        String rendered =
                renderer.render(result(true), options(RenderLanguage.TURKISH, RenderStyle.COMPACT));

        assertThat(rendered)
                .contains("StackMate Hızlı Tanı")
                .contains("Olay: STM-01234567")
                .contains("Kategori: GENEL JAVA")
                .contains("Özet: Null bir referansa erişildi")
                .contains("İlk uygulama karesini ve burada erişilen değerleri")
                .contains("inceleyin.")
                .contains("Ağ devre")
                .contains("dışı")
                .doesNotContain("Null reference was dereferenced");
    }

    @Test
    void boundsEveryLineAndStripsTerminalEscapesAndSecrets() {
        String rendered =
                renderer.render(
                        resultWithMessage(
                                "\u001B[31m password=very-secret " + "long-value ".repeat(30),
                                true),
                        options(RenderLanguage.ENGLISH, RenderStyle.BOX));

        assertThat(rendered.lines()).allMatch(line -> line.codePointCount(0, line.length()) <= 60);
        assertThat(rendered)
                .doesNotContain("\u001B")
                .doesNotContain("very-secret")
                .contains("password=[REDACTED]");
    }

    @Test
    void usesExplicitUnknownFallbackAndOmitsAbsentLocation() {
        String rendered =
                renderer.render(
                        unknownResult(false), options(RenderLanguage.TURKISH, RenderStyle.COMPACT));

        assertThat(rendered)
                .contains("Kategori: BİLİNMİYOR")
                .contains("Kesin tanı için yeterli bağlam yok.")
                .doesNotContain("İlk uygulama konumu")
                .doesNotContain("Uygulama izi");
    }

    @Test
    void rendersDuplicateAsOneConciseMessage() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        String rendered =
                renderer.renderDuplicate(
                        result(true),
                        new DeduplicationResult(7, true, false, now, now),
                        new RenderOptions(
                                RenderLanguage.ENGLISH,
                                RenderStyle.COMPACT,
                                80,
                                true,
                                false,
                                false));

        assertThat(rendered).isEqualTo("StackMate duplicate STM-01234567 (count 7)");
    }

    @Test
    void severeJvmRenderingIsMinimalAndSanitized() {
        String rendered =
                renderer.renderSevereJvmFailure(
                        FINGERPRINT,
                        "java.lang.OutOfMemoryError\u001B[2J",
                        new RenderOptions(
                                RenderLanguage.ENGLISH,
                                RenderStyle.COMPACT,
                                80,
                                true,
                                false,
                                false));

        assertThat(rendered)
                .isEqualTo("StackMate severe JVM failure STM-01234567: java.lang.OutOfMemoryError")
                .doesNotContain("\u001B");
    }

    @Test
    void validatesWidthBounds() {
        assertThatThrownBy(
                        () ->
                                new RenderOptions(
                                        RenderLanguage.ENGLISH,
                                        RenderStyle.BOX,
                                        59,
                                        true,
                                        false,
                                        false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new RenderOptions(
                                        RenderLanguage.ENGLISH,
                                        RenderStyle.BOX,
                                        201,
                                        true,
                                        false,
                                        false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("builtInTurkishCases")
    void localizes_built_in_title_explanation_and_check_without_english_prose(
            String causeId,
            String checkId,
            DiagnosticCategory category,
            String expectedTitle,
            String expectedExplanation,
            String expectedCheck) {
        String englishTitle = "English built-in title for " + causeId;
        String englishExplanation = "English built-in explanation for " + causeId;
        String englishCheck = "English built-in check for " + checkId;
        DeterministicResult result =
                localizedResult(
                        causeId, checkId, category, englishTitle, englishExplanation, englishCheck);

        String turkish =
                renderer.render(
                        result, detailedOptions(RenderLanguage.TURKISH, RenderStyle.COMPACT));
        String english =
                renderer.render(
                        result, detailedOptions(RenderLanguage.ENGLISH, RenderStyle.COMPACT));

        assertThat(turkish)
                .contains(expectedTitle)
                .contains(expectedExplanation)
                .contains(expectedCheck)
                .doesNotContain(englishTitle)
                .doesNotContain(englishExplanation)
                .doesNotContain(englishCheck);
        assertThat(english)
                .contains(englishTitle)
                .contains(englishExplanation)
                .contains(englishCheck);
    }

    @Test
    void keeps_sanitized_custom_analyzer_prose_as_turkish_fallback() {
        DeterministicResult result =
                localizedResult(
                        "cause.custom.failure",
                        "check.custom.failure",
                        DiagnosticCategory.UNKNOWN,
                        "Özel neden",
                        "password=custom-secret özel açıklama",
                        "Özel kontrolü uygulayın.");

        String rendered =
                renderer.render(
                        result, detailedOptions(RenderLanguage.TURKISH, RenderStyle.COMPACT));

        assertThat(rendered)
                .contains("Özel neden")
                .contains("password=[REDACTED] özel açıklama")
                .contains("Özel kontrolü uygulayın.")
                .doesNotContain("custom-secret");
    }

    private static RenderOptions options(RenderLanguage language, RenderStyle style) {
        return new RenderOptions(language, style, 60, true, false, false);
    }

    private static RenderOptions detailedOptions(RenderLanguage language, RenderStyle style) {
        return new RenderOptions(language, style, 120, false, true, false);
    }

    private static Stream<Arguments> builtInTurkishCases() {
        return Stream.of(
                Arguments.of(
                        "cause.java.unsupported-operation",
                        "check.java.unsupported-operation",
                        DiagnosticCategory.GENERAL_JAVA,
                        "İstenen işlem desteklenmiyor",
                        "Yakalanan kesin Java hata türü",
                        "Somut implementasyonun işlemi"),
                Arguments.of(
                        "cause.spring.configuration-binding",
                        "check.spring.configuration-properties",
                        DiagnosticCategory.SPRING_CONFIGURATION,
                        "Spring yapılandırma özellikleri bağlanamadı",
                        "Yakalanan Spring hata türü",
                        "Özellik adlarını, değer türlerini"),
                Arguments.of(
                        "cause.web.json-shape-mismatch",
                        "check.web.json-shape-mismatch",
                        DiagnosticCategory.SERIALIZATION,
                        "JSON yapısı hedef türle eşleşmedi",
                        "Yakalanan web veya serileştirme hata türü",
                        "Gelen JSON yapısını hedef veri türüyle karşılaştırın"),
                Arguments.of(
                        "cause.database.unique-constraint",
                        "check.database.unique-constraint",
                        DiagnosticCategory.DATABASE_CONSTRAINT,
                        "Benzersizlik kısıtı ihlal edildi",
                        "yapılandırılmış SQLState bu tanıyı",
                        "Çakışan anahtarı"),
                Arguments.of(
                        "cause.security.access-denied",
                        "check.security.access-denied",
                        DiagnosticCategory.AUTHORIZATION,
                        "Yetkilendirme işleme erişimi reddetti",
                        "Yakalanan güvenlik hata türü",
                        "Kimliği doğrulanmış kullanıcıyı"),
                Arguments.of(
                        "cause.infrastructure.dns-resolution",
                        "check.infrastructure.dns-resolution",
                        DiagnosticCategory.DNS,
                        "Sunucu adı çözümlenemedi",
                        "Yakalanan altyapı hata türü",
                        "Sunucu adını, DNS resolver'ı"),
                Arguments.of(
                        "cause.unknown",
                        "check.inspect-root-cause",
                        DiagnosticCategory.UNKNOWN,
                        "Deterministik bir neden belirlenemedi",
                        "Mevcut kanıtlar bilinen deterministik bir kuralla eşleşmedi",
                        "Kök hata türünü, mesajını"));
    }

    private static DeterministicResult localizedResult(
            String causeId,
            String checkId,
            DiagnosticCategory category,
            String title,
            String explanation,
            String check) {
        EvidenceItem evidence = evidence();
        Diagnosis diagnosis =
                new Diagnosis(
                        List.of(evidence),
                        List.of(
                                new DiagnosedCause(
                                        causeId,
                                        category,
                                        title,
                                        explanation,
                                        DiagnosticStrength.HIGH,
                                        List.of(evidence.id()))),
                        List.of(new CheckCandidate(checkId, check, List.of(evidence.id()))),
                        List.of("renderer-test@1"),
                        causeId.equals("cause.unknown"));
        return new DeterministicResult(incident("localized failure", true), diagnosis, FINGERPRINT);
    }

    private static DeterministicResult result(boolean applicationFrame) {
        return resultWithMessage("order was null", applicationFrame);
    }

    private static DeterministicResult resultWithMessage(String message, boolean applicationFrame) {
        EvidenceItem evidence = evidence();
        Diagnosis diagnosis =
                new Diagnosis(
                        List.of(evidence),
                        List.of(
                                new DiagnosedCause(
                                        "cause.java.null-reference",
                                        DiagnosticCategory.GENERAL_JAVA,
                                        "Null reference was dereferenced",
                                        "An exact NullPointerException was captured.",
                                        DiagnosticStrength.HIGH,
                                        List.of(evidence.id()))),
                        List.of(
                                new CheckCandidate(
                                        "check.java.null-reference",
                                        "Inspect the first application frame and the values"
                                                + " dereferenced there.",
                                        List.of(evidence.id()))),
                        List.of("general-java@1"),
                        false);
        return new DeterministicResult(incident(message, applicationFrame), diagnosis, FINGERPRINT);
    }

    private static DeterministicResult unknownResult(boolean applicationFrame) {
        EvidenceItem evidence = evidence();
        Diagnosis diagnosis =
                new Diagnosis(
                        List.of(evidence),
                        List.of(
                                new DiagnosedCause(
                                        "cause.unknown",
                                        DiagnosticCategory.UNKNOWN,
                                        "No deterministic cause was identified",
                                        "Available evidence did not match a deterministic rule.",
                                        DiagnosticStrength.UNKNOWN,
                                        List.of(evidence.id()))),
                        List.of(),
                        List.of("fallback@1"),
                        true);
        return new DeterministicResult(
                incident("unknown failure", applicationFrame), diagnosis, FINGERPRINT);
    }

    private static EvidenceItem evidence() {
        return new EvidenceItem(
                "evidence.throwable",
                EvidenceType.EXCEPTION_CLASS,
                "throwable.root",
                "java.lang.NullPointerException",
                EvidenceSensitivity.PUBLIC,
                EvidenceReliability.HIGH);
    }

    private static IncidentSnapshot incident(String message, boolean applicationFrame) {
        StackFrameSnapshot frame =
                new StackFrameSnapshot(
                        "example.OrderService",
                        "create",
                        "OrderService.java",
                        42,
                        false,
                        "",
                        "",
                        "app",
                        0);
        ThrowableNode root =
                new ThrowableNode(
                        "java.lang.NullPointerException",
                        message,
                        List.of(frame),
                        List.of(),
                        SqlExceptionDetails.none(),
                        0,
                        false,
                        false,
                        false,
                        false,
                        false,
                        List.of());
        return new IncidentSnapshot(
                "capture-123",
                Instant.parse("2026-08-01T12:00:00Z"),
                new ThrowableSnapshot(List.of(root), 1, false),
                applicationFrame ? List.of(frame) : List.of(),
                new RequestContext("POST", "/orders", "", "", 500, Map.of()),
                new SpringRuntimeContext(
                        "shop",
                        List.of("prod"),
                        "example",
                        "4.1.0",
                        "7.0.0",
                        SpringRuntimeContext.TransactionState.UNKNOWN),
                new JvmRuntimeContext("21.0.12", "OpenJDK", "Amazon", "Windows", "amd64", 8));
    }
}
