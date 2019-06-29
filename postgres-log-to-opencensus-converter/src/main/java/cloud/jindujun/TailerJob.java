package cloud.jindujun;

import java.util.List;
import java.util.Map;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TailerJob implements Job {

	@Autowired
	JdbcTemplate template;

	private RowHandler rowHandler = new RowHandler();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		logger.info("Job ** {} ** fired @ {}", context.getJobDetail().getKey().getName(), context.getFireTime());

		List<Map<String, Object>> result = template.queryForList(
				"select log_time, process_id, session_id, virtual_transaction_id, message, detail, application_name from pglog_last_min");

		for (Map<String, Object> row : result) {
			rowHandler.handleRow(row);
		}

		logger.info("Next job scheduled @ {}", context.getNextFireTime());

	}

}
