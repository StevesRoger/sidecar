package com.jarvis.sidecar.condition;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;

import java.util.List;

@Order(Ordered.HIGHEST_PRECEDENCE + 41)
public class OnEnvironmentCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        MultiValueMap<String, Object> allAnnotationAttributes = metadata.getAllAnnotationAttributes(ConditionalOnEnvironment.class.getName());
        List<Object> keys = allAnnotationAttributes.get("key");
        List<Object> values = allAnnotationAttributes.get("havingValue");
        if (CollectionUtils.isEmpty(keys) || CollectionUtils.isEmpty(values)) return false;
        String key = (String) keys.get(0);
        if (StringUtils.isEmpty(key)) return false;
        String[] properties = (String[]) values.get(0);
        for (String value : properties) {
            String env = System.getenv(key);
            if (StringUtils.isNotEmpty(env) && env.equals(value))
                return true;
        }
        return false;
    }
}
