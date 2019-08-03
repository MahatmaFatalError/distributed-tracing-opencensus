package cloud.jindujun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author d072521
 *
 */
@SpringBootApplication
//@EnableScheduling
public class PostgresLogToOpenTracingConverterApplication implements CommandLineRunner {

	private static Logger LOG = LoggerFactory.getLogger(PostgresLogToOpenTracingConverterApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(PostgresLogToOpenTracingConverterApplication.class, args);

	}

	@Override
	public void run(String... args) throws Exception {
		LOG.info("EXECUTING : command line runner");

	}

}
