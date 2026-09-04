package io.github.kalayciburak.stackmate.inference;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LazyLocalInferenceEngineTest {

    @Test
    void doesNotHoldInitializationMonitorWhileDelegateInferenceRuns() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var lazy = new LazyLocalInferenceEngine(() -> new BlockingEngine(entered, release));
        Thread inference =
                Thread.ofPlatform()
                        .start(
                                () -> {
                                    try {
                                        lazy.infer(null);
                                    } catch (InterruptedException exception) {
                                        Thread.currentThread().interrupt();
                                    }
                                });
        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

        AtomicReference<InferenceAvailability> availability = new AtomicReference<>();
        Thread observer = Thread.ofPlatform().start(() -> availability.set(lazy.availability()));
        observer.join(1_000);

        assertThat(observer.isAlive()).isFalse();
        assertThat(availability.get()).isEqualTo(InferenceAvailability.available());
        release.countDown();
        inference.join(1_000);
        lazy.close();
    }

    private static final class BlockingEngine implements LocalInferenceEngine {
        private final CountDownLatch entered;
        private final CountDownLatch release;

        private BlockingEngine(CountDownLatch entered, CountDownLatch release) {
            this.entered = entered;
            this.release = release;
        }

        @Override
        public InferenceAvailability availability() {
            return InferenceAvailability.available();
        }

        @Override
        public ModelDescriptor model() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InferenceResult infer(InferenceRequest request) throws InterruptedException {
            entered.countDown();
            release.await();
            return new InferenceResult.Failed(
                    new InferenceFailure(
                            InferenceFailure.Kind.UNAVAILABLE, "test completed", false));
        }

        @Override
        public void close() {}
    }
}
