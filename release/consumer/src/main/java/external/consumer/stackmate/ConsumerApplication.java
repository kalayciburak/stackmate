package external.consumer.stackmate;

import io.github.kalayciburak.stackmate.analysis.AnalysisContribution;
import io.github.kalayciburak.stackmate.analysis.DiagnosticAnalyzer;
import io.github.kalayciburak.stackmate.incident.IncidentSnapshot;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class ConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
    }

    @GetMapping("/success")
    String success() {
        return "consumer-ok";
    }

    @GetMapping("/failure")
    String failure() {
        throw new IllegalStateException(
                "consumer-unhandled password=consumer-synthetic-secret");
    }

    @GetMapping("/handled")
    String handled() {
        throw new ConsumerHandledException();
    }

    @Bean
    DiagnosticAnalyzer consumerDiagnosticAnalyzer() {
        return new DiagnosticAnalyzer() {
            @Override
            public String id() {
                return "consumer-extension";
            }

            @Override
            public int order() {
                return 5;
            }

            @Override
            public AnalysisContribution analyze(IncidentSnapshot incident) {
                return AnalysisContribution.empty(id());
            }
        };
    }

    static final class ConsumerHandledException extends RuntimeException {}

    @ControllerAdvice
    static final class ConsumerExceptionAdvice {

        @ExceptionHandler(ConsumerHandledException.class)
        ResponseEntity<String> handle(ConsumerHandledException failure) {
            return ResponseEntity.status(HttpStatus.I_AM_A_TEAPOT).body("handled-by-consumer");
        }
    }
}
