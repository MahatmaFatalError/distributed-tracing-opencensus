package loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.loadtest4j.LoadTester;
import org.loadtest4j.Request;
import org.loadtest4j.Result;
import org.loadtest4j.drivers.gatling.GatlingBuilder;

/**
 *
 * DONT FORGET TO SET UP A SMALL CONNECTION POOL OF THE SUT
 *
 */
@Execution(ExecutionMode.CONCURRENT)
public class ExhaustedConnectionPoolLT {

	private static final LoadTester loadTester = GatlingBuilder.withUrl("http://localhost:8080/greetings").withDuration(Duration.ofSeconds(50)).withUsersPerSecond(5).build();

	@Test
	@Execution(ExecutionMode.CONCURRENT)
	public void connectionPoolLoadTest() {
		List<Request> requests = Arrays.asList(Request.get("/sleepquery").withQueryParam("duration", "300").withHeader("Accept", "application/json"));

		Result result = loadTester.run(requests);

		assertThat(result.getPercentOk()).isGreaterThan(0.999);
		assertThat(result.getResponseTime().getPercentile(90)).isLessThanOrEqualTo(Duration.ofMillis(600));
	}

	@Test
	@Execution(ExecutionMode.CONCURRENT)
	public void clearVetsCache() throws InterruptedException, ClientProtocolException, IOException {

		int iterations = 10;
		int pauseMillis = 5000;

		for (int i = 0; i < iterations; i++) {
    		long pause =  Math.round(pauseMillis * Math.random());

			Thread.sleep(pause);
			HttpClient client = HttpClientBuilder.create().build();
			HttpGet request = new HttpGet("http://localhost:8888/sleepquery?duration="+600);

			HttpResponse response = client.execute(request);
		}
	}


}
