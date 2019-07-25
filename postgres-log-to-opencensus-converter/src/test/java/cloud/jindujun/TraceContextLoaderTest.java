package cloud.jindujun;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.jaegertracing.internal.JaegerSpanContext;

public class TraceContextLoaderTest {

	@Test
	public void test() {
		String message = "execute <unnamed>: -- SpanContext{traceId=TraceId{traceId=c0bdde80a21a5802de1adb411946eb17}, spanId=SpanId{spanId=b275e297be6a303c}, traceOptions=TraceOptions{sampled=true}} \n"
				+ " Select name from helloworld";

		JaegerSpanContext spanContext = new TraceContextLoader().loadSpanContext(message);

		assertEquals("c0bdde80a21a5802de1adb411946eb17", spanContext.getTraceId());
		assertEquals("b275e297be6a303c", spanContext.getSpanId());

		// TODO: mit spy den internen TraceContext holen und asserten
	}

}
