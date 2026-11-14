package com.hookforge.demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class DemoWebConfiguration implements WebMvcConfigurer {
    private final DemoRequestInterceptor interceptor;
    public DemoWebConfiguration(DemoRequestInterceptor interceptor) { this.interceptor = interceptor; }

    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/v1/demo/**");
    }
}
