package io.github.kalayciburak.stackmate.configuration;

import java.nio.file.Path;
import java.util.Objects;

/** Controls optional local incident export. */
public class ExportProperties {

    private boolean enabled;
    private Path directory = Path.of(".stackmate", "incidents");
    private int maximumIncidents = 100;

    /** Creates export properties with export disabled and the default local directory. */
    public ExportProperties() {}

    /**
     * Returns whether sanitized incident bundles are exported to the local filesystem.
     *
     * @return whether incident export is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether sanitized incident bundles are exported to the local filesystem.
     *
     * @param enabled whether incident export is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the configured root directory for incident bundles.
     *
     * @return configured export root
     */
    public Path getDirectory() {
        return directory;
    }

    /**
     * Sets the root directory for incident bundles.
     *
     * @param directory non-null export root
     */
    public void setDirectory(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    /**
     * Returns the maximum number of published incident directories retained.
     *
     * @return maximum retained incident count
     */
    public int getMaximumIncidents() {
        return maximumIncidents;
    }

    /**
     * Sets the maximum number of published incident directories retained.
     *
     * @param maximumIncidents retention bound from 1 through 10,000
     */
    public void setMaximumIncidents(int maximumIncidents) {
        this.maximumIncidents =
                PropertyValidation.range(maximumIncidents, 1, 10_000, "maximumIncidents");
    }
}
