package io.github.kalayciburak.stackmate.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PackagedRealModelIT {

    @Test
    void runsAnExplicitLocalModelThroughThePackagedChildLifecycle() throws Exception {
        String configured = System.getProperty("stackmate.e2e.model-path", "");
        assertThat(configured)
                .as("-Dstackmate.model.path must point to an existing local model directory")
                .isNotBlank();
        Path model = Path.of(configured).toAbsolutePath().normalize();
        assertThat(model).isDirectory();
        assertThat(Files.isSymbolicLink(model)).isFalse();

        try (var sample =
                PackagedSample.start(
                        "e2e,hybrid",
                        "--stackmate.inference.model-path=" + model,
                        "--stackmate.inference.timeout=30s")) {
            int mark = sample.mark();
            PackagedSample.Response response = sample.get("/demo/invalid-number");
            sample.awaitOutputSince(mark, "StackMate Local Explanation");

            assertThat(response.status()).isEqualTo(500);
            assertThat(response.elapsed()).isLessThan(java.time.Duration.ofSeconds(2));
            assertThat(sample.outputSince(mark))
                    .contains("StackMate Quick Diagnosis", "StackMate Local Explanation");
            sample.awaitManagedChild();
        }
    }
}
