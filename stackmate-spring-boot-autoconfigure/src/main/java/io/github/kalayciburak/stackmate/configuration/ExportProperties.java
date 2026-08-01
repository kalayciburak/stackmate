package io.github.kalayciburak.stackmate.configuration;

import java.nio.file.Path;
import java.util.Objects;

/** Controls optional local incident export. */
public class ExportProperties {

    private boolean enabled;
    private Path directory = Path.of(".stackmate", "incidents");
    private int maximumIncidents = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Path getDirectory() {
        return directory;
    }

    public void setDirectory(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    public int getMaximumIncidents() {
        return maximumIncidents;
    }

    public void setMaximumIncidents(int maximumIncidents) {
        this.maximumIncidents =
                PropertyValidation.range(maximumIncidents, 1, 10_000, "maximumIncidents");
    }
}
