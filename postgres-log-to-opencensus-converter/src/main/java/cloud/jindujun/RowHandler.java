package cloud.jindujun;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joor.Reflect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import cloud.jindujun.executionplan.ExecPlan;
import cloud.jindujun.executionplan.ExecPlanJsonParser;
import cloud.jindujun.executionplan.PlanEnvelop;
import io.jaegertracing.Configuration;
import io.jaegertracing.Configuration.ReporterConfiguration;
import io.jaegertracing.Configuration.SamplerConfiguration;
import io.jaegertracing.internal.JaegerSpan;
import io.jaegertracing.internal.JaegerSpanContext;
import io.jaegertracing.internal.JaegerTracer;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

@Component
public class RowHandler {

	private int LOCK_OFFSET_MILLISEC = 250;

	private boolean convertHierarchicalSpans;
	private boolean convertExecPlans;
	private boolean convertLocks;

	public RowHandler(@Value("${tracing.features.locks}") boolean convertLocks, @Value("${tracing.features.execplans}") boolean convertExecPlans,
			@Value("${tracing.features.convertHierarchicalSpans}") boolean convertHierarchicalSpans) {
		this.convertHierarchicalSpans = convertHierarchicalSpans;
		this.convertExecPlans = convertExecPlans;
		this.convertLocks = convertLocks;
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
	 * Map<pid,Map<SpanId,start date of first SELECT>>
	 */
	Map<Integer, Map<String, ZonedDateTime>> pidEffectiveStartMapping = new HashMap<>();

	/*
	 * Map<pid, Map<TableId,TimestampTuple>>
	 */
	Map<Integer, Map<String, TimestampTuple>> gatheredTimeSpans = new HashMap<>();

	private static final Tracer tracer = initTracer("PostgreSQL-Log");

	private static final Logger LOG = LoggerFactory.getLogger(RowHandler.class);

	private TraceContextLoader traceContextLoader = new TraceContextLoader();

	private Map<Integer, ZonedDateTime> waitingPids = new HashMap<>();

	private static Pattern relationPattern = Pattern.compile(".*on relation (.*) of database.*");
	private static Pattern relationPatternWithLock = Pattern.compile(".*acquired (.*) on relation (.*) of database.*");
	private static Pattern spanIdPattern = Pattern.compile(".*spanId=(.*)},.*");

	private static Pattern durationPattern = Pattern.compile("duration: (.*) ms.*");

	public void handleRow(Map<String, Object> row) {
		String message = (String) row.get("message");
		java.sql.Timestamp sqlTimestamp = (java.sql.Timestamp) row.get("log_time");
		Integer pid = (Integer) row.get("process_id");
		String command = (String) row.get("command_tag");
		String query = (String) row.get("query");

		String printMessage = message.length() > 500 ? message.substring(0, 500) + "..." : message;

		LOG.info("HandleRow: {} ", row);
		// LOG.info("HandleRow: Timestamp: {} Message: {} ", sqlTimestamp.toString() , printMessage);

		ZonedDateTime timestamp = ZonedDateTime.ofInstant(sqlTimestamp.toInstant(), ZoneId.systemDefault());
		convert(message, timestamp, pid, command, query);
	}

	public void convert(String message, ZonedDateTime timestamp, Integer pid, String command, String query) {
		if (message != null) {
			if (message.contains("-- SpanContext{traceId=TraceId{traceId=")) {

				if (convertLocks && !message.contains("plan:") && !message.contains("still waiting for") && !message.contains(" acquired ")) {
					// createLockSpan(message, timestamp, pid, "lock");
				}

				if (message.startsWith("execute ") && "SELECT".equals(command)) {
					LOG.info("Execute SELECT found for pid {}", pid);
					int newLineIndex = message.indexOf('\n');
					Matcher matcher = spanIdPattern.matcher(message.substring(0, newLineIndex));

					if (matcher.matches()) {
						String spanId = matcher.group(1);
						HashMap<String, ZonedDateTime> spanToStartTimeMap = new HashMap<>();
						spanToStartTimeMap.put(spanId, timestamp);
						pidEffectiveStartMapping.put(pid, spanToStartTimeMap);
					}
				}

				if (convertExecPlans && message.contains("plan:") && ("BIND".equals(command) || "SELECT".equals(command))) {
					LOG.info("Exec Plan found for pid {}", pid);
					handleExecPlanMessage(message, pid);
				}
			}

			if (message.contains("still waiting for")) {
				LOG.info("still waiting for Lock! Timestamp: " + timestamp + " Message: " + message);
				Map<String, TimestampTuple> value = new HashMap<>();
				TimestampTuple tsTuple = new TimestampTuple().setStartTs(timestamp);

				Matcher matcher = relationPattern.matcher(message); // process 1169 still waiting for AccessShareLock on relation 16384 of database 13067

				if (matcher.matches()) {
					String relationId = matcher.group(1);
					value.put(relationId, tsTuple); // "TODO_RELATION" Eigenen Lock Context speichern

					gatheredTimeSpans.put(pid, value);

					waitingPids.put(pid, timestamp);
				}

			} else if (message.contains(" acquired ") && waitingPids.containsKey(pid)) { // process 1169 acquired AccessShareLock on relation 16384 of database 13067 after 3138.526
																							// ms
				LOG.info("Lock acquired! Timestamp: " + timestamp + " Message: " + message);
				waitingPids.remove(pid);
				Map<String, TimestampTuple> relationTsTupleMap = gatheredTimeSpans.get(pid);

				Matcher matcher = relationPatternWithLock.matcher(message); // process 1169 acquired AccessShareLock on relation 16384 of database 13067 after 3138.526 ms

				if (matcher.matches()) {
					String lockType = matcher.group(1);
					String relationId = matcher.group(2);
					TimestampTuple timestampTuple = relationTsTupleMap.get(relationId);
					timestampTuple.setEndTs(timestamp);
					relationTsTupleMap.put(relationId, timestampTuple);

					createLockSpan(query, timestamp, pid, lockType);
				}

			}
		}
	}

	private void handleExecPlanMessage(String message, Integer pid) {
		int newLineIndex = message.indexOf('\n');
		String duration = message.substring(0, newLineIndex);
		// LOG.info(" Duration: " + duration + " Message: " + message);

		Matcher matcher = durationPattern.matcher(duration);

		if (matcher.matches()) {
			String durationInMs = matcher.group(1);
			Double durationInMsDouble = Double.valueOf(durationInMs);
			String json = message.substring(newLineIndex + 1);

			try {
				LOG.info("Execution Plan found: ");// + json);
				PlanEnvelop planWrapper = ExecPlanJsonParser.getInstance().parse(json);

				SpanContext spanContext = traceContextLoader.loadSpanContext(message);
				Map<String, ZonedDateTime> realSpanStart = pidEffectiveStartMapping.get(pid);

				matcher = spanIdPattern.matcher(message.replaceAll("\\r\\n|\\r|\\n", " "));

				if (matcher.matches()) {
					String spanId = matcher.group(1);

					ZonedDateTime timestamp = realSpanStart.remove(spanId); // timestamp von vorherigem, initialen log des statements holen

					if (convertHierarchicalSpans) {
						ZonedDateTime startTimestamp = timestamp;
						ZonedDateTime endTimestamp = timestamp.plus(getEndMicroSecs(durationInMsDouble), ChronoUnit.MICROS); // NPE timestamp is null

						JaegerSpan span = startSpan(spanContext, startTimestamp, "execPlan");

						collectSpansFromExecPlanPreOrder(planWrapper.getPlan(), span, timestamp);

						closeSpan(span, endTimestamp);
					} else {
						collectSpansFromExecPlan(planWrapper.getPlan(), spanContext, timestamp);
					}
				}

			} catch (IOException e) {
				LOG.error("Parsing the json failed ", e);
			}
		}
	}

	private JaegerSpan startSpan(SpanContext spanContext, ZonedDateTime startTimestamp, String operationName) {
		JaegerSpan span = (JaegerSpan) tracer.buildSpan(operationName).asChildOf(spanContext).start();
		long startMicros = ChronoUnit.MICROS.between(Instant.EPOCH, startTimestamp.toInstant());// TimeUnit.MILLISECONDS.toMicros(startTimestamp.toInstant().toEpochMilli());
		Reflect.on(span).set("startTimeMicroseconds", startMicros);
		return span;
	}

	private long getEndMicroSecs(Double durationInMsDouble) {
		Double d = Double.valueOf(1000 * durationInMsDouble);
		return d.longValue();
	}

//	private void collectSpansFromExecPlanPostOrder(ExecPlan plan, Span parentSpan, ZonedDateTime timestamp, SpanBuilder defaultSpanBuilder) {
//		ZonedDateTime startTimestamp = plan.getStart(timestamp);
//		ZonedDateTime endTimestamp = plan.getEnd(timestamp);
//
//		LOG.info("startTimestamp	: " + startTimestamp + " of node " + plan.getNodeType());
//		LOG.info("endTimestamp	: " + endTimestamp + " of node " + plan.getNodeType());
//
//		Duration duration = Duration.between(startTimestamp, endTimestamp);
//		LOG.info("Duration (in ns)	: " + duration.getNano() + " of node " + plan.getNodeType());
//
//		if (endTimestamp.isBefore(startTimestamp)) {
//			LOG.info("Plan	: " + plan.getNodeType() + " is corrupt");
//			return;
//		}
//
//		// SpanBuilder spanBuilder = defaultSpanBuilder;
//		SpanBuilder spanBuilder = tracer.spanBuilder(plan.getNodeType());
//		// SpanBuilder spanBuilder =
//		// tracer.spanBuilderWithExplicitParent(plan.getNodeType(), parentSpan);
//
//		for (ExecPlan subPlan : plan.getPlans()) {
//			collectSpansFromExecPlanPostOrder(subPlan, null, timestamp, spanBuilder);
//		}
//		RecordEventsSpanImpl span = (RecordEventsSpanImpl) spanBuilder.startSpan();
//		span.setStartTime(toNanos(startTimestamp));
//
//		try (Scope ws = tracer.withSpan(span)) {
//			span.addAnnotation(plan.getNodeType());
//			LOG.info("Span started with trace Id:" + span.getContext().getTraceId() + " and node " + plan.getNodeType());
//
//		} finally {
//			closeSpan(span, endTimestamp);
//		}
//	}

	private void collectSpansFromExecPlanPreOrder(ExecPlan plan, Span parentSpan, ZonedDateTime initialStartTimestamp) {
		ZonedDateTime startTimestamp = plan.getStart(initialStartTimestamp);
		ZonedDateTime endTimestamp = plan.getEnd(initialStartTimestamp);

		LOG.info("startTimestamp	: " + startTimestamp + " of node " + plan.getNodeType());
		LOG.info("endTimestamp	: " + endTimestamp + " of node " + plan.getNodeType());

		Duration duration = Duration.between(startTimestamp, endTimestamp);
		LOG.info("Duration (in ns)	: " + duration.getNano() + " of node " + plan.getNodeType());

		if (endTimestamp.isBefore(startTimestamp)) {
			LOG.info("Plan	: " + plan.getNodeType() + " is corrupt");
			return;
		}

		JaegerSpan span = (JaegerSpan) tracer.buildSpan(plan.getNodeType()).asChildOf(parentSpan).start();
		long startMicros = ChronoUnit.MICROS.between(Instant.EPOCH, startTimestamp.toInstant());
		// TimeUnit.MILLISECONDS.toMicros(startTimestamp.toInstant().toEpochMilli());
		Reflect.on(span).set("startTimeMicroseconds", startMicros);

		// LOG.info("Span started with trace Id:" + span.getContext().getTraceId() + " and node " + plan.getNodeType());

		for (ExecPlan subPlan : plan.getPlans()) {
			collectSpansFromExecPlanPreOrder(subPlan, span, initialStartTimestamp);
		}
		closeSpan(span, endTimestamp);

	}

	private void closeSpan(JaegerSpan span, ZonedDateTime endTimestamp) {
		long finishMicros = ChronoUnit.MICROS.between(Instant.EPOCH, endTimestamp.toInstant());// TimeUnit.MILLISECONDS.toMicros(endTimestamp.toInstant().toEpochMilli());
		span.finish(finishMicros);
		LocalDateTime startTimestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(TimeUnit.MICROSECONDS.toMillis(span.getStart())), ZoneId.systemDefault());
		LocalDateTime endingTimestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(TimeUnit.MICROSECONDS.toMillis(span.getStart() + span.getDuration())), ZoneId.systemDefault());

		LOG.info("Closing Span {}, starting on {} with duration {}ms ending on {} ", span, startTimestamp, span.getDuration(), endingTimestamp);
	}

	/**
	 * old style
	 *
	 * @param plan
	 * @param spanContext
	 * @param timestamp
	 */
	private void collectSpansFromExecPlan(ExecPlan plan, SpanContext spanContext, ZonedDateTime timestamp) {
//		SpanBuilder spanBuilder = tracer.spanBuilderWithRemoteParent("exec plan node " + plan.getNodeType(), spanContext).setRecordEvents(true)
//				.setSampler(Samplers.alwaysSample());
//
//		RecordEventsSpanImpl span = (RecordEventsSpanImpl) spanBuilder.startSpan();
//
//		ZonedDateTime startTimestamp = plan.getStart(timestamp);
//		ZonedDateTime endTimestamp = plan.getEnd(timestamp);
//
//		LOG.info("startTimestamp	: " + startTimestamp + " of node " + plan.getNodeType());
//		LOG.info("endTimestamp	: " + endTimestamp + " of node " + plan.getNodeType());
//
//		Duration duration = Duration.between(startTimestamp, endTimestamp);
//		LOG.info("Duration (in ns)	: " + duration.getNano() + " of node " + plan.getNodeType());
//
//		span.setStartTime(toNanos(startTimestamp));
//
//		try (Scope ws = tracer.withSpan(span)) {
//			span.addAnnotation(plan.getNodeType());
//			LOG.info("Span started with trace Id:" + span.getContext().getTraceId() + " and node " + plan.getNodeType());
//
//			// TODO: Fix span hierarchy
//			// SpanContext subContext = SpanContext.create(spanContext.getTraceId(), span.getContext().getSpanId(), span.getContext().getTraceOptions(),
//			// span.getContext().getTracestate());
//			for (ExecPlan subPlan : plan.getPlans()) {
//				collectSpansFromExecPlan(subPlan, spanContext, timestamp);
//			}
//		} finally {
//			closeSpan(span, endTimestamp);
//		}

	}

	private void createLockSpan(String message, ZonedDateTime timestamp, Integer pid, String lockType) {

		JaegerSpanContext spanContext = traceContextLoader.loadSpanContext(message);

		Map<String, TimestampTuple> relationTsTupleMap = gatheredTimeSpans.remove(pid);
		if (relationTsTupleMap != null) {

			// pidTraceMapping.put(pid, spanContext.getTraceId()); // ???

			Optional<Entry<String, TimestampTuple>> searchresult = relationTsTupleMap.entrySet().stream().findFirst();

			if (searchresult.isPresent()) {
				TimestampTuple timestampTuple = relationTsTupleMap.remove(searchresult.get().getKey());
				if (timestampTuple.getStartTs() != null && timestampTuple.getEndTs() != null) {
					ZonedDateTime startTs = timestampTuple.getStartTs().minus(LOCK_OFFSET_MILLISEC, ChronoUnit.MILLIS);
					JaegerSpan span = startSpan(spanContext, startTs, "wait for " + lockType);
					span.setTag("sql", message);

					ZonedDateTime endTs = timestampTuple.getEndTs();
					closeSpan(span, endTs);

				} else {
					LOG.error("no lock ending found for message {} of process {}", message, pid);
				}

			}

		}
	}

	public static JaegerTracer initTracer(String service) {
		SamplerConfiguration samplerConfig = SamplerConfiguration.fromEnv().withType("const").withParam(1);
		ReporterConfiguration reporterConfig = ReporterConfiguration.fromEnv().withLogSpans(true);
		Configuration config = new Configuration(service).withSampler(samplerConfig).withReporter(reporterConfig);
		return config.getTracer();
	}
}
