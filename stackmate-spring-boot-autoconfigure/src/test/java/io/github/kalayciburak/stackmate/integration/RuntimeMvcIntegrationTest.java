package io.github.kalayciburak.stackmate.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.kalayciburak.stackmate.capture.CapturedServletException;
import io.github.kalayciburak.stackmate.capture.ServletExceptionCaptureHandler;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@SpringBootTest(
        classes = RuntimeMvcIntegrationTest.TestApplication.class,
        properties = "stackmate.application-packages=io.github.kalayciburak.stackmate.integration")
@AutoConfigureMockMvc
class RuntimeMvcIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private RecordingCaptureHandler captureHandler;

    @BeforeEach
    void clearCapturedIncidents() {
        captureHandler.clear();
    }

    @Test
    void should_leave_normal_request_untouched() throws Exception {
        mockMvc.perform(get("/test/normal"))
                .andExpect(status().isOk())
                .andExpect(content().string("normal"));

        assertThat(captureHandler.captured()).isEmpty();
    }

    @Test
    void should_preserve_user_exception_handler_status_and_body_without_capture() throws Exception {
        mockMvc.perform(get("/test/handled"))
                .andExpect(status().isConflict())
                .andExpect(content().string("handled-by-application"));

        assertThat(captureHandler.captured()).isEmpty();
    }

    @Test
    void should_capture_unhandled_exception_and_rethrow_same_object() {
        assertThatThrownBy(() -> mockMvc.perform(get("/test/unhandled")))
                .satisfies(
                        failure -> {
                            assertThat(captureHandler.captured()).hasSize(1);
                            Throwable captured = captureHandler.captured().getFirst().exception();
                            assertThat(captured).isSameAs(failure);
                            assertThat(failure).hasRootCause(TestController.UNHANDLED);
                        });
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({TestController.class, TestAdvice.class, RecordingCaptureHandler.class})
    static class TestApplication {}

    @RestController
    static class TestController {

        private static final IllegalStateException UNHANDLED =
                new IllegalStateException("controlled unhandled failure");

        @GetMapping("/test/normal")
        String normal() {
            return "normal";
        }

        @GetMapping("/test/handled")
        String handled() {
            throw new HandledException();
        }

        @GetMapping("/test/unhandled")
        String unhandled() {
            throw UNHANDLED;
        }
    }

    @RestControllerAdvice
    static class TestAdvice {

        @ExceptionHandler(HandledException.class)
        ResponseEntity<String> handle() {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("handled-by-application");
        }
    }

    static final class HandledException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class RecordingCaptureHandler implements ServletExceptionCaptureHandler {
        private final CopyOnWriteArrayList<CapturedServletException> incidents =
                new CopyOnWriteArrayList<>();

        @Override
        public void capture(CapturedServletException capturedException) {
            incidents.add(capturedException);
        }

        CopyOnWriteArrayList<CapturedServletException> captured() {
            return incidents;
        }

        void clear() {
            incidents.clear();
        }
    }
}
