package cloud.jindujun.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class JaegerConfig {

//	public JaegerConfig(@Value("${tracing.jaegerUrl}") String jaegerThriftEndpoint) {
//		// JaegerTraceExporter.createAndRegister(jaegerThriftEndpoint, "PostgreSQL-log-converter");
//		JaegerTraceExporter.createAndRegister(builder().setServiceName("PostgreSQL-log-converter").setThriftEndpoint(jaegerThriftEndpoint).build());
//	}
}
