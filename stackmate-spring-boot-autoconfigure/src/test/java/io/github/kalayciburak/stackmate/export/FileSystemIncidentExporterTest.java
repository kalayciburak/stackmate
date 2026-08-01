package io.github.kalayciburak.stackmate.export;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.kalayciburak.stackmate.analysis.CheckCandidate;
import io.github.kalayciburak.stackmate.analysis.DeterministicResult;
import io.github.kalayciburak.stackmate.analysis.DiagnosedCause;
import io.github.kalayciburak.stackmate.analysis.Diagnosis;
import io.github.kalayciburak.stackmate.analysis.DiagnosticCategory;
import io.github.kalayciburak.stackmate.analysis.DiagnosticStrength;
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
import io.github.kalayciburak.stackmate.privacy.SanitizerConfig;
import io.github.kalayciburak.stackmate.throwable.SqlExceptionDetails;
import io.github.kalayciburak.stackmate.throwable.ThrowableNode;
import io.github.kalayciburak.stackmate.throwable.ThrowableSnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemIncidentExporterTest {

    @TempDir Path temporaryDirectory;

    @Test
    void publishesFourSanitizedFilesAtomically() throws IOException {
        Path root = temporaryDirectory.resolve("incidents");
        var exporter = exporter(root, 10);

        IncidentExportResult result =
                exporter.export(result('a', "password=hunter2 Bearer abc.def.ghi"));

        var exported = (IncidentExportResult.Exported) result;
        assertThat(exported.directory()).hasParent(root.toAbsolutePath());
        assertThat(exported.directory())
                .isDirectoryContaining(
                        path -> path.getFileName().toString().equals("incident.json"))
                .isDirectoryContaining(path -> path.getFileName().toString().equals("diagnosis.md"))
                .isDirectoryContaining(
                        path -> path.getFileName().toString().equals("stacktrace.txt"))
                .isDirectoryContaining(
                        path -> path.getFileName().toString().equals("agent-prompt.md"));
        String bundle = readBundle(exported.directory());
        assertThat(bundle)
                .doesNotContain("hunter2", "abc.def.ghi")
                .contains("[REDACTED]", "no source file was copied");
        try (var entries = Files.list(root)) {
            assertThat(entries.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.startsWith(".stackmate-export-"));
        }
    }

    @Test
    void doesNotTruncateTheStructuredBundleAtThePerValueSanitizerLimit() throws IOException {
        Path root = temporaryDirectory.resolve("bounded-values");
        var sanitizer =
                new DefaultSanitizer(
                        new SanitizerConfig(
                                64, "[REDACTED]", true, true, true, Set.of(), List.of()));
        var exporter = new FileSystemIncidentExporter(root, 10, sanitizer);

        Path directory =
                ((IncidentExportResult.Exported) exporter.export(result('9', "x".repeat(256))))
                        .directory();
        String json = Files.readString(directory.resolve("incident.json"));

        assertThat(json).contains("\"evidence\"").endsWith("}\n").hasSizeGreaterThan(64);
    }

    @Test
    void returnsExistingSafeBundleForTheSameFingerprint() throws IOException {
        var exporter = exporter(temporaryDirectory.resolve("incidents"), 10);
        DeterministicResult incident = result('b', "same");

        var first = (IncidentExportResult.Exported) exporter.export(incident);
        var second = (IncidentExportResult.Exported) exporter.export(incident);

        assertThat(second.directory()).isEqualTo(first.directory());
        try (var files = Files.list(first.directory())) {
            assertThat(files).hasSize(4);
        }
    }

    @Test
    void rejectsAnExistingIncompleteFingerprintDirectory() throws IOException {
        Path root = Files.createDirectories(temporaryDirectory.resolve("incidents"));
        Path incomplete = Files.createDirectory(root.resolve("STM-FFFFFFFF"));
        Files.writeString(incomplete.resolve("owner-file.txt"), "do not replace");

        IncidentExportResult result = exporter(root, 10).export(result('f', "same"));

        assertThat(result).isInstanceOf(IncidentExportResult.Rejected.class);
        assertThat(incomplete.resolve("owner-file.txt")).hasContent("do not replace");
    }

    @Test
    void enforcesRetentionWithoutRecursivelyDeletingUnknownContent() throws IOException {
        Path root = temporaryDirectory.resolve("incidents");
        var exporter = exporter(root, 1);
        Path first =
                ((IncidentExportResult.Exported) exporter.export(result('c', "first"))).directory();

        IncidentExportResult secondResult = exporter.export(result('d', "second"));

        assertThat(secondResult).isInstanceOf(IncidentExportResult.Exported.class);
        try (var entries = Files.list(root)) {
            assertThat(entries.filter(Files::isDirectory)).hasSize(1);
        }
        assertThat(first).doesNotExist();
    }

    @Test
    void rejectsConfiguredDirectoryThatTraversesASymbolicLink() throws IOException {
        Path actual = Files.createDirectory(temporaryDirectory.resolve("actual"));
        Path link = temporaryDirectory.resolve("linked");
        try {
            Files.createSymbolicLink(link, actual);
        } catch (IOException | UnsupportedOperationException | SecurityException unsupported) {
            Assumptions.abort("Symbolic links are unavailable in this test environment");
        }

        IncidentExportResult result =
                exporter(link.resolve("incidents"), 10).export(result('e', "x"));

        assertThat(result).isInstanceOf(IncidentExportResult.Rejected.class);
        assertThat(actual).isEmptyDirectory();
    }

    private FileSystemIncidentExporter exporter(Path root, int maximumIncidents) {
        return new FileSystemIncidentExporter(
                root, maximumIncidents, new DefaultSanitizer(SanitizerConfig.defaults()));
    }

    private String readBundle(Path directory) throws IOException {
        StringBuilder content = new StringBuilder();
        for (String fileName :
                List.of("incident.json", "diagnosis.md", "stacktrace.txt", "agent-prompt.md")) {
            content.append(Files.readString(directory.resolve(fileName), StandardCharsets.UTF_8));
        }
        return content.toString();
    }

    private DeterministicResult result(char digestCharacter, String message) {
        var frame =
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
        var root =
                new ThrowableNode(
                        "java.lang.IllegalStateException",
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
        var incident =
                new IncidentSnapshot(
                        "capture-1",
                        Instant.parse("2026-08-01T12:00:00Z"),
                        new ThrowableSnapshot(List.of(root), 1, false),
                        List.of(frame),
                        new RequestContext(
                                "POST",
                                "/orders",
                                "example.OrderController",
                                "create",
                                500,
                                Map.of()),
                        new SpringRuntimeContext(
                                "shop",
                                List.of("dev"),
                                "example",
                                "4.1.0",
                                "7.0.8",
                                SpringRuntimeContext.TransactionState.INACTIVE),
                        new JvmRuntimeContext(
                                "21.0.12", "OpenJDK", "Amazon", "Windows", "amd64", 8));
        var evidence =
                new EvidenceItem(
                        "fact.throwable",
                        EvidenceType.EXCEPTION_CLASS,
                        "throwable.root",
                        "java.lang.IllegalStateException",
                        EvidenceSensitivity.PUBLIC,
                        EvidenceReliability.HIGH);
        var diagnosis =
                new Diagnosis(
                        List.of(evidence),
                        List.of(
                                new DiagnosedCause(
                                        "cause.illegal-state",
                                        DiagnosticCategory.GENERAL_JAVA,
                                        "An operation encountered an invalid state",
                                        "The captured exception reports an invalid state.",
                                        DiagnosticStrength.HIGH,
                                        List.of(evidence.id()))),
                        List.of(
                                new CheckCandidate(
                                        "check.state",
                                        "Inspect the state transition at the first application frame.",
                                        List.of(evidence.id()))),
                        List.of("general-java@1"),
                        false);
        return new DeterministicResult(
                incident,
                diagnosis,
                new ExceptionFingerprint(String.valueOf(digestCharacter).repeat(64)));
    }
}
