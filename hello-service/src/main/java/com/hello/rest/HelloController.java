package com.hello.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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

	private static final Logger log = LoggerFactory.getLogger(HelloController.class);
	private static final Tracer tracer = Tracing.getTracer();

	private final HelloService helloService;

	@Autowired
	@Lazy
	JdbcTemplate template;

	@Autowired
	public HelloController(HelloService helloService) {
		this.helloService = helloService;
	}

	@GetMapping("/hello")
	public String getHello() {
		return helloService.printHello();
	}

	@Cacheable("hello_cache")
	@GetMapping("/hellocache")
	public String getCachedHello() {
		return helloService.printHello();
	}

	@CacheEvict(value = "hello_cache", allEntries = true)
	@GetMapping(path = "/clearcache")
	public void evictCache() {
		Span span = SpanUtils.buildSpan(tracer, "HelloController clear Cache").startSpan();

		try (Scope ws = tracer.withSpan(span)) {
			log.info("cache evicted");
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

	@RequestMapping(value = "/sleepquery", method = RequestMethod.GET)
	@Transactional
	public ResponseEntity<Void> execSleepQuery(@RequestParam(name = "duration", defaultValue = "400") Integer millis) {
		double seconds = millis.doubleValue() / 1000;

		template.queryForMap("SELECT pg_sleep(?)", seconds);

		return new ResponseEntity<Void>(HttpStatus.NO_CONTENT);
	}
}
