package io.github.kalayciburak.stackmate.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
                        "--stackmate.inference.trigger-policy=always",
                        "--stackmate.inference.timeout=30s",
                        "--stackmate.console.debug-logging=true")) {
            int mark = sample.mark();
            PackagedSample.Response response = sample.get("/demo/invalid-number");
            sample.awaitManagedChild();
            sample.awaitAnyOutputSince(
                    mark,
                    Duration.ofSeconds(45),
                    "StackMate Local Explanation",
                    "StackMate local inference skipped safely");

            assertThat(response.status()).isEqualTo(500);
            assertThat(response.elapsed()).isLessThan(Duration.ofSeconds(2));
            assertThat(sample.outputSince(mark))
                    .contains("StackMate Quick Diagnosis")
                    .satisfies(
                            output ->
                                    assertThat(output)
                                            .containsAnyOf(
                                                    "StackMate Local Explanation",
                                                    "StackMate local inference skipped safely"));
        }
    }
}
