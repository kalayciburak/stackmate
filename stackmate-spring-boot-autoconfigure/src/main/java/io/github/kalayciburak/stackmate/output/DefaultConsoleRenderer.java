package io.github.kalayciburak.stackmate.output;

import io.github.kalayciburak.stackmate.analysis.CheckCandidate;
import io.github.kalayciburak.stackmate.analysis.DeterministicResult;
import io.github.kalayciburak.stackmate.analysis.DiagnosedCause;
import io.github.kalayciburak.stackmate.analysis.DiagnosticCategory;
import io.github.kalayciburak.stackmate.deduplication.DeduplicationResult;
import io.github.kalayciburak.stackmate.fingerprint.ExceptionFingerprint;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.incident.StackFrameSnapshot;
import io.github.kalayciburak.stackmate.privacy.Sanitizer;
import io.github.kalayciburak.stackmate.throwable.ThrowableNode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Default deterministic renderer for plain log and terminal output. */
public final class DefaultConsoleRenderer implements ConsoleRenderer {

    private static final Map<DiagnosticCategory, String> TURKISH_CATEGORIES = categories();
    private static final Map<String, String> TURKISH_CAUSES = causes();
    private static final Map<String, String> TURKISH_EXPLANATIONS = explanations(TURKISH_CAUSES);
    private static final Map<String, String> TURKISH_CHECKS = checks();
    private static final int MAX_CHECKS = 3;
    private static final int MAX_TRACE_FRAMES = 5;

    private final Sanitizer sanitizer;

    public DefaultConsoleRenderer(Sanitizer sanitizer) {
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
    }

    @Override
    public String render(DeterministicResult result, RenderOptions options) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(options, "options");
        boolean tr = options.language() == RenderLanguage.TURKISH;
        IncidentSnapshot incident = result.incident();
        DiagnosedCause cause = result.diagnosis().causes().getFirst();
        ThrowableNode root = incident.throwable().root();
        List<String> lines = new ArrayList<>();
        lines.add(tr ? "StackMate Hızlı Tanı" : "StackMate Quick Diagnosis");
        lines.add(label(tr, "Olay", "Incident") + displayId(result.fingerprint()));
        lines.add(label(tr, "Kategori", "Category") + category(cause.category(), tr));
        lines.add(label(tr, "Kanıt gücü", "Evidence") + strength(cause.strength().name(), tr));
        lines.add(label(tr, "Özet", "Summary") + causeTitle(cause, tr));
        lines.add(label(tr, "Kök hata", "Root") + root(root));

        if (!incident.applicationFrames().isEmpty()) {
            lines.add(
                    label(tr, "İlk uygulama konumu", "First app location")
                            + frame(incident.applicationFrames().getFirst()));
        }
        if (options.showDetails()) {
            lines.add(label(tr, "Ayrıntı", "Details") + causeExplanation(cause, tr));
            appendRequest(lines, incident, tr);
        }
        appendChecks(lines, result, tr);
        if (options.showCauseChain()) {
            appendCauseChain(lines, incident, tr);
        }
        if (options.showApplicationTrace()) {
            appendTrace(lines, incident, tr);
        }
        lines.add(
                label(tr, "Kaynak", "Source")
                        + (tr ? "Deterministik kurallar" : "Deterministic rules"));
        lines.add(label(tr, "Çalışma ortamı", "Runtime") + runtime(incident, tr));
        if (result.diagnosis().fallback()) {
            lines.add(
                    tr
                            ? "Not: Kesin tanı için yeterli bağlam yok."
                            : "Note: Insufficient context for a conclusive diagnosis.");
        }
        return format(lines, options);
    }

    @Override
    public String renderDuplicate(
            DeterministicResult result, DeduplicationResult duplicate, RenderOptions options) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(duplicate, "duplicate");
        Objects.requireNonNull(options, "options");
        boolean tr = options.language() == RenderLanguage.TURKISH;
        String message =
                tr
                        ? "StackMate tekrar "
                                + displayId(result.fingerprint())
                                + " (toplam "
                                + duplicate.occurrenceCount()
                                + ")"
                        : "StackMate duplicate "
                                + displayId(result.fingerprint())
                                + " (count "
                                + duplicate.occurrenceCount()
                                + ")";
        return format(List.of(message), options);
    }

    @Override
    public String renderSevereJvmFailure(
            ExceptionFingerprint fingerprint, String failureType, RenderOptions options) {
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(failureType, "failureType");
        Objects.requireNonNull(options, "options");
        boolean tr = options.language() == RenderLanguage.TURKISH;
        String message =
                tr
                        ? "StackMate kritik JVM hatası "
                                + displayId(fingerprint)
                                + ": "
                                + safe("throwableType", failureType)
                        : "StackMate severe JVM failure "
                                + displayId(fingerprint)
                                + ": "
                                + safe("throwableType", failureType);
        return format(List.of(message), options);
    }

    private void appendChecks(List<String> lines, DeterministicResult result, boolean tr) {
        List<CheckCandidate> checks = result.diagnosis().checks();
        if (checks.isEmpty()) {
            lines.add(
                    tr
                            ? "Kontroller: Kanıt destekli bir sonraki kontrol bulunamadı."
                            : "Checks: No evidence-backed next check is available.");
            return;
        }
        lines.add(tr ? "İlk kontroller:" : "First checks:");
        checks.stream()
                .limit(MAX_CHECKS)
                .forEach(check -> lines.add("  - " + checkInstruction(check, tr)));
    }

    private void appendCauseChain(List<String> lines, IncidentSnapshot incident, boolean tr) {
        lines.add(tr ? "Neden zinciri:" : "Cause chain:");
        for (ThrowableNode node : incident.throwable().causeChain()) {
            lines.add("  - " + root(node));
        }
    }

    private void appendTrace(List<String> lines, IncidentSnapshot incident, boolean tr) {
        if (incident.applicationFrames().isEmpty()) {
            return;
        }
        lines.add(tr ? "Uygulama izi:" : "Application trace:");
        incident.applicationFrames().stream()
                .limit(MAX_TRACE_FRAMES)
                .forEach(frame -> lines.add("  " + frame(frame)));
    }

    private void appendRequest(List<String> lines, IncidentSnapshot incident, boolean tr) {
        var request = incident.requestContext();
        if (request.method().isBlank() && request.path().isBlank()) {
            return;
        }
        lines.add(
                label(tr, "İstek", "Request")
                        + safe("requestMethod", request.method())
                        + " "
                        + safe("requestPath", request.path()));
    }

    private String root(ThrowableNode node) {
        String type = safe("throwableType", node.type());
        String message = safe("throwableMessage", node.message());
        return message.isBlank() ? type : type + ": " + message;
    }

    private String frame(StackFrameSnapshot frame) {
        String className = safe("className", frame.className());
        int packageEnd = className.lastIndexOf('.');
        String simpleClassName = packageEnd < 0 ? className : className.substring(packageEnd + 1);
        StringBuilder value =
                new StringBuilder()
                        .append(simpleClassName)
                        .append('#')
                        .append(safe("methodName", frame.methodName()))
                        .append('(')
                        .append(safe("fileName", frame.fileName()));
        if (frame.lineNumber() >= 0) {
            value.append(':').append(frame.lineNumber());
        }
        return value.append(')').toString();
    }

    private String runtime(IncidentSnapshot incident, boolean tr) {
        String javaVersion = safe("javaVersion", incident.jvmRuntimeContext().javaVersion());
        String bootVersion = safe("bootVersion", incident.springRuntimeContext().bootVersion());
        if (javaVersion.isBlank()) {
            javaVersion = tr ? "bilinmiyor" : "unknown";
        }
        if (bootVersion.isBlank()) {
            bootVersion = tr ? "bilinmiyor" : "unknown";
        }
        return "Java "
                + javaVersion
                + " | Spring Boot "
                + bootVersion
                + " | "
                + (tr ? "Ağ devre dışı" : "Network disabled");
    }

    private String causeTitle(DiagnosedCause cause, boolean tr) {
        if (tr) {
            String translated = TURKISH_CAUSES.get(cause.id());
            if (translated != null) {
                return translated;
            }
        }
        return safe("causeTitle", cause.title());
    }

    private String checkInstruction(CheckCandidate check, boolean tr) {
        if (tr) {
            String translated = TURKISH_CHECKS.get(check.id());
            if (translated != null) {
                return translated;
            }
        }
        return safe("checkInstruction", check.instruction());
    }

    private String causeExplanation(DiagnosedCause cause, boolean tr) {
        if (tr) {
            String translated = TURKISH_EXPLANATIONS.get(cause.id());
            if (translated != null) {
                return translated;
            }
        }
        return safe("explanation", cause.explanation());
    }

    private String category(DiagnosticCategory category, boolean tr) {
        if (!tr) {
            return category.name();
        }
        return TURKISH_CATEGORIES.getOrDefault(category, category.name());
    }

    private static String strength(String strength, boolean tr) {
        if (!tr) {
            return strength;
        }
        return switch (strength) {
            case "HIGH" -> "YÜKSEK";
            case "MEDIUM" -> "ORTA";
            case "LOW" -> "DÜŞÜK";
            default -> "BİLİNMİYOR";
        };
    }

    private String safe(String fieldName, String value) {
        return sanitizer.sanitize(fieldName, Objects.requireNonNull(value, fieldName));
    }

    private static String displayId(ExceptionFingerprint fingerprint) {
        return "STM-" + fingerprint.value().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }

    private static String label(boolean tr, String turkish, String english) {
        return (tr ? turkish : english) + ": ";
    }

    private static String format(List<String> sourceLines, RenderOptions options) {
        int contentWidth =
                options.style() == RenderStyle.BOX ? options.maxWidth() - 4 : options.maxWidth();
        List<String> lines = new ArrayList<>();
        for (String line : sourceLines) {
            lines.addAll(wrap(line, contentWidth));
        }
        if (options.style() == RenderStyle.COMPACT) {
            return String.join(System.lineSeparator(), lines);
        }
        String border = "+" + "-".repeat(options.maxWidth() - 2) + "+";
        StringBuilder rendered = new StringBuilder(border);
        for (String line : lines) {
            rendered.append(System.lineSeparator())
                    .append("| ")
                    .append(line)
                    .append(" ".repeat(contentWidth - codePointLength(line)))
                    .append(" |");
        }
        return rendered.append(System.lineSeparator()).append(border).toString();
    }

    private static List<String> wrap(String value, int width) {
        if (codePointLength(value) <= width) {
            return List.of(value);
        }
        List<String> lines = new ArrayList<>();
        String remaining = value;
        while (codePointLength(remaining) > width) {
            int end = remaining.offsetByCodePoints(0, width);
            int split = remaining.lastIndexOf(' ', end - 1);
            if (split <= 0) {
                split = end;
            }
            lines.add(remaining.substring(0, split).stripTrailing());
            remaining = remaining.substring(split).stripLeading();
        }
        if (!remaining.isEmpty()) {
            lines.add(remaining);
        }
        return lines;
    }

    private static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private static Map<DiagnosticCategory, String> categories() {
        Map<DiagnosticCategory, String> values = new EnumMap<>(DiagnosticCategory.class);
        values.put(DiagnosticCategory.GENERAL_JAVA, "GENEL JAVA");
        values.put(DiagnosticCategory.CONCURRENCY, "EŞZAMANLILIK");
        values.put(DiagnosticCategory.RESOURCE_EXHAUSTION, "KAYNAK TÜKENMESİ");
        values.put(DiagnosticCategory.SPRING_DEPENDENCY_INJECTION, "SPRING BAĞIMLILIK ENJEKSİYONU");
        values.put(DiagnosticCategory.SPRING_CONFIGURATION, "SPRING YAPILANDIRMASI");
        values.put(DiagnosticCategory.WEB_REQUEST, "WEB İSTEĞİ");
        values.put(DiagnosticCategory.VALIDATION, "DOĞRULAMA");
        values.put(DiagnosticCategory.SERIALIZATION, "SERİLEŞTİRME");
        values.put(DiagnosticCategory.DATABASE, "VERİTABANI");
        values.put(DiagnosticCategory.DATABASE_CONSTRAINT, "VERİTABANI KISITI");
        values.put(DiagnosticCategory.DATABASE_SCHEMA, "VERİTABANI ŞEMASI");
        values.put(DiagnosticCategory.DATABASE_CONNECTION, "VERİTABANI BAĞLANTISI");
        values.put(DiagnosticCategory.DATABASE_TRANSACTION, "VERİTABANI İŞLEMİ");
        values.put(DiagnosticCategory.DATABASE_LOCKING, "VERİTABANI KİLİDİ");
        values.put(DiagnosticCategory.PERSISTENCE_STATE, "KALICILIK DURUMU");
        values.put(DiagnosticCategory.AUTHENTICATION, "KİMLİK DOĞRULAMA");
        values.put(DiagnosticCategory.AUTHORIZATION, "YETKİLENDİRME");
        values.put(DiagnosticCategory.DNS, "DNS");
        values.put(DiagnosticCategory.TLS, "TLS");
        values.put(DiagnosticCategory.NETWORK_CONNECTION, "AĞ BAĞLANTISI");
        values.put(DiagnosticCategory.TIMEOUT, "ZAMAN AŞIMI");
        values.put(DiagnosticCategory.UNKNOWN, "BİLİNMİYOR");
        return Map.copyOf(values);
    }

    private static Map<String, String> causes() {
        Map<String, String> values = new HashMap<>();

        values.put("cause.java.null-reference", "Null bir referansa erişildi");
        values.put("cause.java.invalid-argument", "Bir metot geçersiz bir argümanı reddetti");
        values.put("cause.java.invalid-state", "İşlem geçersiz bir durumda çalıştı");
        values.put("cause.java.unsupported-operation", "İstenen işlem desteklenmiyor");
        values.put("cause.java.incompatible-runtime-type", "Değerin çalışma zamanı türü uyumsuz");
        values.put("cause.java.index-out-of-bounds", "Dizin kullanılabilir aralığın dışında");
        values.put("cause.java.invalid-number", "Metin istenen sayı türüne dönüştürülemedi");
        values.put("cause.java.class-not-found", "İstenen sınıf çalışma zamanında bulunamadı");
        values.put(
                "cause.java.class-definition-missing",
                "Sınıf tanımı çalışma zamanında kullanılamıyor");
        values.put("cause.java.out-of-memory", "JVM gerekli belleği ayıramadı");
        values.put("cause.java.stack-overflow", "İş parçacığı yığını tükendi");
        values.put(
                "cause.java.concurrent-modification",
                "Koleksiyon uyumsuz bir dolaşım sırasında değiştirildi");
        values.put("cause.java.concurrent-timeout", "Asenkron işlem süresini aştı");
        values.put("cause.java.execution-rejected", "Executor gönderilen işi reddetti");

        values.put("cause.spring.missing-bean", "Gerekli Spring bean'i bulunamadı");
        values.put(
                "cause.spring.ambiguous-bean",
                "Enjeksiyon noktasıyla birden fazla Spring bean'i eşleşti");
        values.put("cause.spring.unsatisfied-dependency", "Spring bean bağımlılığı karşılanamadı");
        values.put("cause.spring.circular-dependency", "Spring bean oluşturma döngüsü algılandı");
        values.put("cause.spring.bean-creation", "Spring bean'i oluşturulurken hata oluştu");
        values.put("cause.spring.bean-definition-conflict", "Spring bean tanımları çakıştı");
        values.put(
                "cause.spring.configuration-binding",
                "Spring yapılandırma özellikleri bağlanamadı");
        values.put(
                "cause.spring.unresolved-placeholder",
                "Spring yapılandırma yer tutucusu çözümlenemedi");
        values.put("cause.spring.configuration-conversion", "Spring değer dönüşümü başarısız oldu");
        values.put(
                "cause.spring.converter-not-found",
                "Gerekli Spring değer dönüştürücüsü bulunamadı");
        values.put("cause.spring.type-mismatch", "Spring değeri gerekli türle eşleşmedi");
        values.put("cause.spring.invalid-property", "Spring bean özelliğini geçersiz buldu");
        values.put(
                "cause.spring.property-not-writable",
                "Yapılandırılan bean özelliği yazılabilir değil");
        values.put("cause.spring.bean-definition-processing", "Spring bean tanımını işleyemedi");
        values.put(
                "cause.spring.missing-bean-message",
                "Gerekli bir Spring bean'i bulunamıyor olabilir");
        values.put(
                "cause.spring.unresolved-placeholder-message",
                "Bir yapılandırma yer tutucusu çözümlenemiyor olabilir");
        values.put(
                "cause.spring.configuration-binding-message",
                "Yapılandırma özellikleri bağlanamamış olabilir");
        values.put(
                "cause.spring.condition-evaluation-message",
                "Bir Spring yapılandırma koşulu değerlendirilememiş olabilir");
        values.put(
                "cause.spring.property-conversion-message",
                "Bir Spring özellik değeri dönüştürülememiş olabilir");

        values.put("cause.web.request-validation", "İstek doğrulaması başarısız oldu");
        values.put("cause.web.method-validation", "Controller metodu doğrulanamadı");
        values.put("cause.web.constraint-violation", "Tanımlı bir doğrulama kısıtı ihlal edildi");
        values.put("cause.web.request-body-unreadable", "HTTP istek gövdesi okunamadı");
        values.put("cause.web.response-body-unwritable", "HTTP yanıt gövdesi yazılamadı");
        values.put("cause.web.json-shape-mismatch", "JSON yapısı hedef türle eşleşmedi");
        values.put("cause.web.json-value-format", "JSON değeri hedef türle uyumsuz bir biçimdeydi");
        values.put("cause.web.missing-request-parameter", "Zorunlu istek parametresi eksik");
        values.put(
                "cause.web.request-parameter-type", "İstek değeri bildirilen türe dönüştürülemedi");
        values.put(
                "cause.web.http-method-not-supported",
                "HTTP metodu endpoint tarafından desteklenmiyor");
        values.put("cause.web.media-type-not-supported", "İstek medya türü desteklenmiyor");
        values.put("cause.web.handler-not-found", "İstekle eşleşen MVC handler bulunamadı");
        values.put(
                "cause.web.resource-not-found",
                "İstekle eşleşen handler veya statik kaynak bulunamadı");
        values.put("cause.web.json-parse-message", "İstek gövdesinde geçersiz JSON olabilir");
        values.put(
                "cause.web.validation-message",
                "Bir istek argümanı doğrulamadan geçememiş olabilir");
        values.put(
                "cause.web.enum-conversion-message",
                "İstek değeri kabul edilen enum değerleriyle eşleşmiyor olabilir");

        values.put("cause.database.bad-sql-grammar", "Veritabanı SQL söz dizimini reddetti");
        values.put(
                "cause.database.sql-grammar",
                "Veritabanı SQL söz dizimini veya tanımlayıcıyı reddetti");
        values.put("cause.database.jdbc-connection", "JDBC bağlantısı alınamadı");
        values.put("cause.database.transient-connection", "Geçici bir JDBC bağlantı hatası oluştu");
        values.put(
                "cause.database.pool-initialization", "Veritabanı bağlantı havuzu başlatılamadı");
        values.put("cause.database.optimistic-lock", "İyimser kilitleme denetimi başarısız oldu");
        values.put("cause.database.pessimistic-lock", "Kötümser veritabanı kilidi alınamadı");
        values.put(
                "cause.database.transaction-required",
                "Kalıcılık işlemi etkin transaction gerektiriyor");
        values.put("cause.database.lazy-initialization", "Lazy veri etkin oturum dışında erişildi");
        values.put("cause.database.query-timeout", "Veritabanı sorgusu izin verilen süreyi aştı");
        values.put("cause.database.driver-error", "Veritabanı sürücüsü hata bildirdi");
        values.put("cause.database.unique-constraint", "Benzersizlik kısıtı ihlal edildi");
        values.put("cause.database.foreign-key-constraint", "Yabancı anahtar kısıtı ihlal edildi");
        values.put(
                "cause.database.not-null-constraint",
                "Zorunlu veritabanı sütununa null değer verildi");
        values.put("cause.database.missing-table", "Başvurulan veritabanı tablosu bulunamadı");
        values.put("cause.database.missing-column", "Başvurulan veritabanı sütunu bulunamadı");
        values.put("cause.database.sql-syntax", "Veritabanı SQL söz dizimini reddetti");
        values.put("cause.database.deadlock", "Veritabanı deadlock algıladı");
        values.put("cause.database.lock-not-available", "İstenen veritabanı kilidi kullanılamıyor");
        values.put("cause.database.query-canceled", "Veritabanı sorguyu iptal etti");
        values.put("cause.database.connection-exception", "Veritabanı bağlantısı başarısız oldu");
        values.put("cause.database.data-exception", "Veritabanı veri değerini reddetti");
        values.put(
                "cause.database.integrity-constraint", "Veritabanı bütünlük kısıtı ihlal edildi");
        values.put(
                "cause.database.authorization-specification",
                "Veritabanı yetkilendirmeyi reddetti");
        values.put("cause.database.transaction-rollback", "Veritabanı transaction'ı geri aldı");
        values.put(
                "cause.database.sql-syntax-or-access",
                "SQL söz dizimi veya erişim kuralı işlemi reddetti");
        values.put("cause.database.sql-state", "Veritabanı yapılandırılmış bir SQLState döndürdü");
        values.put(
                "cause.database.unique-constraint-message",
                "Benzersizlik kısıtı ihlal edilmiş olabilir");
        values.put(
                "cause.database.missing-table-message",
                "Başvurulan veritabanı tablosu eksik olabilir");
        values.put(
                "cause.database.missing-column-message",
                "Başvurulan veritabanı sütunu eksik olabilir");
        values.put(
                "cause.database.sql-syntax-message",
                "Veritabanı SQL söz dizimini reddetmiş olabilir");
        values.put(
                "cause.database.connection-refused-message",
                "Veritabanı endpoint'i bağlantıyı reddetmiş olabilir");
        values.put(
                "cause.database.connection-timeout-message",
                "Veritabanı bağlantısı kurulurken zaman aşımı oluşmuş olabilir");
        values.put(
                "cause.database.pool-exhaustion-message",
                "Veritabanı bağlantı havuzu tükenmiş olabilir");
        values.put("cause.database.deadlock-message", "Veritabanında deadlock oluşmuş olabilir");
        values.put(
                "cause.database.lock-unavailable-message",
                "Gerekli veritabanı kilidi alınamamış olabilir");
        values.put(
                "cause.database.transaction-required-message",
                "Kalıcılık işlemi etkin transaction gerektiriyor olabilir");
        values.put(
                "cause.database.lazy-initialization-message",
                "Lazy veriye etkin oturum dışında erişilmiş olabilir");
        values.put(
                "cause.database.query-timeout-message",
                "Veritabanı sorgusu süre sınırını aşmış olabilir");

        values.put("cause.security.bad-credentials", "Kimlik doğrulama bilgileri reddedildi");
        values.put("cause.security.credentials-not-found", "Kimlik bilgisi bulunamadı");
        values.put(
                "cause.security.insufficient-authentication", "Kimlik doğrulama düzeyi yetersiz");
        values.put(
                "cause.security.account-locked",
                "Hesap kilitli olduğu için kimlik doğrulama reddedildi");
        values.put(
                "cause.security.account-disabled",
                "Hesap devre dışı olduğu için kimlik doğrulama reddedildi");
        values.put("cause.security.access-denied", "Yetkilendirme işleme erişimi reddetti");
        values.put("cause.security.authorization-denied", "Yetkilendirme kararı işlemi reddetti");
        values.put(
                "cause.security.credentials-expired",
                "Kimlik doğrulama bilgilerinin süresi dolmuş");
        values.put("cause.security.jwt-validation", "JWT doğrulaması başarısız oldu");
        values.put("cause.security.malformed-token", "JWT biçimi bozuk veya geçersiz");
        values.put("cause.security.invalid-bearer-token", "Bearer token geçersiz");
        values.put("cause.security.token-expired", "JWT'nin süresi dolmuş");
        values.put("cause.security.token-decode", "JWT çözümlenemedi");
        values.put(
                "cause.security.access-denied-message", "Yetkilendirme işlemi reddetmiş olabilir");
        values.put(
                "cause.security.authentication-required-message",
                "İşlem daha güçlü kimlik doğrulama gerektiriyor olabilir");
        values.put(
                "cause.security.token-expired-message",
                "Kimlik doğrulama token'ının süresi dolmuş olabilir");
        values.put(
                "cause.security.malformed-token-message",
                "Kimlik doğrulama token'ının biçimi bozuk olabilir");

        values.put("cause.infrastructure.dns-resolution", "Sunucu adı çözümlenemedi");
        values.put("cause.infrastructure.tls-handshake", "TLS el sıkışması başarısız oldu");
        values.put("cause.infrastructure.tls-protocol", "TLS bağlantısı başarısız oldu");
        values.put("cause.infrastructure.socket-timeout", "Socket işlemi zaman aşımına uğradı");
        values.put("cause.infrastructure.http-timeout", "HTTP işlemi zaman aşımına uğradı");
        values.put("cause.infrastructure.connection-failed", "Ağ bağlantısı kurulamadı");
        values.put(
                "cause.infrastructure.no-route",
                "Sunucuya giden kullanılabilir bir ağ rotası bulunamadı");
        values.put("cause.infrastructure.socket-failure", "Socket işlemi başarısız oldu");
        values.put(
                "cause.infrastructure.connection-refused-message",
                "Hedef bağlantıyı reddetmiş olabilir");
        values.put(
                "cause.infrastructure.timed-out-message",
                "İşlem zaman aşımı süresini geçmiş olabilir");
        values.put(
                "cause.infrastructure.dns-resolution-message",
                "Sunucu adı çözümlenememiş olabilir");

        values.put("cause.unknown", "Deterministik bir neden belirlenemedi");
        return Map.copyOf(values);
    }

    private static Map<String, String> explanations(Map<String, String> causes) {
        Map<String, String> values = new HashMap<>();
        for (String id : causes.keySet()) {
            String explanation;
            if (id.equals("cause.unknown")) {
                explanation = "Mevcut kanıtlar bilinen deterministik bir kuralla eşleşmedi.";
            } else if (id.endsWith("-message")) {
                explanation =
                        "Yakalanan hata mesajındaki bilinen örüntü bu olası tanıyı kanıtlarla"
                                + " destekliyor; kesin hata türü eşleşmedi.";
            } else if (id.startsWith("cause.java.")) {
                explanation = "Yakalanan kesin Java hata türü bu tanıyı doğrudan destekliyor.";
            } else if (id.startsWith("cause.spring.")) {
                explanation = "Yakalanan Spring hata türü bu tanıyı doğrudan destekliyor.";
            } else if (id.startsWith("cause.web.")) {
                explanation = "Yakalanan web veya serileştirme hata türü bu tanıyı destekliyor.";
            } else if (id.startsWith("cause.database.")) {
                explanation =
                        "Yakalanan kalıcılık hata türü veya yapılandırılmış SQLState bu tanıyı"
                                + " destekliyor.";
            } else if (id.startsWith("cause.security.")) {
                explanation = "Yakalanan güvenlik hata türü bu tanıyı doğrudan destekliyor.";
            } else {
                explanation = "Yakalanan altyapı hata türü bu tanıyı doğrudan destekliyor.";
            }
            values.put(id, explanation);
        }
        return Map.copyOf(values);
    }

    private static Map<String, String> checks() {
        Map<String, String> values = new HashMap<>();
        values.put(
                "check.java.null-reference",
                "İlk uygulama karesini ve burada erişilen değerleri inceleyin.");
        values.put(
                "check.java.invalid-argument",
                "Çağıran tarafın sözleşmesini ve ilk uygulama karesindeki argümanları kontrol"
                        + " edin.");
        values.put(
                "check.java.invalid-state",
                "İlk uygulama karesinden önceki yaşam döngüsü ve durum geçişlerini kontrol edin.");
        values.put(
                "check.java.memory",
                "JVM bellek havuzunu, ayırma baskısını ve yapılandırılmış sınırları inceleyin.");
        values.put(
                "check.java.recursion",
                "Sınırsız özyineleme veya döngüsel çağrılar için tekrarlanan kareleri inceleyin.");
        values.put(
                "check.java.concurrent-timeout",
                "Beklenen işlemi, süre sınırını, executor doluluğunu ve alt sistem gecikmesini"
                        + " kontrol edin.");
        values.put(
                "check.java.unsupported-operation",
                "Somut implementasyonun işlemi destekleyip desteklemediğini ve nesnenin değişmez"
                        + " olup olmadığını kontrol edin.");
        values.put(
                "check.java.runtime-type",
                "Cast işleminden önce değeri üreten ifadeyi ve generic tür sınırını inceleyin.");
        values.put(
                "check.java.index-range",
                "Koleksiyon veya dizi boyutunu ve hesaplanan dizini kontrol edin.");
        values.put(
                "check.java.number-input",
                "Ayrıştırma sınırındaki giriş biçimini ve locale varsayımlarını kontrol edin.");
        values.put(
                "check.java.runtime-classpath",
                "Çalışma zamanı classpath'ini, module path'i ve istenen sınıf adını kontrol edin.");
        values.put(
                "check.java.collection-mutation",
                "Dolaşım sırasında koleksiyon değişikliklerini ve iş parçacıkları arası erişimi"
                        + " inceleyin.");
        values.put(
                "check.java.executor-capacity",
                "Executor kapanma durumunu, kuyruk kapasitesini, rejection politikasını ve görev"
                        + " geliş hızını kontrol edin.");

        values.put(
                "check.spring.bean-registration",
                "Component scan kapsamını, koşullu yapılandırmayı, qualifier'ları ve bean"
                        + " tanımlarını kontrol edin.");
        values.put(
                "check.spring.bean-qualifier",
                "Qualifier'ları, primary bean'leri ve enjeksiyon noktasının türünü kontrol edin.");
        values.put(
                "check.spring.dependency-chain",
                "Neden zincirindeki ilk başarısız bağımlılığı bulun ve bean tanımını doğrulayın.");
        values.put(
                "check.spring.dependency-cycle",
                "Constructor ve factory metodu bağımlılıklarında döngü olup olmadığını inceleyin.");
        values.put(
                "check.spring.bean-cause",
                "Daha derindeki nedeni ve etkilenen bean yapılandırmasını inceleyin.");
        values.put(
                "check.spring.bean-names",
                "Yinelenen bean adlarını ve configuration import'larını kontrol edin.");
        values.put(
                "check.spring.configuration-properties",
                "Özellik adlarını, değer türlerini, etkin profilleri ve yapılandırma kaynaklarını"
                        + " kontrol edin.");
        values.put(
                "check.spring.placeholder",
                "Etkin property source'ları ve yer tutucu anahtarını kontrol edin.");
        values.put(
                "check.spring.configuration-conversion",
                "Kaynak değeri, hedef türü, kayıtlı converter'ları ve etkin yapılandırmayı kontrol"
                        + " edin.");
        values.put(
                "check.spring.invalid-property",
                "Özellik adını, yazılabilirliğini, erişim metotlarını ve yapılandırılmış değeri"
                        + " kontrol edin.");
        values.put(
                "check.spring.bean-definition-processing",
                "Daha derindeki nedeni, configuration sınıfını, import'ları ve koşullu tanımları"
                        + " inceleyin.");
        values.put(
                "check.spring.condition-evaluation",
                "Koşulu, başvurulan sınıf veya özelliği, etkin profilleri ve daha derindeki nedeni"
                        + " inceleyin.");
        values.put(
                "check.spring.property-conversion-message",
                "Yapılandırılmış değeri, bildirilen özellik türünü ve kayıtlı converter'ları"
                        + " kontrol edin.");

        values.put(
                "check.web.request-validation",
                "Reddedilen alanları ve bu alanlardaki doğrulama kısıtlarını inceleyin.");
        values.put(
                "check.web.method-validation",
                "Metot parametresi ve dönüş değeri kısıtlarını inceleyin.");
        values.put(
                "check.web.constraint-violation",
                "İhlal edilen kısıtı ve sınıra gönderilen değeri inceleyin.");
        values.put(
                "check.web.request-body-unreadable",
                "Content-Type değerini, JSON yapısını, alan türlerini ve istek kodlamasını kontrol"
                        + " edin.");
        values.put(
                "check.web.response-body-unwritable",
                "Yanıt türünü, serializer yapılandırmasını ve döngüsel nesne referanslarını"
                        + " kontrol edin.");
        values.put(
                "check.web.json-shape-mismatch",
                "Gelen JSON yapısını hedef veri türüyle karşılaştırın.");
        values.put(
                "check.web.json-value-format",
                "Hedef alan türünü ve kabul ettiği metin biçimini kontrol edin.");
        values.put(
                "check.web.missing-request-parameter",
                "Endpoint sözleşmesini ve istek parametresinin adını kontrol edin.");
        values.put(
                "check.web.request-parameter-type",
                "Gelen değeri ve controller parametresinin türünü kontrol edin.");
        values.put(
                "check.web.http-method-not-supported",
                "Endpoint mapping'ini ve istemcinin kullandığı HTTP metodunu kontrol edin.");
        values.put(
                "check.web.media-type-not-supported",
                "Content-Type header'ını ve endpoint consumes tanımlarını kontrol edin.");
        values.put(
                "check.web.handler-not-found",
                "İstek yolunu, context path'i ve controller mapping'lerini kontrol edin.");
        values.put(
                "check.web.resource-not-found",
                "İstek yolunu, statik kaynak konumlarını ve controller mapping'lerini kontrol edin.");
        values.put(
                "check.web.json-parse-message",
                "İstek JSON söz dizimini doğrulayın ve alanları endpoint sözleşmesiyle"
                        + " karşılaştırın.");
        values.put(
                "check.web.validation-message",
                "Reddedilen alanları ve tanımlı doğrulama kısıtlarını inceleyin.");
        values.put(
                "check.web.enum-conversion-message",
                "Gelen değeri endpoint enum sabitleri ve büyük-küçük harf politikasıyla"
                        + " karşılaştırın.");

        values.put(
                "check.database.bad-sql-grammar",
                "Üretilen SQL'i, tanımlayıcıları, şema migration'larını ve veritabanı dialect'ini"
                        + " kontrol edin.");
        values.put(
                "check.database.sql-grammar",
                "Üretilen SQL'i, tablo ve sütun adlarını, migration'ları ve dialect'i kontrol"
                        + " edin.");
        values.put(
                "check.database.jdbc-connection",
                "Veritabanı erişilebilirliğini, havuz durumunu, kimlik bilgilerini ve bağlantı"
                        + " sınırlarını kontrol edin.");
        values.put(
                "check.database.transient-connection",
                "Havuz kapasitesini, bağlantı alma süresini, veritabanı sınırlarını ve servis"
                        + " erişilebilirliğini kontrol edin.");
        values.put(
                "check.database.pool-initialization",
                "JDBC URL'sini, sürücüyü, kimlik bilgilerini, erişimi ve havuz yapılandırmasını"
                        + " kontrol edin.");
        values.put(
                "check.database.optimistic-lock",
                "İşlemi yinelemeden önce entity version değerini ve eşzamanlı güncellemeleri"
                        + " kontrol edin.");
        values.put(
                "check.database.pessimistic-lock",
                "Kilit sırasını, kilit süresini, transaction süresini ve rakip transaction'ları"
                        + " kontrol edin.");
        values.put(
                "check.database.transaction-required",
                "Transaction sınırlarını, propagation ayarını, proxy çağrısını ve thread"
                        + " geçişini kontrol edin.");
        values.put(
                "check.database.lazy-initialization",
                "Gerekli veriyi transaction içinde yükleyin veya kalıcılık sınırından çıkmadan"
                        + " önce eşleyin.");
        values.put(
                "check.database.query-timeout",
                "Sorgu planını, bloklayan kilitleri, sonuç boyutunu ve sorgu zaman aşımını kontrol"
                        + " edin.");
        values.put(
                "check.database.driver-error",
                "SQLState'i, vendor kodunu, veritabanı loglarını ve ilk uygulama karesindeki"
                        + " işlemi kontrol edin.");
        values.put(
                "check.database.unique-constraint",
                "Çakışan anahtarı ve işlemin insert mi update mi olması gerektiğini kontrol edin.");
        values.put(
                "check.database.foreign-key-constraint",
                "Başvurulan satırları ve ilişkili insert, update veya delete sırasını kontrol"
                        + " edin.");
        values.put(
                "check.database.not-null-constraint",
                "Eşlenen alanı ve kalıcılık işleminden önce sağlanan değeri kontrol edin.");
        values.put(
                "check.database.missing-table",
                "Tablo adını, etkin şemayı, search path'i ve uygulanmış migration'ları kontrol"
                        + " edin.");
        values.put(
                "check.database.missing-column",
                "Sütun adını, entity mapping'ini, üretilen SQL'i ve migration'ları kontrol edin.");
        values.put(
                "check.database.sql-syntax",
                "Üretilen SQL'i, veritabanı dialect'ini ve bildirilen konumdaki söz dizimini"
                        + " kontrol edin.");
        values.put(
                "check.database.deadlock",
                "Sınırlı retry uygulamadan önce kilit sırasını ve rakip transaction sürelerini"
                        + " kontrol edin.");
        values.put(
                "check.database.lock-not-available",
                "Rakip transaction'ı, kilit modunu ve kilit zaman aşımını kontrol edin.");
        values.put(
                "check.database.query-canceled",
                "Statement timeout'u, açık iptali, bloklayan kilitleri ve sorgu maliyetini"
                        + " kontrol edin.");
        values.put(
                "check.database.connection-exception",
                "Veritabanı erişimini, listener portunu, bağlantı havuzunu ve kimlik bilgilerini"
                        + " kontrol edin.");
        values.put(
                "check.database.data-exception",
                "Değer biçimini, sayısal aralıkları, kodlamayı ve hedef sütun türünü kontrol"
                        + " edin.");
        values.put(
                "check.database.integrity-constraint",
                "Adı verilen kısıtı ve yazılan satır değerlerini kontrol edin.");
        values.put(
                "check.database.authorization-specification",
                "Veritabanı kullanıcısını, kimlik bilgisi kaynağını ve verilen yetkileri kontrol"
                        + " edin.");
        values.put(
                "check.database.transaction-rollback",
                "Serialization hatalarını, deadlock'ları ve transaction retry politikasını"
                        + " kontrol edin.");
        values.put(
                "check.database.sql-syntax-or-access",
                "Üretilen SQL'i, şema adlarını, migration'ları ve veritabanı yetkilerini kontrol"
                        + " edin.");
        values.put(
                "check.database.sql-state",
                "SQLState'i kullanılan veritabanı üreticisinin belgelerinde inceleyin.");
        values.put(
                "check.database.unique-constraint-message",
                "Çakışan anahtarı ve sürücünün bildirdiği veritabanı kısıtını kontrol edin.");
        values.put(
                "check.database.missing-table-message", values.get("check.database.missing-table"));
        values.put(
                "check.database.missing-column-message",
                values.get("check.database.missing-column"));
        values.put("check.database.sql-syntax-message", values.get("check.database.sql-syntax"));
        values.put(
                "check.database.connection-refused-message",
                "Veritabanı sunucusunu, portu, listener'ı, firewall'u ve servis durumunu kontrol"
                        + " edin.");
        values.put(
                "check.database.connection-timeout-message",
                "Ağ gecikmesini, listener erişimini ve bağlantı zaman aşımı ayarını kontrol edin.");
        values.put(
                "check.database.pool-exhaustion-message",
                "Sızan bağlantıları, havuz kapasitesini, bağlantı alma süresini ve veritabanı"
                        + " sınırlarını kontrol edin.");
        values.put(
                "check.database.deadlock-message",
                "Sınırlı retry uygulamadan önce kilit sırasını ve transaction sürelerini kontrol"
                        + " edin.");
        values.put(
                "check.database.lock-unavailable-message",
                "Rakip transaction'ları, kilit sırasını, transaction süresini ve kilit zaman"
                        + " aşımını kontrol edin.");
        values.put(
                "check.database.transaction-required-message",
                values.get("check.database.transaction-required"));
        values.put(
                "check.database.lazy-initialization-message",
                values.get("check.database.lazy-initialization"));
        values.put(
                "check.database.query-timeout-message", values.get("check.database.query-timeout"));

        values.put(
                "check.security.bad-credentials",
                "Kimlik bilgisi kaynağını ve authentication provider yapılandırmasını kontrol"
                        + " edin.");
        values.put(
                "check.security.credentials-not-found",
                "Security filter chain kapsamını ve security context aktarımını kontrol edin.");
        values.put(
                "check.security.insufficient-authentication",
                "Gerekli kimlik doğrulama mekanizmasını ve oluşturulan security context'i"
                        + " kontrol edin.");
        values.put(
                "check.security.account-locked",
                "Hesap durumunu ve hesabı kilitleyen ya da açan politikayı kontrol edin.");
        values.put(
                "check.security.account-disabled",
                "Hesap durumunu ve kullanıcı sağlama sürecini kontrol edin.");
        values.put(
                "check.security.access-denied",
                "Kimliği doğrulanmış kullanıcıyı, verilen yetkileri ve authorization kuralını"
                        + " kontrol edin.");
        values.put(
                "check.security.authorization-denied",
                "Metot veya istek authorization kurallarını ve kullanıcının yetkilerini kontrol"
                        + " edin.");
        values.put(
                "check.security.credentials-expired",
                "Kimlik bilgisi yenilemeyi, token ömrünü, saat eşitlemesini ve yenileme akışını"
                        + " kontrol edin.");
        values.put(
                "check.security.jwt-validation",
                "Token süresini, issuer ve audience değerlerini, imzayı ve saat eşitlemesini"
                        + " kontrol edin.");
        values.put(
                "check.security.malformed-token",
                "Bearer token çıkarımını, JWT yapısını, kodlamayı ve imzayı kontrol edin.");
        values.put(
                "check.security.invalid-bearer-token",
                "Token biçimini, issuer ve audience değerlerini, imzayı, süreyi ve decoder"
                        + " yapılandırmasını kontrol edin.");
        values.put(
                "check.security.token-expired",
                "Token ömrünü, istemci yenilemesini ve saat eşitlemesini kontrol edin.");
        values.put(
                "check.security.token-decode",
                "Bearer token çıkarımını, JWT yapısını ve kodlamayı kontrol edin.");
        values.put(
                "check.security.access-denied-message", values.get("check.security.access-denied"));
        values.put(
                "check.security.authentication-required-message",
                "Kimlik bilgilerinin çıkarılmasını, security context oluşturulmasını ve endpoint"
                        + " politikasını kontrol edin.");
        values.put(
                "check.security.token-expired-message", values.get("check.security.token-expired"));
        values.put(
                "check.security.malformed-token-message",
                values.get("check.security.malformed-token"));

        values.put(
                "check.infrastructure.dns-resolution",
                "Sunucu adını, DNS resolver'ı, search domain'leri ve ağ yolunu kontrol edin.");
        values.put(
                "check.infrastructure.tls-handshake",
                "Sertifika güvenini, geçerliliğini, sunucu adını, protokolü ve cipher uyumluluğunu"
                        + " kontrol edin.");
        values.put(
                "check.infrastructure.tls-protocol",
                "Daha derindeki nedeni, karşı taraf sertifikasını ve TLS protokol ayarlarını"
                        + " kontrol edin.");
        values.put(
                "check.infrastructure.socket-timeout",
                "Üst sistem gecikmesini ve bağlantı ya da okuma zaman aşımı ayarını kontrol edin.");
        values.put(
                "check.infrastructure.http-timeout",
                "Üst sistem gecikmesini ve HTTP zaman aşımı ayarını kontrol edin.");
        values.put(
                "check.infrastructure.connection-failed",
                "Hedef sunucuyu ve portu, listener'ı, firewall'u ve yönlendirmeyi kontrol edin.");
        values.put(
                "check.infrastructure.no-route",
                "Yönlendirmeyi, ağ politikasını, firewall kurallarını ve hedef erişimini kontrol"
                        + " edin.");
        values.put(
                "check.infrastructure.socket-failure",
                "Daha derindeki nedeni, bağlantı yaşam döngüsünü ve karşı tarafın durumunu"
                        + " kontrol edin.");
        values.put(
                "check.infrastructure.connection-refused-message",
                "Hedef listener'ı, sunucuyu, portu ve firewall politikasını kontrol edin.");
        values.put(
                "check.infrastructure.timed-out-message",
                "Sınırları artırmadan önce zaman aşımı ayarını ve alt sistem gecikmesini kontrol"
                        + " edin.");
        values.put(
                "check.infrastructure.dns-resolution-message",
                values.get("check.infrastructure.dns-resolution"));

        values.put(
                "check.inspect-root-cause",
                "Kök hata türünü, mesajını ve ilk uygulama karesini inceleyin.");
        return Map.copyOf(values);
    }
}
