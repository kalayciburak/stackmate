package io.github.kalayciburak.stackmate.configuration;

import java.nio.file.Path;
import java.util.Objects;

/** Controls optional local incident export. */
public class ExportProperties {

    private boolean enabled;
    private Path directory = Path.of(".stackmate", "incidents");

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
}
