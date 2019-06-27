package cloud.jindujun;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
	Map<String, String> pidTraceMapping = new HashMap<>();

	/*
	 * Map<pid, Map<TableId,TimestampTuple>>
	 */
	Map<String, Map<String, TimestampTuple>> gatheredTimeSpans = new HashMap<>();

	private static final Tracer tracer = Tracing.getTracer();

	private static Logger LOG = LoggerFactory.getLogger(RowHandler.class);

	private TraceContextLoader traceContextLoader = new TraceContextLoader();

	private Map<String, String> waitingPids = new HashMap<>();

	public void handleRow(Map<String, Object> row) {
		String message = (String) row.get("message");
		String timestamp = (String) row.get("log_time");
		String pid = (String) row.get("process_id");
		LOG.info(message);
		convert(message, timestamp, pid);
	}

	public void convert(String message, String timestamp, String pid) {
		SpanBuilder spanBuilder = null;

		if (message != null && message.contains("-- SpanContext{traceId=TraceId{traceId=")) {

			String spanName = "Holger";

			try {
				SpanContext spanContext = traceContextLoader.loadSpanContext(message);

				spanBuilder = tracer.spanBuilderWithRemoteParent(spanName, spanContext).setRecordEvents(true);

				pidTraceMapping.put(pid, spanContext.getTraceId().toString());
			} catch (SpanContextParseException e) {
				spanBuilder = tracer.spanBuilder(spanName).setRecordEvents(true);
				LOG.warn("Parent Span is not present");
			}

			Span tempspan = spanBuilder.startSpan();
			RecordEventsSpanImpl span = (RecordEventsSpanImpl) tempspan;

			// https://github.com/census-instrumentation/opencensus-java/issues/1905
			// https://github.com/census-instrumentation/opencensus-java/pull/1906

			Map<String, TimestampTuple> relationTsTupleMap = gatheredTimeSpans.get(pid);
			TimestampTuple timestampTuple = relationTsTupleMap.get("TODO_RELATION");

			Timestamp startTsConverted = Timestamp.create(timestampTuple.getStartTs().toInstant().getEpochSecond(),
					timestampTuple.getStartTs().toInstant().getNano());
			Timestamp endTsConverted = Timestamp.create(timestampTuple.getEndTs().toInstant().getEpochSecond(),
					timestampTuple.getEndTs().toInstant().getNano());

			span.setStartTime(startTsConverted);

			try (Scope ws = tracer.withSpan(span)) {
				span.addAnnotation(message);
			} finally {
				span.end(EndSpanOptions.DEFAULT, endTsConverted);
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
			ZonedDateTime startTs = ZonedDateTime.parse(timestamp, formatter);
			TimestampTuple tsTuple = new TimestampTuple().setStartTs(startTs);
			value.put("TODO_RELATION", tsTuple);

			gatheredTimeSpans.put(pid, value);

			waitingPids.put(pid, timestamp);

		} else if (message != null && message.contains(" acquired ") && waitingPids.containsKey(pid)) {

			ZonedDateTime endTs = ZonedDateTime.parse(timestamp, formatter);

			Map<String, TimestampTuple> relationTsTupleMap = gatheredTimeSpans.get(pid);
			TimestampTuple timestampTuple = relationTsTupleMap.get("TODO_RELATION");
			timestampTuple.setEndTs(endTs);
			relationTsTupleMap.put(pid, timestampTuple);

		}
	}

}
