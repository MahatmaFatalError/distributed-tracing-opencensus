package cloud.jindujun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opencensus.trace.SpanContext;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.propagation.SpanContextParseException;
import io.opencensus.trace.propagation.TextFormat;

public class TraceContextLoader {

	private static Logger LOG = LoggerFactory.getLogger(TraceContextLoader.class);

	private static final Tracer tracer = Tracing.getTracer();

	private static final String X_B3_TRACE_ID = "X-B3-TraceId";
	private static final String X_B3_SPAN_ID = "X-B3-SpanId";
	private static final TextFormat textFormat = Tracing.getPropagationComponent().getB3Format();
	public static final TextFormat.Getter<String> getter = new TextFormat.Getter<String>() {

		/**
		 * Returns the first value of the given propagation {@code key} or returns
		 * {@code null}.
		 *
		 * @param carrier carrier of propagation fields, here the String from postgres
		 *                log in Format: --
		 *                SpanContext{traceId=TraceId{traceId=c0bdde80a21a5802de1adb411946eb17},
		 *                spanId=SpanId{spanId=b275e297be6a303c},
		 *                traceOptions=TraceOptions{sampled=true}}
		 * @param key     the key of the field.
		 * @return the first value of the given propagation {@code key} or returns
		 *         {@code null}.
		 * @since 0.11
		 */
		@Override
		public String get(String carrier, String key) {

			if (X_B3_TRACE_ID.equals(key)) {
				String traceIdIdentifier = "traceId=TraceId{traceId=";
				int indexOfTraceId = carrier.indexOf(traceIdIdentifier);
				String traceId = carrier.substring(indexOfTraceId + traceIdIdentifier.length(),
						indexOfTraceId + 32 + traceIdIdentifier.length());
				return traceId;
			} else if (X_B3_SPAN_ID.equals(key)) {
				String spanIdIdentifier = "=SpanId{spanId=";
				int indexOSpanId = carrier.indexOf(spanIdIdentifier);
				String spanId = carrier.substring(indexOSpanId + spanIdIdentifier.length(),
						indexOSpanId + 16 + spanIdIdentifier.length());
				return spanId;
			}
			return null;
		}
	};

	public SpanContext loadSpanContext(String carrier) throws SpanContextParseException {
		return textFormat.extract(carrier, getter);

	}
}
