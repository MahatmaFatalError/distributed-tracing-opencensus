package cloud.jindujun;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.opencensus.common.Scope;
import io.opencensus.common.Timestamp;
import io.opencensus.implcore.trace.RecordEventsSpanImpl;
import io.opencensus.trace.EndSpanOptions;
import io.opencensus.trace.Span;
import io.opencensus.trace.SpanBuilder;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.propagation.SpanContextParseException;
import io.opencensus.trace.samplers.Samplers;

public class RowHandler {

	private static long miliInNano = 1000000l;

	private class TimestampTuple {
		private ZonedDateTime startTs;
		private ZonedDateTime endTs;

		public ZonedDateTime getStartTs() {
			return startTs;
		}

		public TimestampTuple setStartTs(ZonedDateTime startTs) {
			this.startTs = startTs;
			return this;
		}

		public ZonedDateTime getEndTs() {
			return endTs;
		}

		public TimestampTuple setEndTs(ZonedDateTime endTs) {
			this.endTs = endTs;
			return this;
		}
	}

	private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z", Locale.ENGLISH);

	// TODO noch die Tabelle berücksichtigen, die geloggt wurde. Innerhalb einer
	// transaktion können untecschiedliche Tabellen gelockt sein und unterschiedlich
	// schnell frei gegebn werden

	/*
	 * Map<pid,traceContext>
	 */
	Map<Integer, String> pidTraceMapping = new HashMap<>();

	/*
	 * Map<pid, Map<TableId,TimestampTuple>>
	 */
	Map<Integer, Map<String, TimestampTuple>> gatheredTimeSpans = new HashMap<>();

	private static final Tracer tracer = Tracing.getTracer();

	private static Logger LOG = LoggerFactory.getLogger(RowHandler.class);

	private TraceContextLoader traceContextLoader = new TraceContextLoader();

	private Map<Integer, ZonedDateTime> waitingPids = new HashMap<>();

	public void handleRow(Map<String, Object> row) {
		String message = (String) row.get("message");
		java.sql.Timestamp sqlTimestamp = (java.sql.Timestamp) row.get("log_time");
		Integer pid = (Integer) row.get("process_id");
		LOG.info("Timestamp: " + sqlTimestamp.toString() + " Message: " + message);

		ZonedDateTime timestamp = ZonedDateTime.ofInstant(sqlTimestamp.toInstant(), ZoneId.systemDefault());
		convert(message, timestamp, pid);
	}

	public void convert(String message, ZonedDateTime timestamp, Integer pid) {
		SpanBuilder spanBuilder = null;
		SpanContext spanContext = null;

		if (message != null && message.contains("-- SpanContext{traceId=TraceId{traceId=")) {

			try {
				spanContext = traceContextLoader.loadSpanContext(message);
			} catch (SpanContextParseException e) {
				// spanBuilder =
				// tracer.spanBuilder(spanName).setRecordEvents(true).setSampler(Samplers.alwaysSample());
				LOG.warn("Parent Span is not present");
			}

			Map<String, TimestampTuple> relationTsTupleMap = gatheredTimeSpans.get(pid);
			if (relationTsTupleMap != null) {

				spanBuilder = tracer.spanBuilderWithRemoteParent("wait for lock", spanContext).setRecordEvents(true)
						.setSampler(Samplers.alwaysSample());

				pidTraceMapping.put(pid, spanContext.getTraceId().toString());

				Span tempspan = spanBuilder.startSpan();
				RecordEventsSpanImpl span = (RecordEventsSpanImpl) tempspan;

				// https://github.com/census-instrumentation/opencensus-java/issues/1905
				// https://github.com/census-instrumentation/opencensus-java/pull/1906

				TimestampTuple timestampTuple = relationTsTupleMap.get("TODO_RELATION");

				ZonedDateTime startTs = timestampTuple.getStartTs();
				long startNanos = toNanos(startTs);

				Timestamp startTsConverted = Timestamp.create(startTs.toInstant().getEpochSecond(),
						startTs.toInstant().getNano());

				ZonedDateTime endTs = timestampTuple.getEndTs();

				Timestamp endTsConverted = Timestamp.create(endTs.toInstant().getEpochSecond(),
						endTs.toInstant().getNano());

				long endNanos = toNanos(endTs);

				LOG.info("startTs:	" + startTs.toString());
				LOG.info("endTs: 	" + endTs.toString());
				LOG.info("startTsConverted: " + startTsConverted.toString());
				LOG.info("endTsConverted: 	" + endTsConverted.toString());
				LOG.info("startNanos:	" + startNanos);
				LOG.info("endNanos: 	" + endNanos);

				span.setStartTime(startNanos);

				try (Scope ws = tracer.withSpan(span)) {
					span.addAnnotation(message);
					gatheredTimeSpans.remove(pid);
				} finally {
					span.end(EndSpanOptions.DEFAULT, endNanos);
				}
			}

		}

		if (message != null && message.contains("plan:")) {
			LOG.info(message);
			String json = message.substring(message.indexOf('\n') + 1);
			JsonObject jsonObject = new JsonParser().parse(json).getAsJsonObject();
			LOG.info(jsonObject.toString());
		}

		if (message != null && message.contains("still waiting for")) {

			Map<String, TimestampTuple> value = new HashMap<>();
			TimestampTuple tsTuple = new TimestampTuple().setStartTs(timestamp);
			value.put("TODO_RELATION", tsTuple);

			gatheredTimeSpans.put(pid, value);

			waitingPids.put(pid, timestamp);

		} else if (message != null && message.contains(" acquired ") && waitingPids.containsKey(pid)) {

			Map<String, TimestampTuple> relationTsTupleMap = gatheredTimeSpans.get(pid);
			TimestampTuple timestampTuple = relationTsTupleMap.get("TODO_RELATION");
			timestampTuple.setEndTs(timestamp);
			relationTsTupleMap.put("TODO_RELATION", timestampTuple);

		}
	}

	private long toNanos(ZonedDateTime endTs) {
		return TimeUnit.SECONDS.toNanos(endTs.toInstant().getEpochSecond())
				+ endTs.toInstant().getNano();
	}

}
