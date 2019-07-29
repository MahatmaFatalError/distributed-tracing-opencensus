package com.hello.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hello.service.HelloService;
import com.hello.utils.SpanUtils;

import io.opencensus.common.Scope;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;

@RestController("/hello")
public class HelloController {
	private static final Tracer tracer = Tracing.getTracer();

	private final HelloService helloService;

	@Autowired
	@Lazy
	JdbcTemplate template;

	@Autowired
	public HelloController(HelloService helloService) {
		this.helloService = helloService;
	}

	@GetMapping
	public String getHello() {
		Span span = SpanUtils.buildSpan(tracer, "HelloController getHello").startSpan();
		String result;
		try (Scope ws = tracer.withSpan(span)) {
			result = helloService.printHello();
		}
		span.end();
		return result;
	}

	@GetMapping(path = "/clearcache")
	public void evictCache() {
		Span span = SpanUtils.buildSpan(tracer, "HelloController clear Cache").startSpan();

		try (Scope ws = tracer.withSpan(span)) {
			helloService.evictCache();
		}
		span.end();

	}

	@CacheEvict(value = "hello_cache", allEntries = true)
	@Transactional
	@GetMapping(path = "/lock")
	public void lockTable(@RequestParam(name = "duration", defaultValue = "2000") Integer millis) throws InterruptedException {
		Span span = SpanUtils.buildSpan(tracer, "HelloController lock table").startSpan();

		try (Scope ws = tracer.withSpan(span)) {
			template.execute("LOCK TABLE DOCUMENT_TEMPLATE IN ACCESS EXCLUSIVE MODE");
			Thread.sleep(millis);
		}
		span.end();

	}
}
