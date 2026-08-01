package io.github.kalayciburak.stackmate.sample.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.kalayciburak.stackmate.sample.demo.database.SampleDatabaseException;
import io.github.kalayciburak.stackmate.sample.demo.security.AccessDeniedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@WebMvcTest(DemoFailureController.class)
@Import(DemoFailureControllerTest.ControlledFailureHttpAdapter.class)
class DemoFailureControllerTest {

    private final MockMvc mockMvc;

    @Autowired
    DemoFailureControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/demo/null-pointer",
                "/demo/invalid-number",
                "/demo/missing-configuration",
                "/demo/database/duplicate",
                "/demo/database/not-null",
                "/demo/database/foreign-key",
                "/demo/database/missing-column",
                "/demo/timeout",
                "/demo/access-denied",
                "/demo/redaction",
                "/demo/prompt-injection"
            })
    void controlledFailuresReturnInternalServerErrorWithoutExposingTheirMessage(String path)
            throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(""));
    }

    @Test
    void repeatedFailureReturnsTheSameHttpResultAcrossRequests() throws Exception {
        mockMvc.perform(get("/demo/repeated"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(""));
        mockMvc.perform(get("/demo/repeated"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(""));
    }

    @Test
    void validRequestPassesValidation() throws Exception {
        mockMvc.perform(
                        post("/demo/validation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"sample\",\"quantity\":1}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void invalidRequestIsRejectedByBeanValidation() throws Exception {
        mockMvc.perform(
                        post("/demo/validation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonIsRejectedBeforeControllerInvocation() throws Exception {
        mockMvc.perform(
                        post("/demo/malformed-json")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validJsonReachesMalformedJsonDemoEndpoint() throws Exception {
        mockMvc.perform(
                        post("/demo/malformed-json")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"message\":\"valid\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @RestControllerAdvice
    static class ControlledFailureHttpAdapter {

        @ExceptionHandler({
            NullPointerException.class,
            IllegalArgumentException.class,
            IllegalStateException.class,
            SampleDatabaseException.class,
            AccessDeniedException.class
        })
        ResponseEntity<Void> internalServerError() {
            return ResponseEntity.internalServerError().build();
        }
    }
}
