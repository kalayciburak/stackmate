package io.github.kalayciburak.stackmate.inference.jvm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

final class ChildCommandFactory {
    private static final String MAXIMUM_HEAP = "-Xmx1536m";
    private static final String CHILD_MAIN = JlamaChildMain.class.getName();
    private static final String CHILD_ENTRY =
            "io/github/kalayciburak/stackmate/inference/jvm/JlamaChildMain.class";
    private static final String PROPERTIES_LAUNCHER =
            "org.springframework.boot.loader.launch.PropertiesLauncher";
    private static final String PROPERTIES_LAUNCHER_ENTRY =
            "org/springframework/boot/loader/launch/PropertiesLauncher.class";

    private final Path javaHome;
    private final String osName;
    private final String classPath;

    ChildCommandFactory(Path javaHome, String osName, String classPath) {
        this.javaHome = javaHome.toAbsolutePath().normalize();
        this.osName = osName;
        this.classPath = classPath;
    }

    static ChildCommandFactory currentRuntime() {
        return new ChildCommandFactory(
                Path.of(System.getProperty("java.home")),
                System.getProperty("os.name", ""),
                System.getProperty("java.class.path", ""));
    }

    List<String> command(Path modelDirectory) {
        if (classPath.isBlank()) {
            throw new UnsupportedPackagingException("current Java classpath is empty");
        }
        Path javaExecutable =
                javaHome.resolve("bin").resolve(isWindows() ? "java.exe" : "java").normalize();
        List<String> command = new ArrayList<>();
        command.add(javaExecutable.toString());
        command.add(MAXIMUM_HEAP);
        command.add("--enable-preview");
        command.add("--add-modules=jdk.incubator.vector");

        Path singleJar = singleJarClassPath();
        if (singleJar != null && !containsEntry(singleJar, CHILD_ENTRY)) {
            if (!containsEntry(singleJar, PROPERTIES_LAUNCHER_ENTRY)) {
                throw new UnsupportedPackagingException(
                        "single-JAR runtime has neither StackMate child classes nor Spring Boot PropertiesLauncher");
            }
            command.add("-Dloader.main=" + CHILD_MAIN);
            command.add("-cp");
            command.add(singleJar.toString());
            command.add(PROPERTIES_LAUNCHER);
        } else {
            command.add("-cp");
            command.add(classPath);
            command.add(CHILD_MAIN);
        }
        command.add(modelDirectory.toString());
        return List.copyOf(command);
    }

    private boolean isWindows() {
        return osName.toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private Path singleJarClassPath() {
        char classPathSeparator = isWindows() ? ';' : ':';
        if (classPath.indexOf(classPathSeparator) >= 0) {
            return null;
        }
        Path path = Path.of(classPath).toAbsolutePath().normalize();
        return classPath.toLowerCase(Locale.ROOT).endsWith(".jar") && Files.isRegularFile(path)
                ? path
                : null;
    }

    private static boolean containsEntry(Path jar, String entry) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return zip.getEntry(entry) != null;
        } catch (IOException exception) {
            throw new UnsupportedPackagingException("cannot inspect runtime JAR", exception);
        }
    }

    static final class UnsupportedPackagingException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        UnsupportedPackagingException(String message) {
            super(message);
        }

        UnsupportedPackagingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
