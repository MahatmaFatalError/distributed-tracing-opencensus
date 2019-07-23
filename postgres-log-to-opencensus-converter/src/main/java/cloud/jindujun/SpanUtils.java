package cloud.jindujun;

import static cloud.jindujun.SpanUtils.toNanos;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opencensus.common.Timestamp;
import io.opencensus.implcore.trace.RecordEventsSpanImpl;
import io.opencensus.trace.EndSpanOptions;
import io.opencensus.trace.SpanBuilder;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.samplers.Samplers;

public class SpanUtils {

	private static final Logger LOG = LoggerFactory.getLogger(SpanUtils.class);

	public static void closeSpan(RecordEventsSpanImpl span, ZonedDateTime endTimestamp) {
		LOG.info("Before Closing span {} of trace {} end: {}", span.getContext().getSpanId(), span.getContext().getTraceId(),
				endTimestamp);
		
		span.end(EndSpanOptions.DEFAULT, toTimestamp(endTimestamp));

		LOG.info("Closing span {} of trace {} with start: {} and end: {}", span.getContext().getSpanId(), span.getContext().getTraceId(),
				getInstantFromNanos(span.getStartNanoTime()), getInstantFromNanos(span.getEndNanoTime()));
	}

	public static Timestamp toTimestamp(ZonedDateTime endTimestamp) {
		return Timestamp.fromMillis(endTimestamp.toInstant().toEpochMilli());
	}

	public static long toNanos(ZonedDateTime endTs) {
		return TimeUnit.SECONDS.toNanos(endTs.toInstant().getEpochSecond()) + endTs.toInstant().getNano();
	}

	public static Instant getInstantFromNanos(Long nanosSinceEpoch) {
		return Instant.ofEpochSecond(0L, nanosSinceEpoch);
	}
	
    public static SpanBuilder buildSpan(Tracer tracer, String name) {
        return tracer.spanBuilder(name)
                .setRecordEvents(true)
                .setSampler(Samplers.alwaysSample());
    }
    
	public static void startSpan(ZonedDateTime startTimestamp, RecordEventsSpanImpl span) {
		span.setStartTime(toTimestamp(startTimestamp));
		LOG.info("Starting span {} of trace {} with original start: {} and converted start: {}", span.getContext().getSpanId(), span.getContext().getTraceId(),
				startTimestamp, getInstantFromNanos(span.getStartNanoTime()));
	}
}
