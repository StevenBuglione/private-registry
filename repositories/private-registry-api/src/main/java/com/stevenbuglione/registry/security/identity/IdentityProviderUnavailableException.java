package com.stevenbuglione.registry.security.identity;

public class IdentityProviderUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IdentityProviderUnavailableException(String message) {
        super(message);
    }

    public IdentityProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
