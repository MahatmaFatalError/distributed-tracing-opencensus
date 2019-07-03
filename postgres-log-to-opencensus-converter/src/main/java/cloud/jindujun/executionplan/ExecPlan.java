package cloud.jindujun.executionplan;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * DTO for PostgreSQL exec plans in json
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
//@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonRootName("Plans")
public class ExecPlan implements Comparable<ExecPlan>{


	@JsonProperty("Node Type")
	private String nodeType;
	
	@JsonProperty("Parent Relationship")
	private String parentRelationship;
	
	@JsonProperty("Startup Cost")
	private double startupCost;
	
	@JsonProperty("Total Cost")
	private double totalCost;
    
	@JsonProperty("Actual Startup Time")
	private Double actualStartupTime;
	
	@JsonProperty("Actual Total Time")
	private Double actualTotalTime;
	
	@JsonProperty("Plan Rows")
	private long planRows;
	
	@JsonProperty("Actual Rows")
	private long actualRows;
	
	@JsonProperty("Loops")
	private long loops;
    
	@JsonProperty("Plans")
	private List<ExecPlan> plans = new ArrayList<>();
	
	public String getNodeType() {
		return nodeType;
	}

	public void setNodeType(String nodeType) {
		this.nodeType = nodeType;
	}

	public String getParentRelationship() {
		return parentRelationship;
	}

	public void setParentRelationship(String parentRelationship) {
		this.parentRelationship = parentRelationship;
	}

	public double getStartupCost() {
		return startupCost;
	}

	public void setStartupCost(double startupCost) {
		this.startupCost = startupCost;
	}

	public double getTotalCost() {
		return totalCost;
	}

	public void setTotalCost(double totalCost) {
		this.totalCost = totalCost;
	}

	public double getActualStartupTime() {
		return actualStartupTime;
	}

	public void setActualStartupTime(double actualStartupTime) {
		this.actualStartupTime = actualStartupTime;
	}

	public double getActualTotalTime() {
		return actualTotalTime;
	}

	public void setActualTotalTime(double actualTotalTime) {
		this.actualTotalTime = actualTotalTime;
	}

	public long getPlanRows() {
		return planRows;
	}

	public void setPlanRows(long planRows) {
		this.planRows = planRows;
	}

	public long getActualRows() {
		return actualRows;
	}

	public void setActualRows(long actualRows) {
		this.actualRows = actualRows;
	}

	public long getLoops() {
		return loops;
	}

	public void setLoops(long loops) {
		this.loops = loops;
	}

	public List<ExecPlan> getPlans() {
		return plans;
	}

	public void setPlans(List<ExecPlan> plans) {
		this.plans = plans;
	}
	
	public void addPlan(ExecPlan plan) {
		this.plans.add(plan);
	}
	
	public ExecPlan getPlan(int index) {
		return plans.get(index);
	}

	/**
	 * @return negative if this plan happened before the provided plan
	 */
	@Override
	public int compareTo(ExecPlan o) {
		// TODO Auto-generated method stub
		return this.actualStartupTime.compareTo(o.getActualStartupTime());
	}
	
	public ZonedDateTime getStart(ZonedDateTime logTimestamp) {		
		return logTimestamp.plusNanos(TimeUnit.MICROSECONDS.toNanos(actualStartupTime.longValue()));
	}
	
	public ZonedDateTime getEnd(ZonedDateTime logTimestamp) {
		return logTimestamp.plusNanos(TimeUnit.MICROSECONDS.toNanos(actualTotalTime.longValue()));
	}
}
