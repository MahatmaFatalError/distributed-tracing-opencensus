package com.hello.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.hello.utils.SpanUtils;

import io.opencensus.trace.Span;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;

@Service
public class HelloService {

	private static final Logger log = LoggerFactory.getLogger(HelloService.class);

	@Autowired
	JdbcTemplate template;

	private static final Logger LOG = LoggerFactory.getLogger(HelloService.class);
	private static final Tracer tracer = Tracing.getTracer();

	@Cacheable("hello_cache")
	public String printHello() {
		Span span = SpanUtils.buildSpan(tracer, "HelloService printHello").startSpan();
		String helloStr = "Hello from ";
		LOG.info("Printing hello");

		String name = template.queryForObject("Select name from helloworld", String.class);
		span.addAnnotation(name);
		helloStr += name;
		span.end();
		return helloStr;
	}

	@CacheEvict(value = "hello_cache", allEntries = true)
	public void evictCache() {
		log.info("cache evicted");
	}
}
