package org.group7.wearwise.exception;

public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException() {
        super("Invalid username or password.");
    }

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
