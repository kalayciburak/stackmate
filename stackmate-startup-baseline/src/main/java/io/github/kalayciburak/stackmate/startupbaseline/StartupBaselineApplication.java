package io.github.kalayciburak.stackmate.startupbaseline;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/** Dependency-free control application for process-level startup measurements. */
@SpringBootApplication
public class StartupBaselineApplication {

    public static void main(String[] arguments) {
        SpringApplication.run(StartupBaselineApplication.class, arguments);
    }

    @Bean
    ErrorAttributes deterministicErrorAttributes() {
        return new DefaultErrorAttributes() {
            @Override
            public Map<String, Object> getErrorAttributes(
                    WebRequest webRequest, ErrorAttributeOptions options) {
                Map<String, Object> attributes = super.getErrorAttributes(webRequest, options);
                attributes.remove("timestamp");
                return attributes;
            }
        };
    }

    @RestController
    static class BenchmarkController {

        @GetMapping(value = "/benchmark/success", produces = "text/plain")
        String success() {
            return "stackmate-benchmark-ok";
        }

        @GetMapping(value = "/benchmark/failure", produces = "text/plain")
        String failure() {
            throw new IllegalStateException("deterministic benchmark failure");
        }
    }
}
