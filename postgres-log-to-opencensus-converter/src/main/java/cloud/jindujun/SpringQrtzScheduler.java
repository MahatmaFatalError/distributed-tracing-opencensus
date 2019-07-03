package cloud.jindujun;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

import javax.annotation.PostConstruct;

import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringQrtzScheduler {
	private final Logger logger = LoggerFactory.getLogger(getClass());
	private static final int FREQUENCY_60_SECONDS = 60;

	@Autowired
	private ApplicationContext applicationContext;

	@PostConstruct
	public void init() {
		logger.info("Hello world from Quartz...");
	}


	@Bean
	public JobDetail jobDetail() {

		return newJob().ofType(TailerJob.class).storeDurably().withIdentity(JobKey.jobKey("Qrtz_Job_Detail"))
				.withDescription("Invoke Postgres Log Job service...").build();
	}

	@Bean
	public Trigger trigger(JobDetail job) {
		
		logger.info("Configuring trigger to fire every {} seconds", FREQUENCY_60_SECONDS);

		return newTrigger().forJob(job).withIdentity(TriggerKey.triggerKey("Qrtz_Trigger"))
				.withDescription("Minute trigger")
				.withSchedule(simpleSchedule().withIntervalInSeconds(FREQUENCY_60_SECONDS).repeatForever()).build();
	}
}
