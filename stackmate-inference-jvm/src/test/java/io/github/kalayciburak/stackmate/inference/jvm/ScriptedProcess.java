package io.github.kalayciburak.stackmate.inference.jvm;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class ScriptedProcess extends Process {
    interface Script {
        void run(InputStream input, OutputStream output) throws Exception;
    }

    private final PipedOutputStream parentOutput;
    private final PipedInputStream parentInput;
    private final PipedInputStream childInput;
    private final PipedOutputStream childOutput;
    private final AtomicBoolean alive = new AtomicBoolean(true);
    private final CountDownLatch exited = new CountDownLatch(1);
    private final Thread worker;

    ScriptedProcess(Script script) throws IOException {
        childInput = new PipedInputStream(65_536);
        parentOutput = new PipedOutputStream(childInput);
        parentInput = new PipedInputStream(65_536);
        childOutput = new PipedOutputStream(parentInput);
        worker =
                Thread.ofVirtual()
                        .name("scripted-inference-child")
                        .start(
                                () -> {
                                    try {
                                        script.run(childInput, childOutput);
                                    } catch (InterruptedException exception) {
                                        Thread.currentThread().interrupt();
                                    } catch (Exception ignored) {
                                        // The parent observes a closed protocol stream as process
                                        // failure.
                                    } finally {
                                        alive.set(false);
                                        closeQuietly(childOutput);
                                        closeQuietly(childInput);
                                        exited.countDown();
                                    }
                                });
    }

    @Override
    public OutputStream getOutputStream() {
        return parentOutput;
    }

    @Override
    public InputStream getInputStream() {
        return parentInput;
    }

    @Override
    public InputStream getErrorStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public int waitFor() throws InterruptedException {
        exited.await();
        return 0;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
        return exited.await(timeout, unit);
    }

    @Override
    public int exitValue() {
        if (alive.get()) {
            throw new IllegalThreadStateException("process is still alive");
        }
        return 0;
    }

    @Override
    public void destroy() {
        if (alive.getAndSet(false)) {
            worker.interrupt();
            closeQuietly(parentOutput);
            closeQuietly(parentInput);
            closeQuietly(childOutput);
            closeQuietly(childInput);
            exited.countDown();
        }
    }

    @Override
    public Process destroyForcibly() {
        destroy();
        return this;
    }

    @Override
    public boolean isAlive() {
        return alive.get();
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Test process cleanup is intentionally idempotent.
        }
    }
}
