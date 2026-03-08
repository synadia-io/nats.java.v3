package io.nats.client;

import java.io.IOException;

/**
 * AuthenticationException is used when the connect process fails due to an authentication
 * problem.
 * 
 * The exception will not include the authentication tokens, but as a subclass
 * of IOException allows the client to distinguish an IO problem from an
 * authentication problem.
 */
public class AuthenticationException extends IOException {

    private static final long serialVersionUID = 1L;

    /**
     * Create a new AuthenticationException.
     * 
     * @param errorMessage the error message, see Exception for details
     */
    public AuthenticationException(String errorMessage) {
        super(errorMessage);
    }
}
