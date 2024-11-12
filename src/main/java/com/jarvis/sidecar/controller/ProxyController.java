package com.jarvis.sidecar.controller;

import com.jarvis.sidecar.service.ProxyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class ProxyController {

    @Autowired
    private ProxyService service;

    @RequestMapping("/**")
    public Mono<ResponseEntity<String>> proxy(ServerHttpRequest request) {
        return service.proxy(request);
    }
}
