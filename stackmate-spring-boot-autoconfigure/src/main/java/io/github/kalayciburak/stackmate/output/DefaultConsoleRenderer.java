package io.github.kalayciburak.stackmate.output;

import io.github.kalayciburak.stackmate.analysis.CheckCandidate;
import io.github.kalayciburak.stackmate.analysis.DeterministicResult;
import io.github.kalayciburak.stackmate.analysis.DiagnosedCause;
import io.github.kalayciburak.stackmate.deduplication.DeduplicationResult;
import io.github.kalayciburak.stackmate.fingerprint.ExceptionFingerprint;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import io.github.kalayciburak.stackmate.incident.StackFrameSnapshot;
import io.github.kalayciburak.stackmate.privacy.Sanitizer;
import io.github.kalayciburak.stackmate.throwable.ThrowableNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Default English deterministic renderer for plain log and terminal output. */
public final class DefaultConsoleRenderer implements ConsoleRenderer {

    private static final int MAX_CHECKS = 3;
    private static final int MAX_TRACE_FRAMES = 5;

    private final Sanitizer sanitizer;

    /**
     * Creates the default renderer.
     *
     * @param sanitizer sanitizer applied to all rendered diagnostic text
     */
    public DefaultConsoleRenderer(Sanitizer sanitizer) {
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
    }

    @Override
    public String render(DeterministicResult result, RenderOptions options) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(options, "options");
        IncidentSnapshot incident = result.incident();
        DiagnosedCause cause = result.diagnosis().causes().getFirst();
        ThrowableNode root = incident.throwable().root();
        List<String> lines = new ArrayList<>();
        lines.add("StackMate Quick Diagnosis");
        lines.add("Incident: " + displayId(result.fingerprint()));
        lines.add("Category: " + cause.category().name());
        lines.add("Evidence: " + cause.strength().name());
        lines.add("Summary: " + safe("causeTitle", cause.title()));
        lines.add("Root: " + root(root));

        if (!incident.applicationFrames().isEmpty()) {
            lines.add("First app location: " + frame(incident.applicationFrames().getFirst()));
        }
        if (options.showDetails()) {
            lines.add("Details: " + safe("explanation", cause.explanation()));
            appendRequest(lines, incident);
        }
        appendChecks(lines, result);
        if (options.showCauseChain()) {
            appendCauseChain(lines, incident);
        }
        if (options.showApplicationTrace()) {
            appendTrace(lines, incident);
        }
        lines.add("Source: Deterministic rules");
        lines.add("Runtime: " + runtime(incident));
        if (result.diagnosis().fallback()) {
            lines.add("Note: Insufficient context for a conclusive diagnosis.");
        }
        return format(lines, options);
    }

    @Override
    public String renderDuplicate(
            DeterministicResult result, DeduplicationResult duplicate, RenderOptions options) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(duplicate, "duplicate");
        Objects.requireNonNull(options, "options");
        return format(
                List.of(
                        "StackMate duplicate "
                                + displayId(result.fingerprint())
                                + " (count "
                                + duplicate.occurrenceCount()
                                + ")"),
                options);
    }

    @Override
    public String renderSevereJvmFailure(
            ExceptionFingerprint fingerprint, String failureType, RenderOptions options) {
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(failureType, "failureType");
        Objects.requireNonNull(options, "options");
        return format(
                List.of(
                        "StackMate severe JVM failure "
                                + displayId(fingerprint)
                                + ": "
                                + safe("throwableType", failureType)),
                options);
    }

    private void appendChecks(List<String> lines, DeterministicResult result) {
        List<CheckCandidate> checks = result.diagnosis().checks();
        if (checks.isEmpty()) {
            lines.add("Checks: No evidence-backed next check is available.");
            return;
        }
        lines.add("First checks:");
        checks.stream()
                .limit(MAX_CHECKS)
                .forEach(
                        check -> lines.add("  - " + safe("checkInstruction", check.instruction())));
    }

    private void appendCauseChain(List<String> lines, IncidentSnapshot incident) {
        lines.add("Cause chain:");
        for (ThrowableNode node : incident.throwable().causeChain()) {
            lines.add("  - " + root(node));
        }
    }

    private void appendTrace(List<String> lines, IncidentSnapshot incident) {
        if (incident.applicationFrames().isEmpty()) {
            return;
        }
        lines.add("Application trace:");
        incident.applicationFrames().stream()
                .limit(MAX_TRACE_FRAMES)
                .forEach(applicationFrame -> lines.add("  " + frame(applicationFrame)));
    }

    private void appendRequest(List<String> lines, IncidentSnapshot incident) {
        var request = incident.requestContext();
        if (request.method().isBlank() && request.path().isBlank()) {
            return;
        }
        lines.add(
                "Request: "
                        + safe("requestMethod", request.method())
                        + " "
                        + safe("requestPath", request.path()));
    }

    private String root(ThrowableNode node) {
        String type = safe("throwableType", node.type());
        String message = safe("throwableMessage", node.message());
        return message.isBlank() ? type : type + ": " + message;
    }

    private String frame(StackFrameSnapshot stackFrame) {
        String className = safe("className", stackFrame.className());
        int packageEnd = className.lastIndexOf('.');
        String simpleClassName = packageEnd < 0 ? className : className.substring(packageEnd + 1);
        StringBuilder value =
                new StringBuilder()
                        .append(simpleClassName)
                        .append('#')
                        .append(safe("methodName", stackFrame.methodName()))
                        .append('(')
                        .append(safe("fileName", stackFrame.fileName()));
        if (stackFrame.lineNumber() >= 0) {
            value.append(':').append(stackFrame.lineNumber());
        }
        return value.append(')').toString();
    }

    private String runtime(IncidentSnapshot incident) {
        String javaVersion = safe("javaVersion", incident.jvmRuntimeContext().javaVersion());
        String bootVersion = safe("bootVersion", incident.springRuntimeContext().bootVersion());
        if (javaVersion.isBlank()) {
            javaVersion = "unknown";
        }
        if (bootVersion.isBlank()) {
            bootVersion = "unknown";
        }
        return "Java " + javaVersion + " | Spring Boot " + bootVersion + " | Network disabled";
    }

    private String safe(String fieldName, String value) {
        return sanitizer.sanitize(fieldName, Objects.requireNonNull(value, fieldName));
    }

    private static String displayId(ExceptionFingerprint fingerprint) {
        return "STM-" + fingerprint.value().substring(0, 8).toUpperCase(Locale.ROOT);
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
}
