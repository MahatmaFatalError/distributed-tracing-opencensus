package com.hello.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.hello.utils.SpanUtils;

import io.opencensus.implcore.trace.RecordEventsSpanImpl;
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
		RecordEventsSpanImpl span = (RecordEventsSpanImpl) SpanUtils.buildSpan(tracer, "HelloService printHello").startSpan();
		String helloStr = "Hello from ";
		LOG.info("Printing hello");

		// useless long running query
		List<Map<String, Object>> result = template.queryForList(
				"select d1.name, STRING_AGG(d1.SHORT_DESCRIPTION, ',' order by age(d1.LAST_UPDATED, d1.CREATED)) from DOCUMENT_TEMPLATE d1 inner join DOCUMENT_TEMPLATE d2 on d1.id = d2.id where d1.author = ? OR d1.author = ? group by d1.name having count(*) > ? order by d1.name",
				"Jimmy", "Jessica" , 1000);
		
		String name = (String) result.get(0).get("name");
		span.addAnnotation(name);
		helloStr += name;
		SpanUtils.closeSpan(span);
		return helloStr;
	}

	@CacheEvict(value = "hello_cache", allEntries = true)
	public void evictCache() {
		log.info("cache evicted");
	}
}
