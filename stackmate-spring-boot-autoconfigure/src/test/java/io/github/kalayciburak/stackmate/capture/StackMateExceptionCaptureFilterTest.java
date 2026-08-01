package io.github.kalayciburak.stackmate.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.kalayciburak.stackmate.configuration.StackMateProperties;
import io.github.kalayciburak.stackmate.context.ServletContextExtractor;
import io.github.kalayciburak.stackmate.privacy.DefaultSanitizer;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class StackMateExceptionCaptureFilterTest {

    @ParameterizedTest
    @MethodSource("escapingFailures")
    void capturesAndRethrowsTheExactSameEscapingFailure(Throwable failure) {
        AtomicReference<CapturedServletException> captured = new AtomicReference<>();
        StackMateExceptionCaptureFilter filter = filter(captured::set);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/failure");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, throwingChain(failure)))
                .isSameAs(failure);

        assertThat(captured.get().exception()).isSameAs(failure);
        assertThat(captured.get().context().request().path()).isEqualTo("/failure");
    }

    @Test
    void neverMutatesStatusOrBody() throws Exception {
        RuntimeException failure = new RuntimeException("boom");
        StackMateExceptionCaptureFilter filter = filter(ignored -> {});
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/failure");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(418);
        response.getWriter().write("application-owned");

        assertThatThrownBy(() -> filter.doFilter(request, response, throwingChain(failure)))
                .isSameAs(failure);

        assertThat(response.getStatus()).isEqualTo(418);
        assertThat(response.getContentAsString()).isEqualTo("application-owned");
        assertThat(response.isCommitted()).isFalse();
    }

    @Test
    void leavesNormalAndApplicationHandledFlowsUntouched() throws Exception {
        AtomicInteger captures = new AtomicInteger();
        StackMateExceptionCaptureFilter filter = filter(ignored -> captures.incrementAndGet());
        MockHttpServletRequest normalRequest = new MockHttpServletRequest("GET", "/normal");
        MockHttpServletResponse normalResponse = new MockHttpServletResponse();

        filter.doFilter(
                normalRequest,
                normalResponse,
                (request, response) -> {
                    ((HttpServletResponse) response).setStatus(204);
                });

        MockHttpServletRequest handledRequest = new MockHttpServletRequest("GET", "/handled");
        MockHttpServletResponse handledResponse = new MockHttpServletResponse();
        filter.doFilter(
                handledRequest,
                handledResponse,
                (request, response) -> {
                    try {
                        throw new IllegalArgumentException("handled");
                    } catch (IllegalArgumentException handled) {
                        ((HttpServletResponse) response).setStatus(422);
                        response.getWriter().write("handled-body");
                    }
                });

        assertThat(captures).hasValue(0);
        assertThat(normalResponse.getStatus()).isEqualTo(204);
        assertThat(handledResponse.getStatus()).isEqualTo(422);
        assertThat(handledResponse.getContentAsString()).isEqualTo("handled-body");
    }

    @Test
    void skipsErrorDispatchToPreventDuplicateCapture() {
        AtomicInteger captures = new AtomicInteger();
        StackMateExceptionCaptureFilter filter = filter(ignored -> captures.incrementAndGet());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setDispatcherType(DispatcherType.ERROR);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RuntimeException failure = new RuntimeException("already dispatched");

        assertThatThrownBy(() -> filter.doFilter(request, response, throwingChain(failure)))
                .isSameAs(failure);

        assertThat(captures).hasValue(0);
    }

    @Test
    void handlerFailureCannotReplaceTheApplicationFailure() {
        RuntimeException applicationFailure = new RuntimeException("application");
        AssertionError handlerFailure = new AssertionError("stackmate");
        StackMateExceptionCaptureFilter filter =
                filter(
                        ignored -> {
                            throw handlerFailure;
                        });

        assertThatThrownBy(
                        () ->
                                filter.doFilter(
                                        new MockHttpServletRequest("GET", "/failure"),
                                        new MockHttpServletResponse(),
                                        throwingChain(applicationFailure)))
                .isSameAs(applicationFailure);
    }

    @Test
    void recursionGuardPreventsNestedCaptureOnTheSameFilterAndThread() {
        AtomicInteger captures = new AtomicInteger();
        AtomicReference<StackMateExceptionCaptureFilter> filterReference = new AtomicReference<>();
        ServletExceptionCaptureHandler handler =
                ignored -> {
                    captures.incrementAndGet();
                    RuntimeException nested = new RuntimeException("nested");
                    try {
                        filterReference
                                .get()
                                .doFilter(
                                        new MockHttpServletRequest("GET", "/nested"),
                                        new MockHttpServletResponse(),
                                        throwingChain(nested));
                    } catch (IOException | ServletException expected) {
                        throw new AssertionError(expected);
                    }
                };
        StackMateExceptionCaptureFilter filter = filter(handler);
        filterReference.set(filter);
        RuntimeException applicationFailure = new RuntimeException("application");

        assertThatThrownBy(
                        () ->
                                filter.doFilter(
                                        new MockHttpServletRequest("GET", "/outer"),
                                        new MockHttpServletResponse(),
                                        throwingChain(applicationFailure)))
                .isSameAs(applicationFailure);

        assertThat(captures).hasValue(1);
    }

    private StackMateExceptionCaptureFilter filter(ServletExceptionCaptureHandler handler) {
        ServletContextExtractor extractor =
                new ServletContextExtractor(
                        new MockEnvironment(),
                        new DefaultListableBeanFactory(),
                        new StackMateProperties(),
                        DefaultSanitizer.defaults());
        return new StackMateExceptionCaptureFilter(extractor, handler);
    }

    private static FilterChain throwingChain(Throwable failure) {
        return (request, response) -> throwFailure(failure);
    }

    private static void throwFailure(Throwable failure) throws IOException, ServletException {
        if (failure instanceof IOException ioException) {
            throw ioException;
        }
        if (failure instanceof ServletException servletException) {
            throw servletException;
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw (Error) failure;
    }

    private static Stream<Arguments> escapingFailures() {
        return Stream.of(
                Arguments.of(new IOException("io")),
                Arguments.of(new ServletException("servlet")),
                Arguments.of(new RuntimeException("runtime")),
                Arguments.of(new AssertionError("error")));
    }
}
