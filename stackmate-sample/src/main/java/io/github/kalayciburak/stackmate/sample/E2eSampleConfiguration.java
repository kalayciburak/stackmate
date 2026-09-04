package io.github.kalayciburak.stackmate.sample;

import java.util.Map;
import java.util.concurrent.locks.LockSupport;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Configuration(proxyBeanMethods = false)
@Profile("e2e")
class E2eSampleConfiguration {

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
    }
}
