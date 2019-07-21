package cloud.jindujun.config;

import static io.opencensus.exporter.trace.jaeger.JaegerExporterConfiguration.builder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import io.opencensus.exporter.trace.jaeger.JaegerTraceExporter;

@Configuration
public class JaegerConfig {

	public JaegerConfig(@Value("${tracing.jaegerUrl}") String jaegerThriftEndpoint) {
		// JaegerTraceExporter.createAndRegister(jaegerThriftEndpoint, "PostgreSQL-log-converter");
		JaegerTraceExporter.createAndRegister(builder().setServiceName("PostgreSQL-log-converter").setThriftEndpoint(jaegerThriftEndpoint).build());
	}
}
