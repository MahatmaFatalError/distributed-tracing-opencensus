package cloud.jindujun;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @author d072521
 *
 */
@SpringBootApplication
public class PostgresLogToOpencensusConverterApplication implements CommandLineRunner {

	@Autowired
	JdbcTemplate template;

	private RowHandler rowHandler = new RowHandler();

	private static Logger LOG = LoggerFactory.getLogger(PostgresLogToOpencensusConverterApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(PostgresLogToOpencensusConverterApplication.class, args);

	}

	@Override
	public void run(String... args) throws Exception {
		LOG.info("EXECUTING : command line runner");

		List<Map<String, Object>> result = template.queryForList(
				"select log_time, process_id, session_id, virtual_transaction_id, message, detail, application_name from pglog_last_min");

		for (Map<String, Object> row : result) {
			rowHandler.handleRow(row);
		}

	}

}
