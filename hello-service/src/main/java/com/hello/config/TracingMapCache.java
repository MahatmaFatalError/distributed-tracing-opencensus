package com.hello.config;

import java.util.HashMap;

import org.joor.Reflect;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.lang.Nullable;

import com.hello.utils.SpanUtils;

import io.opencensus.common.Scope;
import io.opencensus.implcore.trace.RecordEventsSpanImpl;
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
		Span span = SpanUtils.buildSpan(tracer, "Check Cache").startSpan();
		ValueWrapper response = null;
		try (Scope ws = tracer.withSpan(span)) {
			return super.get(key);
		} finally {
			HashMap<String, AttributeValue> map = new HashMap<String, AttributeValue>();
			if (response == null) {
				Reflect.on(span).set("name", "Cache Miss");
				map.put("cache_miss", AttributeValue.booleanAttributeValue(true));
			} else {
				Reflect.on(span).set("name", "Cache Hit");
				map.put("cache_miss", AttributeValue.booleanAttributeValue(false));
			}
			span.addAnnotation(Annotation.fromDescriptionAndAttributes("Cache Behaviour", map));
			span.end();
		}
	}
}