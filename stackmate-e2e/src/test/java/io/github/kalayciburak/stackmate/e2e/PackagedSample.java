package io.github.kalayciburak.stackmate.e2e;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

final class PackagedSample implements AutoCloseable {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(25);
    private static final Duration OUTPUT_TIMEOUT = Duration.ofSeconds(8);

    private final Process process;
    private final URI baseUri;
    private final OutputCollector stdout;
    private final OutputCollector stderr;
    private final Set<Long> observedDescendants = ConcurrentHashMap.newKeySet();
    private boolean closed;

    private PackagedSample(
            Process process, URI baseUri, OutputCollector stdout, OutputCollector stderr) {
        this.process = process;
        this.baseUri = baseUri;
        this.stdout = stdout;
        this.stderr = stderr;
    }

    static PackagedSample start(String profiles, String... arguments) throws Exception {
        Path jar =
                Path.of(
                                System.getProperty(
                                        "stackmate.e2e.sample-jar",
                                        "../stackmate-sample/target/stackmate-sample-0.1.0-rc.1.jar"))
                        .toAbsolutePath()
                        .normalize();
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("packaged sample JAR is missing: " + jar);
        }
        int port = availablePort();
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-jar");
        command.add(jar.toString());
        command.add("--server.port=" + port);
        command.add("--spring.profiles.active=" + profiles);
        command.add("--spring.main.banner-mode=off");
        command.add("--logging.pattern.console=%msg%n");
        command.addAll(List.of(arguments));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(jar.getParent().getParent().getParent().toFile());
        builder.environment().remove("STACKMATE_MODEL_PATH");
        Process process = builder.start();
        OutputCollector stdout = new OutputCollector("stdout", process.getInputStream());
        OutputCollector stderr = new OutputCollector("stderr", process.getErrorStream());
        var sample =
                new PackagedSample(process, URI.create("http://127.0.0.1:" + port), stdout, stderr);
        try {
            sample.awaitReady();
            return sample;
        } catch (Throwable failure) {
            sample.forceCleanup();
            throw failure;
        }
    }

    int mark() {
        return output().length();
    }

    Response get(String path) throws Exception {
        return request("GET", path, "", "text/plain");
    }

    Response postJson(String path, String json) throws Exception {
        return request("POST", path, json, "application/json");
    }

    Response request(String method, String path, String body, String contentType) throws Exception {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(baseUri.resolve(path)).timeout(HTTP_TIMEOUT);
        if ("GET".equals(method)) {
            builder.GET();
        } else {
            builder.header("Content-Type", contentType)
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        long started = System.nanoTime();
        HttpResponse<String> response =
                HttpClient.newHttpClient()
                        .send(builder.build(), HttpResponse.BodyHandlers.ofString());
        observeDescendants();
        return new Response(
                response.statusCode(),
                response.body(),
                Duration.ofNanos(System.nanoTime() - started));
    }

    String output() {
        return stdout.content() + System.lineSeparator() + stderr.content();
    }

    String outputSince(int mark) {
        String current = output();
        return current.substring(Math.min(mark, current.length()));
    }

    void awaitOutputSince(int mark, String expected) throws InterruptedException {
        await(
                () -> outputSince(mark).contains(expected),
                OUTPUT_TIMEOUT,
                "timed out waiting for new output: " + expected + "\n" + outputSince(mark));
    }

    void awaitAnyOutputSince(int mark, Duration timeout, String... expected)
            throws InterruptedException {
        await(
                () ->
                        Arrays.stream(expected)
                                .anyMatch(candidate -> outputSince(mark).contains(candidate)),
                timeout,
                "timed out waiting for one of "
                        + Arrays.toString(expected)
                        + "\n"
                        + outputSince(mark));
    }

    void awaitManagedChild() throws InterruptedException {
        await(
                () -> {
                    observeDescendants();
                    return !observedDescendants.isEmpty();
                },
                Duration.ofSeconds(5),
                "managed child process did not start");
    }

    void observeDescendants() {
        process.descendants().map(ProcessHandle::pid).forEach(observedDescendants::add);
    }

    private void awaitReady() throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        Throwable lastFailure = null;
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException(
                        "sample exited before readiness (exit "
                                + process.exitValue()
                                + ")\n"
                                + output());
            }
            try {
                Response response = get("/demo/e2e/ready");
                if (response.status() == 200 && response.body().contains("ready")) {
                    return;
                }
            } catch (IOException | InterruptedException failure) {
                if (failure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw failure;
                }
                lastFailure = failure;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("sample did not become ready\n" + output(), lastFailure);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        observeDescendants();
        boolean graceful = false;
        try {
            request("POST", "/demo/e2e/shutdown", "", "text/plain");
            graceful = waitForExit(12);
        } catch (Exception ignored) {
            graceful = waitForExit(12);
        }
        if (!graceful) {
            forceCleanup();
            throw new AssertionError(
                    "packaged sample did not stop through its shutdown endpoint\n" + output());
        }
        try {
            stdout.join();
            stderr.join();
            assertNoOrphanDescendants();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            forceCleanup();
            throw new AssertionError(
                    "interrupted while checking packaged process cleanup", interrupted);
        }
    }

    private void assertNoOrphanDescendants() throws InterruptedException {
        await(
                () ->
                        observedDescendants.stream()
                                .map(ProcessHandle::of)
                                .flatMap(java.util.Optional::stream)
                                .noneMatch(ProcessHandle::isAlive),
                Duration.ofSeconds(3),
                "managed child process remained alive after sample shutdown: "
                        + observedDescendants);
    }

    private void forceCleanup() {
        observeDescendants();
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
        observedDescendants.stream()
                .map(ProcessHandle::of)
                .flatMap(java.util.Optional::stream)
                .filter(ProcessHandle::isAlive)
                .forEach(ProcessHandle::destroyForcibly);
    }

    private boolean waitForExit(long seconds) {
        try {
            return process.waitFor(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void await(BooleanSupplier condition, Duration timeout, String failure)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        if (!condition.getAsBoolean()) {
            throw new AssertionError(failure);
        }
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(false);
            return socket.getLocalPort();
        }
    }

    private static String javaExecutable() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
        return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java")
                .toString();
    }

    record Response(int status, String body, Duration elapsed) {}

    private static final class OutputCollector {
        private final StringBuilder content = new StringBuilder();
        private final Thread reader;

        private OutputCollector(String name, InputStream input) {
            reader =
                    Thread.ofPlatform()
                            .daemon()
                            .name("stackmate-e2e-" + name)
                            .start(() -> read(input));
        }

        private void read(InputStream input) {
            char[] buffer = new char[2_048];
            try (var source = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                int count;
                while ((count = source.read(buffer)) >= 0) {
                    if (count > 0) {
                        synchronized (content) {
                            content.append(buffer, 0, count);
                        }
                    }
                }
            } catch (IOException ignored) {
                // Closing the process pipe is the expected reader termination signal.
            }
        }

        private String content() {
            synchronized (content) {
                return content.toString();
            }
        }

        private void join() throws InterruptedException {
            reader.join(2_000);
        }
    }
}
