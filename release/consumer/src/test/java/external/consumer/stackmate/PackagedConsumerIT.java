package external.consumer.stackmate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PackagedConsumerIT {

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration LOG_TIMEOUT = Duration.ofSeconds(5);
    private static final String DIAGNOSIS_MARKER = "StackMate Quick Diagnosis";

    @Test
    void executableJarPreservesConsumerHttpBehaviorAndEmitsBoundedDiagnostics()
            throws Exception {
        Path jar = Path.of(System.getProperty("consumer.jar")).toAbsolutePath().normalize();
        assertThat(jar).isRegularFile();

        int port = randomLoopbackPort();
        Process process = start(jar, port);
        StringBuffer logs = new StringBuffer();
        Thread logReader = collectLogs(process, logs);
        HttpClient client =
                HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();

        try {
            awaitReady(client, process, port, logs);

            int diagnosisCount = occurrences(logs.toString(), DIAGNOSIS_MARKER);
            HttpResponse<String> success = get(client, port, "/success");
            assertThat(success.statusCode()).isEqualTo(200);
            assertThat(success.body()).isEqualTo("consumer-ok");
            assertThat(occurrences(logs.toString(), DIAGNOSIS_MARKER))
                    .isEqualTo(diagnosisCount);

            HttpResponse<String> failure = get(client, port, "/failure");
            assertThat(failure.statusCode()).isEqualTo(500);
            assertThat(failure.body())
                    .contains("\"status\":500")
                    .contains("\"error\":\"Internal Server Error\"")
                    .contains("\"path\":\"/failure\"")
                    .doesNotContain("StackMate");

            awaitLog(logs, DIAGNOSIS_MARKER, diagnosisCount + 1);
            String diagnosis = latestDiagnosis(logs.toString());
            assertThat(diagnosis)
                    .contains("Source: Deterministic rules")
                    .contains("consumer-unhandled")
                    .contains("password=[REDACTED]")
                    .doesNotContain("consumer-synthetic-secret");

            int duplicateCount = occurrences(logs.toString(), "StackMate duplicate");
            HttpResponse<String> repeated = get(client, port, "/failure");
            assertThat(repeated.statusCode()).isEqualTo(500);
            awaitLog(logs, "StackMate duplicate", duplicateCount + 1);

            int beforeHandled = occurrences(logs.toString(), DIAGNOSIS_MARKER);
            int duplicatesBeforeHandled = occurrences(logs.toString(), "StackMate duplicate");
            HttpResponse<String> handled = get(client, port, "/handled");
            assertThat(handled.statusCode()).isEqualTo(418);
            assertThat(handled.body()).isEqualTo("handled-by-consumer");
            Thread.sleep(200);
            assertThat(occurrences(logs.toString(), DIAGNOSIS_MARKER))
                    .isEqualTo(beforeHandled);
            assertThat(occurrences(logs.toString(), "StackMate duplicate"))
                    .isEqualTo(duplicatesBeforeHandled);
        } finally {
            stop(process);
            logReader.join(Duration.ofSeconds(2));
            assertThat(process.isAlive()).as("consumer process must not be orphaned").isFalse();
        }
    }

    private static Process start(Path jar, int port) throws IOException {
        Path java =
                Path.of(
                        System.getProperty("java.home"),
                        "bin",
                        System.getProperty("os.name").startsWith("Windows")
                                ? "java.exe"
                                : "java");
        return new ProcessBuilder(
                        java.toString(),
                        "-jar",
                        jar.toString(),
                        "--server.address=127.0.0.1",
                        "--server.port=" + port,
                        "--spring.main.banner-mode=off")
                .redirectErrorStream(true)
                .start();
    }

    private static Thread collectLogs(Process process, StringBuffer logs) {
        Thread reader =
                Thread.ofPlatform()
                        .name("consumer-log-reader")
                        .daemon(true)
                        .unstarted(
                                () -> {
                                    try (BufferedReader lines =
                                            new BufferedReader(
                                                    new InputStreamReader(
                                                            process.getInputStream(),
                                                            StandardCharsets.UTF_8))) {
                                        lines.lines()
                                                .forEach(
                                                        line ->
                                                                logs.append(line)
                                                                        .append(
                                                                                System.lineSeparator()));
                                    } catch (IOException failure) {
                                        logs.append("LOG_READER_FAILURE: ")
                                                .append(failure.getMessage())
                                                .append(System.lineSeparator());
                                    }
                                });
        reader.start();
        return reader;
    }

    private static void awaitReady(
            HttpClient client, Process process, int port, StringBuffer logs) throws Exception {
        Instant deadline = Instant.now().plus(STARTUP_TIMEOUT);
        Throwable lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                throw new AssertionError(
                        "consumer exited before readiness, exit="
                                + process.exitValue()
                                + System.lineSeparator()
                                + logs);
            }
            try {
                HttpResponse<String> response = get(client, port, "/success");
                if (response.statusCode() == 200 && response.body().equals("consumer-ok")) {
                    return;
                }
            } catch (IOException | InterruptedException failure) {
                lastFailure = failure;
                if (failure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw failure;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("consumer readiness timed out\n" + logs, lastFailure);
    }

    private static HttpResponse<String> get(HttpClient client, int port, String path)
            throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Accept", "application/json")
                        .GET()
                        .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static void awaitLog(StringBuffer logs, String marker, int expectedCount)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(LOG_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (occurrences(logs.toString(), marker) >= expectedCount) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError(
                "log marker did not reach count " + expectedCount + ": " + marker + "\n" + logs);
    }

    private static String latestDiagnosis(String logs) {
        int start = logs.lastIndexOf(DIAGNOSIS_MARKER);
        assertThat(start).isNotNegative();
        int end = logs.indexOf("Source: Deterministic rules", start);
        assertThat(end).isGreaterThan(start);
        return logs.substring(start, end + "Source: Deterministic rules".length());
    }

    private static int occurrences(String value, String marker) {
        int count = 0;
        int from = 0;
        while ((from = value.indexOf(marker, from)) >= 0) {
            count++;
            from += marker.length();
        }
        return count;
    }

    private static int randomLoopbackPort() throws IOException {
        try (ServerSocket socket =
                new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static void stop(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
    }
}
