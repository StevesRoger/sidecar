package com.jarvis.sidecar.exception;

import org.springframework.http.HttpStatusCode;
import org.springframework.lang.Nullable;
import org.springframework.web.server.ResponseStatusException;

public class ProxyResponseFailedException extends ResponseStatusException {

    private static final long serialVersionUID = -559105670751965186L;

    private String response;

    public ProxyResponseFailedException(HttpStatusCode status) {
        super(status);
    }

    public ProxyResponseFailedException(HttpStatusCode status, String reason) {
        super(status, reason);
    }

    public ProxyResponseFailedException(HttpStatusCode status, String reason, String response) {
        super(status, reason);
        this.response = response;
    }

    public ProxyResponseFailedException(int rawStatusCode, String reason, Throwable cause) {
        super(rawStatusCode, reason, cause);
    }

    public ProxyResponseFailedException(int rawStatusCode, String reason, String response, Throwable cause) {
        super(rawStatusCode, reason, cause);
        this.response = response;
    }

    public ProxyResponseFailedException(HttpStatusCode status, String reason, Throwable cause) {
        super(status, reason, cause);
    }

    public ProxyResponseFailedException(HttpStatusCode status, String reason, String response, Throwable cause) {
        super(status, reason, cause);
        this.response = response;
    }


    protected ProxyResponseFailedException(HttpStatusCode status, String reason, Throwable cause, String messageDetailCode, Object[] messageDetailArguments) {
        super(status, reason, cause, messageDetailCode, messageDetailArguments);
    }

    @Nullable
    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }
}
