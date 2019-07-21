package cloud.jindujun;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import cloud.jindujun.executionplan.ExecPlan;
import cloud.jindujun.executionplan.ExecPlanJsonParser;
import cloud.jindujun.executionplan.PlanEnvelop;
import io.opencensus.common.Scope;
import io.opencensus.implcore.trace.RecordEventsSpanImpl;
import io.opencensus.trace.EndSpanOptions;
import io.opencensus.trace.Span;
import io.opencensus.trace.SpanBuilder;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.propagation.SpanContextParseException;
import io.opencensus.trace.samplers.Samplers;

@Component
public class RowHandler {

	private int LOCK_OFFSET_SEC = 1;

	private boolean convertHierarchicalSpans;

	public RowHandler(@Value("${tracing.convertHierarchicalSpans}") boolean convertHierarchicalSpans) {
		this.convertHierarchicalSpans = convertHierarchicalSpans;
	}

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
		String command = (String) row.get("command_tag");
		LOG.info("Timestamp: " + sqlTimestamp.toString() + " Message: " + message);

		ZonedDateTime timestamp = ZonedDateTime.ofInstant(sqlTimestamp.toInstant(), ZoneId.systemDefault());
		convert(message, timestamp, pid, command);
	}

	public void convert(String message, ZonedDateTime timestamp, Integer pid, String command) {

		if (message != null && message.contains("-- SpanContext{traceId=TraceId{traceId=") && !message.contains("plan:")
				&& !message.contains("still waiting for") && !message.contains(" acquired ")) {

			handleLockMessage(message, timestamp, pid);
		}

		if (message != null && message.contains("-- SpanContext{traceId=TraceId{traceId=") && message.contains("plan:") && "SELECT".equals(command)) {
			handleExecPlanMessage(message, timestamp);
		}

		if (message != null && message.contains("still waiting for")) {
			LOG.info("Timestamp: " + timestamp + " Message: " + message);
			Map<String, TimestampTuple> value = new HashMap<>();
			TimestampTuple tsTuple = new TimestampTuple().setStartTs(timestamp);
			value.put("TODO_RELATION", tsTuple); // Eigenen Lock Context speichern

			gatheredTimeSpans.put(pid, value);

			waitingPids.put(pid, timestamp);

		} else if (message != null && message.contains(" acquired ") && waitingPids.containsKey(pid)) {
			LOG.info("Timestamp: " + timestamp + " Message: " + message);
			Map<String, TimestampTuple> relationTsTupleMap = gatheredTimeSpans.get(pid);
			TimestampTuple timestampTuple = relationTsTupleMap.get("TODO_RELATION");
			timestampTuple.setEndTs(timestamp);
			relationTsTupleMap.put("TODO_RELATION", timestampTuple);

		}
	}

	private void handleExecPlanMessage(String message, ZonedDateTime timestamp) {
		int newLineIndex = message.indexOf('\n');
		String duration = message.substring(0, newLineIndex);

		LOG.info("Timestamp: " + timestamp + " Duration: " + duration + " Message: " + message);
		Pattern pattern = Pattern.compile("duration: (.*) ms.*");
		Matcher matcher = pattern.matcher(duration);

		if (matcher.matches()) {
			String durationInMs = matcher.group(1);
			Double durationInMsDouble = Double.valueOf(durationInMs);
			String json = message.substring(newLineIndex + 1);

			try {
				LOG.info("Execution Plan found: ");// + json);
				PlanEnvelop planWrapper = ExecPlanJsonParser.getInstance().parse(json);
				try {
					SpanContext spanContext = traceContextLoader.loadSpanContext(message);

					SpanBuilder spanBuilder = tracer.spanBuilderWithRemoteParent("DB exec plan", spanContext).setRecordEvents(true)
							.setSampler(Samplers.alwaysSample());
					RecordEventsSpanImpl span = (RecordEventsSpanImpl) spanBuilder.startSpan();
					ZonedDateTime startTimestamp = timestamp;
					ZonedDateTime endTimestamp = timestamp.plus(getEndMicroSecs(durationInMsDouble), ChronoUnit.MICROS);
					span.setStartTime(toNanos(startTimestamp));

					try (Scope ws = tracer.withSpan(span)) {
						collectSpansFromExecPlanPreOrder(planWrapper.getPlan(), span, timestamp); // TODO: timestamp von vorherigem, initialen log
																									// des statements holen

						// collectSpansFromExecPlanPostOrder(planWrapper.getPlan(), span, timestamp, null);
					} finally {
						span.end(EndSpanOptions.DEFAULT, toNanos(endTimestamp));
					}
				} catch (SpanContextParseException e) {
					LOG.warn("Parent Span is not present");
				}

			} catch (IOException e) {
				LOG.error("Parsing the json failed ", e);
			}
		}
	}

	private long getEndMicroSecs(Double durationInMsDouble) {
		Double d = new Double(1000 * durationInMsDouble);
		return d.longValue();
	}

	private void collectSpansFromExecPlanPostOrder(ExecPlan plan, Span parentSpan, ZonedDateTime timestamp, SpanBuilder defaultSpanBuilder) {
		ZonedDateTime startTimestamp = plan.getStart(timestamp);
		ZonedDateTime endTimestamp = plan.getEnd(timestamp);

		LOG.info("startTimestamp	: " + startTimestamp + " of node " + plan.getNodeType());
		LOG.info("endTimestamp	: " + endTimestamp + " of node " + plan.getNodeType());

		Duration duration = Duration.between(startTimestamp, endTimestamp);
		LOG.info("Duration (in ns)	: " + duration.getNano() + " of node " + plan.getNodeType());

		if (endTimestamp.isBefore(startTimestamp)) {
			LOG.info("Plan	: " + plan.getNodeType() + " is corrupt");
			return;
		}

		// SpanBuilder spanBuilder = defaultSpanBuilder;
		SpanBuilder spanBuilder = tracer.spanBuilder(plan.getNodeType());
		// SpanBuilder spanBuilder =
		// tracer.spanBuilderWithExplicitParent(plan.getNodeType(), parentSpan);

		for (ExecPlan subPlan : plan.getPlans()) {
			collectSpansFromExecPlanPostOrder(subPlan, null, timestamp, spanBuilder);
		}
		RecordEventsSpanImpl span = (RecordEventsSpanImpl) spanBuilder.startSpan();
		span.setStartTime(toNanos(startTimestamp));

		try (Scope ws = tracer.withSpan(span)) {
			span.addAnnotation(plan.getNodeType());
			LOG.info("Span started with trace Id:" + span.getContext().getTraceId() + " and node " + plan.getNodeType());

		} finally {
			span.end(EndSpanOptions.DEFAULT, toNanos(endTimestamp));
		}

	}

	private void collectSpansFromExecPlanPreOrder(ExecPlan plan, Span parentSpan, ZonedDateTime timestamp) {
		ZonedDateTime startTimestamp = plan.getStart(timestamp);
		ZonedDateTime endTimestamp = plan.getEnd(timestamp);

		LOG.info("startTimestamp	: " + startTimestamp + " of node " + plan.getNodeType());
		LOG.info("endTimestamp	: " + endTimestamp + " of node " + plan.getNodeType());

		Duration duration = Duration.between(startTimestamp, endTimestamp);
		LOG.info("Duration (in ns)	: " + duration.getNano() + " of node " + plan.getNodeType());

		if (endTimestamp.isBefore(startTimestamp)) {
			LOG.info("Plan	: " + plan.getNodeType() + " is corrupt");
			return;
		}

		// SpanBuilder spanBuilder = tracer.spanBuilder(plan.getNodeType());
		SpanBuilder spanBuilder = tracer.spanBuilderWithExplicitParent(plan.getNodeType(), parentSpan);
		RecordEventsSpanImpl span = (RecordEventsSpanImpl) spanBuilder.startSpan();
		span.setStartTime(toNanos(startTimestamp));

		try (Scope ws = tracer.withSpan(span)) {
			span.addAnnotation(plan.getNodeType());
			LOG.info("Span started with trace Id:" + span.getContext().getTraceId() + " and node " + plan.getNodeType());

			for (ExecPlan subPlan : plan.getPlans()) {
				collectSpansFromExecPlanPreOrder(subPlan, span, timestamp);
			}
		} finally {
			span.end(EndSpanOptions.DEFAULT, toNanos(endTimestamp));
		}

	}

	private void handleLockMessage(String message, ZonedDateTime timestamp, Integer pid) {
		SpanBuilder spanBuilder = null;
		SpanContext spanContext = null;
		LOG.info("Timestamp: " + timestamp + " Message: " + message);
		try {
			spanContext = traceContextLoader.loadSpanContext(message);
		} catch (SpanContextParseException e) {
			LOG.warn("Parent Span is not present");
		}

		Map<String, TimestampTuple> relationTsTupleMap = gatheredTimeSpans.get(pid);
		if (relationTsTupleMap != null) {

			spanBuilder = tracer.spanBuilderWithRemoteParent("wait for lock", spanContext).setRecordEvents(true).setSampler(Samplers.alwaysSample());

			pidTraceMapping.put(pid, spanContext.getTraceId().toString());

			Span tempspan = spanBuilder.startSpan();
			RecordEventsSpanImpl span = (RecordEventsSpanImpl) tempspan;

			// https://github.com/census-instrumentation/opencensus-java/issues/1905
			// https://github.com/census-instrumentation/opencensus-java/pull/1906

			TimestampTuple timestampTuple = relationTsTupleMap.get("TODO_RELATION");

			ZonedDateTime startTs = timestampTuple.getStartTs();
			long startNanos = toNanos(startTs) - TimeUnit.SECONDS.toNanos(LOCK_OFFSET_SEC);

			ZonedDateTime endTs = timestampTuple.getEndTs();

			long endNanos = toNanos(endTs);

			span.setStartTime(startNanos);
			LOG.info("Entering Lock-Wait for Span with trace Id:" + span.getContext().getTraceId());

			try (Scope ws = tracer.withSpan(span)) {
				span.addAnnotation(message);
				gatheredTimeSpans.remove(pid);
			} finally {
				span.end(EndSpanOptions.DEFAULT, endNanos);
			}
		}
	}

	private long toNanos(ZonedDateTime endTs) {
		return TimeUnit.SECONDS.toNanos(endTs.toInstant().getEpochSecond()) + endTs.toInstant().getNano();
	}

}
