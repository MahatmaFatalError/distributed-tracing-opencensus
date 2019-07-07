package cloud.jindujun;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.junit.Test;
import org.springframework.util.ResourceUtils;

public class RowHandlerTest {

	private RowHandler rowHandler = new RowHandler();

	private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z", Locale.ENGLISH);

	@Test
	public void testLock() {
		rowHandler.convert(
				"process 86673 still waiting for AccessShareLock on relation 16387 of database 13382 after 1000.432 ms",
				ZonedDateTime.parse("2019-06-27 15:43:52.746 CEST", formatter), 86673);

		rowHandler.convert(
				"process 86673 acquired AccessShareLock on relation 16387 of database 13382 after 7139.512 ms",
				ZonedDateTime.parse("2019-06-27 15:43:58.886 CEST", formatter), 86673);

		rowHandler.convert(
				"execute <unnamed>: -- SpanContext{traceId=TraceId{traceId=a656b0bbb75b88f6d3ec915d123692d3}, spanId=SpanId{spanId=eb6c14c5a3161a22}, traceOptions=TraceOptions{sampled=true}}\n"
						+ " Select name from helloworld",
				ZonedDateTime.parse("2019-06-27 15:43:58.886 CEST", formatter), 86673);
	}

	@Test // TODO
	public void testExecPlan() throws FileNotFoundException, IOException {

		File file = ResourceUtils.getFile("classpath:logline.csv");
		String content = new String(Files.readAllBytes(file.toPath()));

		rowHandler.convert(content, ZonedDateTime.parse("2019-06-26 18:18:12.050 CEST", formatter), 95727);
	}

}
