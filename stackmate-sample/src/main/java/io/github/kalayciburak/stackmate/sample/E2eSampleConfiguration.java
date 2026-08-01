package io.github.kalayciburak.stackmate.sample;

import io.github.kalayciburak.stackmate.configuration.StackMateProperties;
import io.github.kalayciburak.stackmate.inference.LocalInferenceEngine;
import io.github.kalayciburak.stackmate.inference.jvm.SampleManagedFakeInferenceEngine;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Configuration(proxyBeanMethods = false)
@Profile("e2e")
class E2eSampleConfiguration {

    @Bean(destroyMethod = "close")
    @Profile("e2e-fake-child")
    LocalInferenceEngine sampleManagedFakeInferenceEngine(StackMateProperties properties) {
        return new SampleManagedFakeInferenceEngine(properties.getInference().getTimeout());
    }

    @RestController
    @RequestMapping("/demo/e2e")
    static class E2eController {
        private final ConfigurableApplicationContext context;

        E2eController(ConfigurableApplicationContext context) {
            this.context = context;
        }

        @GetMapping("/ready")
        Map<String, String> ready() {
            return Map.of("status", "ready");
        }

        @PostMapping("/shutdown")
        Map<String, String> shutdown() {
            Thread.ofPlatform()
                    .name("stackmate-e2e-shutdown")
                    .start(
                            () -> {
                                LockSupport.parkNanos(100_000_000L);
                                SpringApplication.exit(context);
                                context.close();
                            });
            return Map.of("status", "stopping");
        }

        @GetMapping("/fake/valid")
        void fakeValid() {
            throw failure("valid");
        }

        @GetMapping("/fake/invalid-json")
        void fakeInvalidJson() {
            throw failure("invalid-json");
        }

        @GetMapping("/fake/unknown-evidence")
        void fakeUnknownEvidence() {
            throw failure("unknown-evidence");
        }

        @GetMapping("/fake/fabricated-source")
        void fakeFabricatedSource() {
            throw failure("fabricated-source");
        }

        @GetMapping("/fake/timeout")
        void fakeTimeout() {
            throw failure("timeout");
        }

        @GetMapping("/fake/exit")
        void fakeExit() {
            throw failure("exit");
        }

        @GetMapping("/fake/oversized")
        void fakeOversized() {
            throw failure("oversized");
        }

        @GetMapping("/fake/invalid-utf8")
        void fakeInvalidUtf8() {
            throw failure("invalid-utf8");
        }

        @GetMapping("/fake/queue/{value}")
        void fakeQueue(@PathVariable String value) {
            throw failure("queue-" + value);
        }

        private IllegalStateException failure(String scenario) {
            return new IllegalStateException("Packaged fake inference scenario: " + scenario);
        }
    }
}
