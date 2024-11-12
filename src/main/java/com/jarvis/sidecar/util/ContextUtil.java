package com.jarvis.sidecar.util;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Optional;

public final class ContextUtil {

    private static ApplicationContext context;

    public static void setContext(ApplicationContext context) {
        ContextUtil.context = context;
    }

    public static <T> T getBean(Class<T> clazz) {
        return optBean(clazz).orElse(null);
    }

    public static <T> Optional<T> optBean(Class<T> clazz) {
        try {
            return Optional.ofNullable(context).flatMap(v -> Optional.of(v.getBean(clazz)));
        } catch (BeansException e) {
            return Optional.empty();
        }
    }

    public static String getTraceId() {
        return optBean(Tracer.class).map(Tracer::currentSpan)
                .map(Span::context).map(TraceContext::traceId).orElse(null);
    }

    public static String getAppName() {
        return optBean(Environment.class)
                .map(v -> v.getProperty("spring.application.name", "sidecar"))
                .orElse("sidecar");
    }
}
