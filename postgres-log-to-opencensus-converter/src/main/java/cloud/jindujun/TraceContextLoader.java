package cloud.jindujun;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jaegertracing.internal.JaegerSpanContext;
import io.jaegertracing.internal.utils.Utils;

public class TraceContextLoader {

	private static Logger LOG = LoggerFactory.getLogger(TraceContextLoader.class);

	private static final String X_B3_TRACE_ID = "X-B3-TraceId";
	private static final String X_B3_SPAN_ID = "X-B3-SpanId";

	private String extractSpanId(String carrier) {
		String spanIdIdentifier = "=SpanId{spanId=";
		int indexOSpanId = carrier.indexOf(spanIdIdentifier);
		String spanId = carrier.substring(indexOSpanId + spanIdIdentifier.length(), indexOSpanId + 16 + spanIdIdentifier.length());
		return spanId;
	}

	private String extraceTraceId(String carrier) {
		String traceIdIdentifier = "traceId=TraceId{traceId=";
		int indexOfTraceId = carrier.indexOf(traceIdIdentifier);
		String traceId = carrier.substring(indexOfTraceId + traceIdIdentifier.length(), indexOfTraceId + 32 + traceIdIdentifier.length());
		return traceId;
	}


	public JaegerSpanContext loadSpanContext(String carrier)  {


		return extract(extraceTraceId(carrier), extractSpanId(carrier));
	}


	public JaegerSpanContext extract(String traceId, String parenSpanId) {
		Long traceIdLow = null;
		Long traceIdHigh = 0L; // It's enough to check for a null low trace id
		Long spanId = Utils.uniqueId();
		Long parentId = 0L; // Conventionally, parent id == 0 means the root span
		byte flags = 1; // sampled
		Map<String, String> baggage = null;
		traceIdLow = HexCodec.lowerHexToUnsignedLong(traceId);
		traceIdHigh = HexCodec.higherHexToUnsignedLong(traceId);

		spanId = HexCodec.lowerHexToUnsignedLong(parenSpanId);

		JaegerSpanContext spanContext = new JaegerSpanContext(traceIdHigh, traceIdLow, spanId, parentId, flags);
		if (baggage != null) {
			spanContext = spanContext.withBaggage(baggage);
		}
		return spanContext;
	}
}
