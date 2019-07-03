package cloud.jindujun.executionplan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanWrapper {

	@JsonProperty("Query Text")
	private String queryText;

	@JsonProperty("Plan")
	private ExecPlan plan;

	public String getQueryText() {
		return queryText;
	}

	public void setQueryText(String queryText) {
		this.queryText = queryText;
	}

	public ExecPlan getPlan() {
		return plan;
	}

	public void setPlan(ExecPlan plan) {
		this.plan = plan;
	}

}
