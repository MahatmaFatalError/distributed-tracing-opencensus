package com.hello.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.hello.utils.SpanUtils;

import io.opencensus.trace.Span;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;

@Service
public class HelloService {

	@Autowired
	JdbcTemplate template;

	private static final Logger LOG = LoggerFactory.getLogger(HelloService.class);
	private static final Tracer tracer = Tracing.getTracer();

	public String printHello() {
		Span span = SpanUtils.buildSpan(tracer, "HelloService printHello").startSpan();
		String helloStr = "Hello from Service";
		LOG.info("Printing hello");

		String name = template.queryForObject("Select name from helloworld", String.class);
		span.addAnnotation(name);

		span.end();
		return helloStr;
	}
}
