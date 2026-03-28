package com.gacfox.meowclaw.exception;

public class ServiceNotSatisfiedException extends RuntimeException {
    public ServiceNotSatisfiedException(String message) {
        super(message);
    }
}
