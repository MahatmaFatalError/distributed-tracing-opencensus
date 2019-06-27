package cloud.jindujun;

import org.junit.Test;

public class RowHandlerTest {

	private RowHandler rowHandler = new RowHandler();

	@Test
	public void test() {
		rowHandler.convert(
				"process 86673 still waiting for AccessShareLock on relation 16387 of database 13382 after 1000.432 ms",
				"2019-06-27 15:43:52.746 CEST", "86673");

		rowHandler.convert(
				"process 86673 acquired AccessShareLock on relation 16387 of database 13382 after 7139.512 ms",
				"2019-06-27 15:43:58.886 CEST", "86673");

		rowHandler.convert(
				"execute <unnamed>: -- SpanContext{traceId=TraceId{traceId=a656b0bbb75b88f6d3ec915d123692d3}, spanId=SpanId{spanId=eb6c14c5a3161a22}, traceOptions=TraceOptions{sampled=true}}\n"
						+ " Select name from helloworld",
				"2019-06-27 15:43:58.886 CEST", "86673");
	}

}
