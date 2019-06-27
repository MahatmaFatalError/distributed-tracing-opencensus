package cloud.jindujun;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.opencensus.trace.SpanContext;
import io.opencensus.trace.propagation.SpanContextParseException;

public class TraceContextLoaderTest {

	@Test
	public void test() throws SpanContextParseException {
		String message = "execute <unnamed>: -- SpanContext{traceId=TraceId{traceId=c0bdde80a21a5802de1adb411946eb17}, spanId=SpanId{spanId=b275e297be6a303c}, traceOptions=TraceOptions{sampled=true}} \n"
				+ " Select name from helloworld";

		SpanContext spanContext = new TraceContextLoader().loadSpanContext(message);

		assertEquals("TraceId{traceId=c0bdde80a21a5802de1adb411946eb17}", spanContext.getTraceId().toString());

		// TODO: mit spy den internen TraceContext holen und asserten
	}

}
