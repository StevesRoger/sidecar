package com.wingbank.sidecar.condition;


import org.springframework.context.annotation.Conditional;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(OnEnvironmentCondition.class)
public @interface ConditionalOnEnvironment {

    String key();

    String[] havingValue() default {};
}
