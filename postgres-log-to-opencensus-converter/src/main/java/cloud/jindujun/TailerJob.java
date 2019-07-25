package cloud.jindujun;

import java.util.List;
import java.util.Map;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

//@Component
@DisallowConcurrentExecution
public class TailerJob extends QuartzJobBean {

	public TailerJob() {
		logger.info("new TailerJob instance created");
	}

	@Autowired
	JdbcTemplate template;


	@Autowired
	private RowHandler rowHandler;

	private final Logger logger = LoggerFactory.getLogger(getClass());


	@Override
	protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
		logger.info("Job ** {} ** fired @ {}", context.getJobDetail().getKey().getName(), context.getFireTime());

		List<Map<String, Object>> result = template.queryForList(
				"select log_time, process_id, session_id, command_tag, virtual_transaction_id, message, detail, application_name from pglog_last_min order by log_time");

		for (Map<String, Object> row : result) {
			rowHandler.handleRow(row);
		}

		logger.info("Next job scheduled @ {}", context.getNextFireTime());

	}

}
