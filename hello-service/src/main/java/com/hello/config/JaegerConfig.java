package com.hello.config;

import io.opencensus.exporter.trace.jaeger.JaegerTraceExporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.EnableLoadTimeWeaving;

import com.hello.spring.jdbc.ConnectionPoolTracingAspect;

@Configuration
@EnableAspectJAutoProxy
@EnableLoadTimeWeaving
public class JaegerConfig {

    public JaegerConfig(@Value("${tracing.jaegerUrl}") String jaegerThriftEndpoint) {
        JaegerTraceExporter.createAndRegister(jaegerThriftEndpoint, "hello-service");
    }

//    @Bean
//    public ConnectionPoolTracingAspect myAspect() {
//        return new ConnectionPoolTracingAspect();
//    }
}
