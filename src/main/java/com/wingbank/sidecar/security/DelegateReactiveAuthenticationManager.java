package com.wingbank.sidecar.security;

import com.wingbank.sidecar.exception.AccessTokenFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

public class DelegateReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final ReactiveAuthenticationManager delegate;

    public DelegateReactiveAuthenticationManager(ReactiveAuthenticationManager delegate) {
        Assert.notNull(delegate, "Authentication manager delegate cannot be null");
        this.delegate = delegate;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        return delegate.authenticate(authentication)
                .onErrorMap(Exception.class, ex ->
                        new AccessTokenFailedException(HttpStatus.UNAUTHORIZED, "unauthorized access", ex));
    }

    @Override
    public String toString() {
        return "DelegateReactiveAuthenticationManager{" +
                "delegate=" + delegate +
                '}';
    }
}
