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
    private static final String DEFAULT_CHILD_MAIN = JlamaChildMain.class.getName();
    private static final String DEFAULT_CHILD_ENTRY =
            "io/github/kalayciburak/stackmate/inference/jvm/JlamaChildMain.class";
    private static final String PROPERTIES_LAUNCHER =
            "org.springframework.boot.loader.launch.PropertiesLauncher";
    private static final String PROPERTIES_LAUNCHER_ENTRY =
            "org/springframework/boot/loader/launch/PropertiesLauncher.class";

    private final Path javaHome;
    private final String osName;
    private final String classPath;
    private final String childMain;
    private final String childEntry;

    ChildCommandFactory(Path javaHome, String osName, String classPath) {
        this(javaHome, osName, classPath, DEFAULT_CHILD_MAIN, DEFAULT_CHILD_ENTRY);
    }

    ChildCommandFactory(
            Path javaHome, String osName, String classPath, String childMain, String childEntry) {
        this.javaHome = javaHome.toAbsolutePath().normalize();
        this.osName = osName;
        this.classPath = classPath;
        this.childMain = requireClassName(childMain, "childMain");
        this.childEntry = requireEntry(childEntry);
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
        if (singleJar != null && !containsEntry(singleJar, childEntry)) {
            if (!containsEntry(singleJar, PROPERTIES_LAUNCHER_ENTRY)) {
                throw new UnsupportedPackagingException(
                        "single-JAR runtime has neither StackMate child classes nor Spring Boot PropertiesLauncher");
            }
            command.add("-Dloader.main=" + childMain);
            command.add("-cp");
            command.add(singleJar.toString());
            command.add(PROPERTIES_LAUNCHER);
        } else {
            command.add("-cp");
            command.add(classPath);
            command.add(childMain);
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

    private static String requireClassName(String value, String name) {
        if (value == null
                || !value.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+")) {
            throw new IllegalArgumentException(name + " must be a fully qualified class name");
        }
        return value;
    }

    private static String requireEntry(String value) {
        if (value == null
                || value.startsWith("/")
                || value.contains("..")
                || !value.matches("[A-Za-z0-9_$/.-]+\\.class")) {
            throw new IllegalArgumentException("childEntry must be a safe class entry");
        }
        return value;
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
