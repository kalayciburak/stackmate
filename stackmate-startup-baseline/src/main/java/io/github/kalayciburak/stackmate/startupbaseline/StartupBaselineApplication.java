package io.github.kalayciburak.stackmate.startupbaseline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Dependency-free control application for process-level startup measurements. */
@SpringBootApplication
public class StartupBaselineApplication {

    public static void main(String[] arguments) {
        SpringApplication.run(StartupBaselineApplication.class, arguments);
    }
}
