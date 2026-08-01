package io.github.kalayciburak.stackmate.benchmark.model;

import io.github.kalayciburak.stackmate.inference.InferenceAvailability;
import io.github.kalayciburak.stackmate.inference.jvm.JlamaLocalInferenceEngine;
import java.nio.file.Path;
import java.time.Duration;

/** Explicit, opt-in A/B/C benchmark entry point for a user-provisioned local model. */
public final class LocalModelBenchmarkMain {

    private static final String MODEL_PATH_PROPERTY = "stackmate.model.path";

    private LocalModelBenchmarkMain() {}

    public static void main(String[] arguments) throws Exception {
        Path outputDirectory = arguments.length == 0 ? Path.of("target") : Path.of(arguments[0]);
        Path modelPath = requiredModelPath();
        Duration timeout =
                Duration.ofSeconds(
                        Long.parseLong(
                                System.getProperty("stackmate.benchmark.timeout-seconds", "30")));
        int maximumCases =
                Integer.parseInt(System.getProperty("stackmate.benchmark.maximum-cases", "50"));
        boolean includeRaw =
                Boolean.parseBoolean(System.getProperty("stackmate.benchmark.include-raw", "true"));
        try (var engine = new JlamaLocalInferenceEngine(modelPath, timeout, 1)) {
            InferenceAvailability availability = engine.availability();
            if (availability.status() == InferenceAvailability.Status.UNAVAILABLE) {
                throw new IllegalStateException(
                        "local model is unavailable: " + availability.detail());
            }
            var result = new LocalModelBenchmarkHarness(engine).evaluate(maximumCases, includeRaw);
            var reports =
                    new LocalModelBenchmarkReportWriter()
                            .write(outputDirectory, result, engine.model());
            System.out.println(reports.json().toAbsolutePath());
            System.out.println(reports.markdown().toAbsolutePath());
        }
    }

    private static Path requiredModelPath() {
        String value = System.getProperty(MODEL_PATH_PROPERTY);
        if (value == null || value.isBlank() || value.equals("${stackmate.model.path}")) {
            throw new IllegalArgumentException(
                    "-Dstackmate.model.path=/absolute/path/to/model is required");
        }
        return Path.of(value);
    }
}
