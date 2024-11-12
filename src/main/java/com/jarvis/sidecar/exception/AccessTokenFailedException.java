package com.jarvis.sidecar.exception;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

public class AccessTokenFailedException extends ResponseStatusException {

    private static final long serialVersionUID = 2799749718653769244L;

    public AccessTokenFailedException(HttpStatusCode status) {
        super(status);
    }

    public AccessTokenFailedException(HttpStatusCode status, String reason) {
        super(status, reason);
    }

    public AccessTokenFailedException(int rawStatusCode, String reason, Throwable cause) {
        super(rawStatusCode, reason, cause);
    }

    public AccessTokenFailedException(HttpStatusCode status, String reason, Throwable cause) {
        super(status, reason, cause);
    }

    protected AccessTokenFailedException(HttpStatusCode status, String reason, Throwable cause, String messageDetailCode, Object[] messageDetailArguments) {
        super(status, reason, cause, messageDetailCode, messageDetailArguments);
    }

    @Override
    public String getMessage() {
        return getReason();
    }
}
