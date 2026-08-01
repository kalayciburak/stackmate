package io.github.kalayciburak.stackmate.inference.jvm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class ManagedChild implements AutoCloseable {
    private static final int MAXIMUM_LAUNCHES = 3;
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(2);

    private final Path modelDirectory;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final ChildCommandFactory commandFactory;
    private final ChildProcessLauncher launcher;
    private final ExecutorService ioExecutor =
            Executors.newSingleThreadExecutor(
                    Thread.ofVirtual().name("stackmate-inference-io").factory());

    private Process process;
    private DataInputStream input;
    private DataOutputStream output;
    private int launches;
    private boolean closed;
    private String lastFailure = "";

    ManagedChild(
            Path modelDirectory,
            Duration timeout,
            ObjectMapper mapper,
            ChildCommandFactory commandFactory,
            ChildProcessLauncher launcher) {
        this.modelDirectory = modelDirectory;
        this.timeout = timeout;
        this.mapper = mapper;
        this.commandFactory = commandFactory;
        this.launcher = launcher;
    }

    synchronized JsonNode generate(JsonNode request)
            throws IOException, InterruptedException, TimeoutException {
        return exchange("generate", request, timeout);
    }

    synchronized JsonNode repair(JsonNode request)
            throws IOException, InterruptedException, TimeoutException {
        return exchange("repair", request, timeout);
    }

    synchronized boolean mayStart() {
        return !closed && (isRunning() || launches < MAXIMUM_LAUNCHES);
    }

    synchronized String lastFailure() {
        return lastFailure;
    }

    synchronized List<String> command() {
        return commandFactory.command(modelDirectory);
    }

    private JsonNode exchange(String operation, JsonNode payload, Duration operationTimeout)
            throws IOException, InterruptedException, TimeoutException {
        ensureStarted();
        String requestId = UUID.randomUUID().toString();
        Protocol.Request request =
                new Protocol.Request(Protocol.VERSION, operation, requestId, payload);
        Process expected = process;
        Future<Protocol.Response> future =
                ioExecutor.submit(
                        () -> {
                            Protocol.writeFrame(
                                    output, request, mapper, Protocol.MAXIMUM_REQUEST_BYTES);
                            return Protocol.readFrame(
                                    input,
                                    Protocol.Response.class,
                                    mapper,
                                    Protocol.MAXIMUM_RESPONSE_BYTES);
                        });
        try {
            Protocol.Response response =
                    future.get(operationTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.version() != Protocol.VERSION || !requestId.equals(response.requestId())) {
                throw new Protocol.ProtocolException("mismatched protocol response");
            }
            if (!response.successful()) {
                throw new ChildFailureException(
                        response.error() == null || response.error().isBlank()
                                ? "child rejected inference"
                                : response.error());
            }
            if (response.payload() == null) {
                throw new Protocol.ProtocolException("child returned no payload");
            }
            lastFailure = "";
            return response.payload();
        } catch (java.util.concurrent.TimeoutException exception) {
            future.cancel(true);
            failProcess(expected, "local inference timed out");
            throw new TimeoutException("local inference timed out");
        } catch (InterruptedException exception) {
            future.cancel(true);
            failProcess(expected, "local inference was interrupted");
            Thread.currentThread().interrupt();
            throw exception;
        } catch (ExecutionException exception) {
            failProcess(expected, Protocol.boundedError(exception.getCause()));
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("child protocol failed", cause);
        } catch (ChildFailureException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            failProcess(expected, Protocol.boundedError(exception));
            throw exception;
        }
    }

    private void ensureStarted() throws IOException {
        if (closed) {
            throw new IOException("local inference engine is closed");
        }
        if (isRunning()) {
            return;
        }
        clearProcess();
        if (launches >= MAXIMUM_LAUNCHES) {
            throw new IOException("local inference child restart limit reached");
        }
        List<String> command = commandFactory.command(modelDirectory);
        process = launcher.launch(command);
        launches++;
        if (!process.isAlive()) {
            failProcess(process, "local inference child exited during startup");
            throw new IOException("local inference child exited during startup");
        }
        input = new DataInputStream(new BufferedInputStream(process.getInputStream()));
        output = new DataOutputStream(new BufferedOutputStream(process.getOutputStream()));
    }

    private boolean isRunning() {
        return process != null && process.isAlive();
    }

    private void failProcess(Process expected, String detail) {
        lastFailure = detail;
        if (process == expected) {
            destroyProcess();
        }
    }

    private void destroyProcess() {
        Process current = process;
        clearProcess();
        if (current == null) {
            return;
        }
        current.destroy();
        try {
            if (!current.waitFor(200, TimeUnit.MILLISECONDS)) {
                current.destroyForcibly();
            }
        } catch (InterruptedException exception) {
            current.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private void clearProcess() {
        process = null;
        input = null;
        output = null;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        if (isRunning()) {
            try {
                exchange("shutdown", mapper.createObjectNode(), CLOSE_TIMEOUT);
                process.waitFor(CLOSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (IOException | TimeoutException | RuntimeException ignored) {
                // Closing is best effort and always falls through to forced process cleanup.
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        closed = true;
        destroyProcess();
        ioExecutor.shutdownNow();
    }

    static final class ChildFailureException extends IOException {
        private static final long serialVersionUID = 1L;

        ChildFailureException(String message) {
            super(message);
        }
    }
}
