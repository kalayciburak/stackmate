package io.github.kalayciburak.stackmate.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PackagedModesIT {

    @Test
    void disabledModeCreatesNoStackMateDiagnosis() throws Exception {
        try (var sample = PackagedSample.start("e2e,disabled")) {
            int mark = sample.mark();
            PackagedSample.Response response = sample.get("/demo/null-pointer");
            Thread.sleep(200);

            assertThat(response.status()).isEqualTo(500);
            assertThat(response.body()).contains("\"status\":500");
            assertThat(sample.outputSince(mark)).doesNotContain("StackMate Quick Diagnosis");
        }
    }
}
