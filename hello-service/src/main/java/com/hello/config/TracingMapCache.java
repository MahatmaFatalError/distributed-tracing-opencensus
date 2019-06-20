package com.hello.config;

import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.lang.Nullable;

import io.opencensus.common.Scope;
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
			if (response == null) {
				span.addAnnotation("Cache Miss");
			} else {
				span.addAnnotation("Cache Hit");
			}
			span.end();
		}

	}

}