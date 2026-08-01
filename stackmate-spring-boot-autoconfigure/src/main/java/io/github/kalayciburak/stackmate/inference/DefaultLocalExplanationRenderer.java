package io.github.kalayciburak.stackmate.inference;

import io.github.kalayciburak.stackmate.output.RenderLanguage;
import io.github.kalayciburak.stackmate.output.RenderOptions;
import io.github.kalayciburak.stackmate.output.RenderStyle;
import io.github.kalayciburak.stackmate.privacy.Sanitizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Terminal-safe renderer for output that has already passed request-scoped validation. */
public final class DefaultLocalExplanationRenderer implements LocalExplanationRenderer {

    private final Sanitizer sanitizer;

    public DefaultLocalExplanationRenderer(Sanitizer sanitizer) {
        this.sanitizer = Objects.requireNonNull(sanitizer, "sanitizer");
    }

    @Override
    public String render(
            ValidatedInferenceOutput output,
            ModelDescriptor model,
            Duration elapsed,
            RenderOptions options) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(elapsed, "elapsed");
        Objects.requireNonNull(options, "options");
        boolean tr = options.language() == RenderLanguage.TURKISH;
        List<String> lines = new ArrayList<>();
        lines.add(tr ? "StackMate Yerel Açıklama" : "StackMate Local Explanation");
        lines.add(label(tr, "Özet", "Summary") + safe("inferenceSummary", output.summary()));
        if (!output.causes().isEmpty()) {
            lines.add(tr ? "Olası nedenler:" : "Possible causes:");
            output.causes()
                    .forEach(
                            cause ->
                                    lines.add(
                                            "  - " + safe("inferenceCause", cause.explanation())));
        }
        if (!output.checks().isEmpty()) {
            lines.add(tr ? "Önerilen kontroller:" : "Suggested checks:");
            output.checks()
                    .forEach(
                            check ->
                                    lines.add(
                                            "  - " + safe("inferenceCheck", check.instruction())));
        }
        lines.add(label(tr, "Kanıt", "Evidence") + evidenceCount(output));
        lines.add(label(tr, "Süre", "Duration") + Math.max(0, elapsed.toMillis()) + " ms");
        lines.add(
                label(tr, "Kaynak", "Source")
                        + (tr ? "Yerel JVM modeli " : "Local JVM model ")
                        + safe("modelName", model.displayName())
                        + " "
                        + safe("modelVersion", model.version())
                        + " | "
                        + (tr ? "Ağ devre dışı" : "Network disabled"));
        return format(lines, options);
    }

    private static int evidenceCount(ValidatedInferenceOutput output) {
        Set<String> facts = new HashSet<>();
        output.causes().forEach(cause -> facts.addAll(cause.factIds()));
        output.checks().forEach(check -> facts.addAll(check.factIds()));
        return facts.size();
    }

    private String safe(String field, String value) {
        return sanitizer.sanitize(field, Objects.requireNonNull(value, field));
    }

    private static String label(boolean tr, String turkish, String english) {
        return (tr ? turkish : english) + ": ";
    }

    private static String format(List<String> sourceLines, RenderOptions options) {
        int contentWidth =
                options.style() == RenderStyle.BOX ? options.maxWidth() - 4 : options.maxWidth();
        List<String> lines = new ArrayList<>();
        sourceLines.forEach(line -> lines.addAll(wrap(line, contentWidth)));
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
