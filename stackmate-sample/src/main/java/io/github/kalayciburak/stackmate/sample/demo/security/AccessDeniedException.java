package io.github.kalayciburak.stackmate.sample.demo.security;

/** Sample-local equivalent used to demonstrate optional security exception analysis. */
public final class AccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AccessDeniedException(String message) {
        super(message);
    }
}
