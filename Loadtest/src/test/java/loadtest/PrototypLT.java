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
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.loadtest4j.LoadTester;
import org.loadtest4j.Request;
import org.loadtest4j.Result;
import org.loadtest4j.drivers.gatling.GatlingBuilder;

@Execution(ExecutionMode.CONCURRENT)
public class PrototypLT {

	private static final LoadTester loadTester = GatlingBuilder.withUrl("http://localhost:9966/petclinic/api")
            .withDuration(Duration.ofSeconds(10))
            .withUsersPerSecond(2)
            .build();



	/**
	 * Heavy IO
	 */
    @Test
    @Execution(ExecutionMode.CONCURRENT)
    public void shouldFindAllPets() {
        List<Request> requests = Arrays.asList(Request.get("/pets")
                                                .withHeader("Accept", "application/json"));

        Result result = loadTester.run(requests);

        assertThat(result.getResponseTime().getPercentile(90))
            .isLessThanOrEqualTo(Duration.ofMillis(1500));
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    public void lockPets() throws InterruptedException, ClientProtocolException, IOException {

    	int iterations = 3;
    	int pauseMillis = 2500;

    	for (int i = 0; i < iterations; i++) {
			Thread.sleep(pauseMillis);
			HttpClient client = HttpClientBuilder.create().build();
			HttpGet request = new HttpGet("http://localhost:9966/petclinic/api/pets/lock?duration=1500"); //new URIBuilder().setParameter("duration", "1500").build()
			HttpResponse response = client.execute(request);
		}
    }

}
