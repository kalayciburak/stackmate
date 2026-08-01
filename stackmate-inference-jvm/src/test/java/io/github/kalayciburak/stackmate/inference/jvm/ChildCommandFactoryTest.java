package io.github.kalayciburak.stackmate.inference.jvm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChildCommandFactoryTest {
    @TempDir Path temporaryDirectory;

    @Test
    void constructsDiscreteWindowsAndUnixArguments() {
        Path model = temporaryDirectory.resolve("model with spaces").toAbsolutePath();

        List<String> windows =
                new ChildCommandFactory(Path.of("C:/jdk-21"), "Windows 11", "classes;libs/*")
                        .command(model);
        List<String> unix =
                new ChildCommandFactory(Path.of("/opt/jdk-21"), "Linux", "classes:libs/*")
                        .command(model);

        assertThat(windows.getFirst()).endsWith("bin\\java.exe");
        assertThat(Path.of(unix.getFirst()).getFileName().toString()).isEqualTo("java");
        assertThat(windows)
                .containsSubsequence(
                        "-Xmx1536m",
                        "--enable-preview",
                        "--add-modules=jdk.incubator.vector",
                        "-cp",
                        "classes;libs/*",
                        JlamaChildMain.class.getName(),
                        model.toString());
        assertThat(windows.getLast()).isEqualTo(model.toString());
    }

    @Test
    void usesPropertiesLauncherOnlyForVerifiedBootJar() throws Exception {
        Path bootJar = jarWith("org/springframework/boot/loader/launch/PropertiesLauncher.class");

        List<String> command =
                new ChildCommandFactory(Path.of("C:/jdk"), "Windows 11", bootJar.toString())
                        .command(temporaryDirectory.resolve("model"));

        assertThat(command)
                .contains("-Dloader.main=" + JlamaChildMain.class.getName())
                .contains("org.springframework.boot.loader.launch.PropertiesLauncher");
    }

    @Test
    void rejectsUnverifiedSingleJarPackaging() throws Exception {
        Path unknownJar = jarWith("example/Application.class");

        assertThatThrownBy(
                        () ->
                                new ChildCommandFactory(
                                                Path.of("C:/jdk"),
                                                "Windows 11",
                                                unknownJar.toString())
                                        .command(temporaryDirectory.resolve("model")))
                .isInstanceOf(ChildCommandFactory.UnsupportedPackagingException.class)
                .hasMessageContaining("neither StackMate child classes nor Spring Boot");
    }

    @Test
    void supportsAnExplicitPackagedTestChildWithoutShellInterpolation() throws Exception {
        Path bootJar = jarWith("org/springframework/boot/loader/launch/PropertiesLauncher.class");
        String fakeMain = "example.StackMateFakeChild";

        List<String> command =
                new ChildCommandFactory(
                                Path.of("C:/jdk"),
                                "Windows 11",
                                bootJar.toString(),
                                fakeMain,
                                "example/StackMateFakeChild.class")
                        .command(temporaryDirectory.resolve("model with spaces"));

        assertThat(command)
                .contains("-Dloader.main=" + fakeMain)
                .doesNotContain(JlamaChildMain.class.getName());
        assertThat(command.getLast()).endsWith("model with spaces");
    }

    private Path jarWith(String entryName) throws Exception {
        Path jar = temporaryDirectory.resolve(entryName.hashCode() + ".jar");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(1);
            output.closeEntry();
        }
        return jar;
    }
}
