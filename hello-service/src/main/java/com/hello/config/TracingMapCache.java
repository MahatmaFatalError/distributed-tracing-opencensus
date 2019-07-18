package com.hello.config;

import java.util.HashMap;

import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.lang.Nullable;

import io.opencensus.common.Scope;
import io.opencensus.trace.Annotation;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;

/**
 * wrapped caching layer
 */
public class TracingMapCache extends ConcurrentMapCache {

	private static final Tracer tracer = Tracing.getTracer();

	public TracingMapCache(String name) {
		super(name);

	}

	@Override
	@Nullable
	public ValueWrapper get(Object key) {
		Span span = com.hello.utils.SpanUtils.buildSpan(tracer, "check cache").startSpan();
		ValueWrapper response = null;
		try (Scope ws = tracer.withSpan(span)) {
			response = super.get(key);
			return response;
		} finally {
			HashMap<String, AttributeValue> map = new HashMap<String, AttributeValue>();			
			if (response == null) {
				map.put("cache_miss", AttributeValue.booleanAttributeValue(true));
			} else {
				map.put("cache_miss", AttributeValue.booleanAttributeValue(false));
			}
			span.addAnnotation(Annotation.fromDescriptionAndAttributes("Cache miss", map));
			span.end();
		}

	}

}