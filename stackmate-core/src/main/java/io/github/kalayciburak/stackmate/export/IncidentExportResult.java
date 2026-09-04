package io.github.kalayciburak.stackmate.export;

import java.nio.file.Path;
import java.util.Objects;

/** Bounded outcome of an optional local incident export attempt. */
public sealed interface IncidentExportResult
        permits IncidentExportResult.Exported,
                IncidentExportResult.Rejected,
                IncidentExportResult.Failed {

    /**
     * Successful export location.
     *
     * @param directory normalized absolute directory containing the exported files
     */
    record Exported(Path directory) implements IncidentExportResult {
        /**
         * Creates a successful export outcome.
         *
         * @param directory directory containing the exported files
         */
        public Exported {
            directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        }
    }

    /**
     * Policy or path validation rejection before files were published.
     *
     * @param reason safe explanation of why the export was rejected
     */
    record Rejected(String reason) implements IncidentExportResult {
        /**
         * Creates a policy or validation rejection outcome.
         *
         * @param reason safe explanation of why the export was rejected
         */
        public Rejected {
            reason = requireReason(reason);
        }
    }

    /**
     * Safe failure after an export attempt.
     *
     * @param reason safe explanation of the export failure
     */
    record Failed(String reason) implements IncidentExportResult {
        /**
         * Creates a safe export failure outcome.
         *
         * @param reason safe explanation of the export failure
         */
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
