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
 * 30 sec call all 500 vets (which is cached) and inject 10 times a cache eviction
 *
 */
@Execution(ExecutionMode.CONCURRENT)
public class CacheMissLT {

	private static final LoadTester loadTester = GatlingBuilder.withUrl("http://localhost:8888").withDuration(Duration.ofSeconds(30)).withUsersPerSecond(5).build();

	@Test
	@Execution(ExecutionMode.CONCURRENT)
	public void shouldFindVets() {
		List<Request> requests = Arrays.asList(Request.get("/hellocache").withHeader("Accept", "application/json"));

		Result result = loadTester.run(requests);

		assertThat(result.getResponseTime().getPercentile(90)).isLessThanOrEqualTo(Duration.ofMillis(1500));
		assertThat(result.getPercentOk()).isGreaterThan(99.9);
	}

	@Test
	@Execution(ExecutionMode.CONCURRENT)
	public void clearVetsCache() throws InterruptedException, ClientProtocolException, IOException {

		int iterations = 5;
		int pauseMillis = 9000;

		for (int i = 0; i < iterations; i++) {
    		long pause =  Math.round(pauseMillis * Math.random());

			Thread.sleep(pause);
			HttpClient client = HttpClientBuilder.create().build();
			HttpGet request = new HttpGet("http://localhost:8888/clearcache");
//			HttpPost request = new HttpPost("http://localhost:8888/clearcache");
//			request.addHeader("content-type", "application/json");
//			StringEntity params = new StringEntity("{\"firstName\":\"myname\",\"lastName\":\"mylastname\"} ");
//			request.setEntity(params);
			HttpResponse response = client.execute(request);
		}
	}

}
