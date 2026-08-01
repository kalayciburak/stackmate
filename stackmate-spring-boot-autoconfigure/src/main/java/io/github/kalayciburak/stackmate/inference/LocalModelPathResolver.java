package io.github.kalayciburak.stackmate.inference;

import io.github.kalayciburak.stackmate.configuration.StackMateProperties;
import java.nio.file.Path;
import java.util.Objects;

/** Resolves an explicit user-provisioned model directory without downloading anything. */
public final class LocalModelPathResolver {

    static final String ENVIRONMENT_NAME = "STACKMATE_MODEL_PATH";
    static final String DEFAULT_MODEL_DIRECTORY = "Qwen2.5-0.5B-Instruct";

    private LocalModelPathResolver() {}

    public static Path resolve(StackMateProperties properties) {
        Objects.requireNonNull(properties, "properties");
        return resolve(
                properties.getInference().getModelPath(),
                environment(ENVIRONMENT_NAME),
                systemProperty("user.home"));
    }

    static Path resolve(String configured, String environment, String userHome) {
        String explicit = configured == null ? "" : configured.strip();
        if (!explicit.isEmpty()) {
            return Path.of(explicit);
        }
        String fromEnvironment = environment == null ? "" : environment.strip();
        if (!fromEnvironment.isEmpty()) {
            return Path.of(fromEnvironment);
        }
        String home = userHome == null ? "" : userHome.strip();
        if (!home.isEmpty()) {
            return Path.of(home, ".stackmate", "models", DEFAULT_MODEL_DIRECTORY);
        }
        return Path.of(".stackmate", "models", DEFAULT_MODEL_DIRECTORY);
    }

    private static String environment(String name) {
        try {
            return System.getenv(name);
        } catch (SecurityException ignored) {
            return "";
        }
    }

    private static String systemProperty(String name) {
        try {
            return System.getProperty(name, "");
        } catch (SecurityException ignored) {
            return "";
        }
    }
}
