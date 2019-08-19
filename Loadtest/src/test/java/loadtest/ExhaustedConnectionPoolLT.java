package loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.Disabled;
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
public class ExhaustedConnectionPoolLT {

	private static final LoadTester loadTester = GatlingBuilder.withUrl("http://localhost:8888").withDuration(Duration.ofSeconds(60)).withUsersPerSecond(5).build();

	@Test
	public void connectionPoolLoadTest() {
		List<Request> requests = Arrays.asList(Request.get("/sleepquery").withQueryParam("duration", "300").withHeader("Accept", "application/json"));

		Result result = loadTester.run(requests);

		assertThat(result.getPercentOk()).isGreaterThan(0.999);
		assertThat(result.getResponseTime().getPercentile(90)).isLessThanOrEqualTo(Duration.ofMillis(600));
	}



}
