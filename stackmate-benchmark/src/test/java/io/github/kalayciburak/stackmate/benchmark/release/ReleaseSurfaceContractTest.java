package io.github.kalayciburak.stackmate.benchmark.release;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ReleaseSurfaceContractTest {

    private static final Set<String> PUBLIC_ARTIFACTS =
            Set.of(
                    "stackmate-parent",
                    "stackmate-core",
                    "stackmate-spring-boot-autoconfigure",
                    "stackmate-spring-boot-starter");
    private static final Set<String> FORBIDDEN_PUBLIC_POM_FRAGMENTS =
            Set.of(
                    "stackmate-inference",
                    "stackmate-spring-boot-autoconfigure-local",
                    "stackmate-spring-boot-starter-local",
                    "jlama",
                    "spring-ai",
                    "langchain4j");
    private static final Pattern REACTOR_MODULE = Pattern.compile("<module>([^<]+)</module>");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path repository = repositoryRoot();

    @Test
    void release_allowlist_is_complete_and_non_public_modules_cannot_deploy() throws IOException {
        JsonNode root =
                MAPPER.readTree(repository.resolve("release/release-artifacts.json").toFile());
        assertThat(root.path("schemaVersion").asText()).isEqualTo("1");
        assertThat(root.path("groupId").asText()).isEqualTo("io.github.kalayciburak");
        assertThat(textValues(root.path("publicArtifacts")))
                .containsExactlyInAnyOrderElementsOf(PUBLIC_ARTIFACTS);

        Set<String> declared = new HashSet<>();
        for (JsonNode module : root.path("modules")) {
            String artifactId = module.path("artifactId").asText();
            assertThat(declared.add(artifactId)).as("unique module %s", artifactId).isTrue();
            boolean deploy = module.path("deploy").asBoolean();
            assertThat(deploy)
                    .as("deploy flag for %s", artifactId)
                    .isEqualTo(PUBLIC_ARTIFACTS.contains(artifactId));
            if (!deploy && !artifactId.equals("research/model-alignment")) {
                String pom = Files.readString(repository.resolve(artifactId).resolve("pom.xml"));
                assertThat(pom)
                        .as("deploy must be disabled for %s", artifactId)
                        .contains("<maven.deploy.skip>true</maven.deploy.skip>");
            }
        }

        assertThat(declared).containsAll(PUBLIC_ARTIFACTS);
        assertThat(declared).contains("research/model-alignment");
        assertThat(declared)
                .containsAll(reactorModules(Files.readString(repository.resolve("pom.xml"))));
    }

    @Test
    void public_poms_and_deterministic_auto_configuration_have_no_inference_surface()
            throws IOException {
        for (String artifactId : PUBLIC_ARTIFACTS) {
            Path pomPath =
                    artifactId.equals("stackmate-parent")
                            ? repository.resolve("pom.xml")
                            : repository.resolve(artifactId).resolve("pom.xml");
            String pom = Files.readString(pomPath).toLowerCase(Locale.ROOT);
            String inspected =
                    artifactId.equals("stackmate-parent")
                            ? between(pom, "<dependencymanagement>", "</dependencymanagement>")
                            : pom;
            for (String forbidden : FORBIDDEN_PUBLIC_POM_FRAGMENTS) {
                assertThat(inspected)
                        .as("%s must not expose %s", artifactId, forbidden)
                        .doesNotContain(forbidden);
            }
        }

        Path imports =
                repository.resolve(
                        "stackmate-spring-boot-autoconfigure/src/main/resources/"
                                + "META-INF/spring/"
                                + "org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        assertThat(Files.readAllLines(imports, StandardCharsets.UTF_8))
                .containsExactly(
                        "io.github.kalayciburak.stackmate.autoconfigure."
                                + "StackMateAutoConfiguration");

        Path source = repository.resolve("stackmate-spring-boot-autoconfigure/src/main/java");
        try (var files = Files.walk(source)) {
            assertThat(
                            files.filter(Files::isRegularFile)
                                    .map(source::relativize)
                                    .map(Path::toString))
                    .noneMatch(
                            name -> {
                                String normalized = name.replace('\\', '/');
                                return normalized.contains("/inference/")
                                        || normalized.endsWith("InferenceProperties.java")
                                        || normalized.endsWith("InferenceTriggerPolicy.java")
                                        || normalized.endsWith(
                                                "AsyncLocalInferenceCoordinator.java")
                                        || normalized.endsWith(
                                                "StackMateInferenceAutoConfiguration.java")
                                        || normalized.endsWith(
                                                "StackMateJvmInferenceAutoConfiguration.java");
                            });
        }

        Path starter = repository.resolve("stackmate-spring-boot-starter");
        String starterPom = Files.readString(starter.resolve("pom.xml"));
        assertThat(starterPom)
                .contains("<id>attach-placeholder-sources</id>")
                .contains("<id>attach-placeholder-javadoc</id>");
        assertThat(starter.resolve("src/release/sources/README.md")).isRegularFile();
        assertThat(starter.resolve("src/release/javadoc/README.md")).isRegularFile();
    }

    @Test
    void packaged_public_jars_contain_only_the_deterministic_surface() throws IOException {
        for (String artifactId : PUBLIC_ARTIFACTS) {
            if (artifactId.equals("stackmate-parent")) {
                continue;
            }
            Path jar = mainJar(repository.resolve(artifactId).resolve("target"), artifactId);
            try (JarFile archive = new JarFile(jar.toFile())) {
                List<String> entries =
                        archive.stream()
                                .map(entry -> entry.getName().toLowerCase(Locale.ROOT))
                                .toList();
                assertThat(entries)
                        .noneMatch(
                                name ->
                                        name.contains("/inference/")
                                                || name.contains("research/")
                                                || name.contains("dataset")
                                                || name.endsWith(".py")
                                                || name.endsWith(".gguf")
                                                || name.endsWith(".safetensors")
                                                || name.contains("adapter_model"));
                if (artifactId.equals("stackmate-spring-boot-autoconfigure")) {
                    String metadata =
                            readJarText(archive, "META-INF/spring-configuration-metadata.json");
                    assertThat(metadata)
                            .doesNotContain("stackmate.mode")
                            .doesNotContain("stackmate.inference");
                }
            }
        }
    }

    @Test
    void public_api_inventory_is_complete_classified_and_hash_bound() throws IOException {
        JsonNode inventory =
                MAPPER.readTree(repository.resolve("release/public-api-inventory.json").toFile());
        List<String> types = new ArrayList<>();
        for (JsonNode item : inventory.path("classifications")) {
            assertThat(item.path("classification").asText())
                    .isIn("STABLE_API", "EXTENSION_SPI", "CONFIGURATION_API");
            types.add(item.path("type").asText());
        }
        assertThat(types).doesNotHaveDuplicates();
        assertThat(types).hasSize(inventory.path("publicTypeCount").asInt());
        Path signature = repository.resolve(inventory.path("signatureFile").asText());
        assertThat(sha256(signature)).isEqualTo(inventory.path("signatureSha256").asText());
    }

    private static List<String> textValues(JsonNode values) {
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }

    private static Set<String> reactorModules(String pom) {
        Set<String> modules = new HashSet<>();
        Matcher matcher = REACTOR_MODULE.matcher(pom);
        while (matcher.find()) {
            modules.add(matcher.group(1));
        }
        return modules;
    }

    private static String between(String value, String opening, String closing) {
        int start = value.indexOf(opening);
        int end = value.indexOf(closing);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        return value.substring(start, end + closing.length());
    }

    private static Path mainJar(Path target, String artifactId) throws IOException {
        try (var files = Files.list(target)) {
            List<Path> matches =
                    files.filter(Files::isRegularFile)
                            .filter(
                                    path ->
                                            path.getFileName()
                                                    .toString()
                                                    .startsWith(artifactId + "-"))
                            .filter(path -> path.getFileName().toString().endsWith(".jar"))
                            .filter(path -> !path.getFileName().toString().endsWith("-sources.jar"))
                            .filter(path -> !path.getFileName().toString().endsWith("-javadoc.jar"))
                            .toList();
            assertThat(matches).as("main JAR for %s", artifactId).hasSize(1);
            return matches.get(0);
        }
    }

    private static String readJarText(JarFile archive, String name) throws IOException {
        var entry = archive.getJarEntry(name);
        assertThat(entry).as("JAR entry %s", name).isNotNull();
        try (var input = archive.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("release/release-artifacts.json"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("StackMate repository root was not found");
    }
}
