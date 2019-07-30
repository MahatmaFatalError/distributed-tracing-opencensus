package com.hello.spring.jdbc;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.opencensus.common.Scope;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.samplers.Samplers;

@Aspect
@Component
public class ConnectionPoolTracingAspect {
	private static final Logger LOG = LoggerFactory.getLogger(ConnectionPoolTracingAspect.class);

	@Around("execution(public static java.sql.Connection org.springframework.jdbc.datasource.DataSourceUtils.getConnection(..))")
	public Object getConnectionTracingAdvice(ProceedingJoinPoint proceedingJoinPoint) {

		Tracer tracer = Tracing.getTracer();
		Span span = tracer.spanBuilder("acquiring db connection from pool").setRecordEvents(true).setSampler(Samplers.alwaysSample())
				.startSpan();

		LOG.info("Before invoking DataSourceUtils.getConnection() method");
		Object value = null;
		try (Scope ws = tracer.withSpan(span)) {
			value = proceedingJoinPoint.proceed();
		} catch (Throwable e) {
			e.printStackTrace();
		} finally {
			span.end();
		}
		LOG.info("After invoking DataSourceUtils.getConnection() method. Return value=" + value);
		return value;
	}
}
