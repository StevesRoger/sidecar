package com.jarvis.sidecar.exception;

public class ProxyFailedException extends RuntimeException {

    private static final long serialVersionUID = -8596879412873012107L;

    public ProxyFailedException() {
    }

    public ProxyFailedException(String message) {
        super(message);
    }

    public ProxyFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProxyFailedException(Throwable cause) {
        super(cause);
    }

    public ProxyFailedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
