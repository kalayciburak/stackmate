package io.github.kalayciburak.stackmate.capture;

import io.github.kalayciburak.stackmate.context.ServletContextExtractor;
import io.github.kalayciburak.stackmate.context.ServletIncidentContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.web.filter.OncePerRequestFilter;

/** Observes only exceptions that escape the servlet chain and never owns the HTTP response. */
public final class StackMateExceptionCaptureFilter extends OncePerRequestFilter {

    private final ServletContextExtractor contextExtractor;
    private final ServletExceptionCaptureHandler captureHandler;
    private final ThreadLocal<Boolean> captureInProgress = new ThreadLocal<>();

    public StackMateExceptionCaptureFilter(
            ServletContextExtractor contextExtractor,
            ServletExceptionCaptureHandler captureHandler) {
        this.contextExtractor = Objects.requireNonNull(contextExtractor, "contextExtractor");
        this.captureHandler = Objects.requireNonNull(captureHandler, "captureHandler");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error failure) {
            observe(failure, request, response);
            throw failure;
        }
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getDispatcherType() == DispatcherType.ERROR;
    }

    private void observe(
            Throwable failure, HttpServletRequest request, HttpServletResponse response) {
        if (Boolean.TRUE.equals(captureInProgress.get())) {
            return;
        }
        captureInProgress.set(Boolean.TRUE);
        try {
            ServletIncidentContext context = contextExtractor.extract(request, response);
            captureHandler.capture(new CapturedServletException(failure, context));
        } catch (Throwable ignored) {
            // StackMate is fail-open: its own failure must never replace the application exception.
        } finally {
            captureInProgress.remove();
        }
    }
}
