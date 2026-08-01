package io.github.kalayciburak.stackmate.export;

import java.nio.file.Path;
import java.util.Objects;

/** Bounded outcome of an optional local incident export attempt. */
public sealed interface IncidentExportResult
        permits IncidentExportResult.Exported,
                IncidentExportResult.Rejected,
                IncidentExportResult.Failed {

    /** Successful export location. */
    record Exported(Path directory) implements IncidentExportResult {
        public Exported {
            directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        }
    }

    /** Policy or path validation rejection before files were published. */
    record Rejected(String reason) implements IncidentExportResult {
        public Rejected {
            reason = requireReason(reason);
        }
    }

    /** Safe failure after an export attempt. */
    record Failed(String reason) implements IncidentExportResult {
        public Failed {
            reason = requireReason(reason);
        }
    }

    private static String requireReason(String reason) {
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank() || reason.length() > 256) {
            throw new IllegalArgumentException("reason must contain 1 to 256 characters");
        }
        return reason;
    }
}
