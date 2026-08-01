package io.github.kalayciburak.stackmate.inference.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JlamaSessionCleanerTest {

    @Test
    void detectsThePinnedJlamaSessionCacheContract() {
        assertThat(JlamaSessionCleaner.supported()).isTrue();
    }
}
