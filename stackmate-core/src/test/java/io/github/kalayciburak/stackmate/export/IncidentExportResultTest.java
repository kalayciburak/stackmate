package io.github.kalayciburak.stackmate.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IncidentExportResultTest {

    @Test
    void normalizesSuccessfulDirectory() {
        var exported = new IncidentExportResult.Exported(Path.of("target", "..", "incidents"));

        assertThat(exported.directory()).isAbsolute();
        assertThat(exported.directory().toString()).doesNotContain("..");
    }

    @Test
    void rejectsBlankOrUnboundedFailureReasons() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IncidentExportResult.Rejected(" "));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IncidentExportResult.Failed("x".repeat(257)));
    }
}
