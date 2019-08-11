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

	private RowHandler rowHandler = new RowHandler(true,true,true);

	private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z", Locale.ENGLISH);

	@Test
	public void testLock() {
		rowHandler.convert(
				"process 86673 still waiting for AccessShareLock on relation 16387 of database 13382 after 1000.432 ms",
				ZonedDateTime.parse("2019-06-27 15:43:52.746 CEST", formatter), 86673, "", "-- SpanContext{traceId=TraceId{traceId=a656b0bbb75b88f6d3ec915d123692d3}, spanId=SpanId{spanId=eb6c14c5a3161a22}, traceOptions=TraceOptions{sampled=true}}\n"
						+ " Select name from helloworld");

		rowHandler.convert(
				"process 86673 acquired AccessShareLock on relation 16387 of database 13382 after 7139.512 ms",
				ZonedDateTime.parse("2019-06-27 15:43:58.886 CEST", formatter), 86673, "","-- SpanContext{traceId=TraceId{traceId=a656b0bbb75b88f6d3ec915d123692d3}, spanId=SpanId{spanId=eb6c14c5a3161a22}, traceOptions=TraceOptions{sampled=true}}\n"
						+ " Select name from helloworld");

		rowHandler.convert(
				"execute <unnamed>: -- SpanContext{traceId=TraceId{traceId=a656b0bbb75b88f6d3ec915d123692d3}, spanId=SpanId{spanId=eb6c14c5a3161a22}, traceOptions=TraceOptions{sampled=true}}\n"
						+ " Select name from helloworld",
				ZonedDateTime.parse("2019-06-27 15:43:58.886 CEST", formatter), 86673, "", null);
	}

	@Test // TODO
	public void testExecPlan() throws FileNotFoundException, IOException {

		File file = ResourceUtils.getFile("classpath:logline.csv");
		String content = new String(Files.readAllBytes(file.toPath()));

		rowHandler.convert(
				"execute <unnamed>: -- SpanContext{traceId=TraceId{traceId=09125e63a222286ecc0f90ae8180a85d}, spanId=SpanId{spanId=8e9365c639a14445}, traceOptions=TraceOptions{sampled=true}} \n"
						+ " select d1.name, STRING_AGG(d1.SHORT_DESCRIPTION, ',' order by age(d1.LAST_UPDATED, d1.CREATED)) from DOCUMENT_TEMPLATE d1 inner join DOCUMENT_TEMPLATE d2 on d1.id = d2.id where d1.author = $1 OR d1.author = $2 group by d1.name having count(*) > $3 order by d1.name",
				ZonedDateTime.parse("2019-07-11 15:32:24.000 CEST", formatter), 22911, "SELECT", null);

		//2019-07-11 15:32:26.897 CEST,"postgres","postgres",22911,"127.0.0.1:55888",5d273959.597f,11,"SELECT",2019-07-11 15:27:53 CEST,5/4019,0,LOG,00000,"
		rowHandler.convert(content, ZonedDateTime.parse("2019-07-11 15:32:26.897 CEST", formatter), 22911, "SELECT", null);
	}

}
