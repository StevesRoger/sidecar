package com.wingbank.sidecar.config;

import com.wingbank.sidecar.security.CustomErrorAttribute;
import com.wingbank.sidecar.util.ContextUtil;
import org.springframework.beans.BeansException;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig implements ApplicationContextAware {

    @Bean
    public DefaultErrorAttributes errorAttributes() {
        return new CustomErrorAttribute();
    }

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        ContextUtil.setContext(context);
    }
}