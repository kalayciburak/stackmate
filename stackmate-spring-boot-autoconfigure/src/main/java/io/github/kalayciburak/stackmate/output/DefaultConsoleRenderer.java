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
            lines.add(label(tr, "Ayrıntı", "Details") + safe("explanation", cause.explanation()));
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
        values.put("cause.java.out-of-memory", "JVM gerekli belleği ayıramadı");
        values.put("cause.java.stack-overflow", "İş parçacığı yığını tükendi");
        values.put("cause.java.concurrent-timeout", "Asenkron işlem süresini aştı");
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
        return Map.copyOf(values);
    }
}
