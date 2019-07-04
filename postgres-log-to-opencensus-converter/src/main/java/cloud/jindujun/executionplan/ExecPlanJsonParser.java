package cloud.jindujun.executionplan;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ExecPlanJsonParser {

	private static ExecPlanJsonParser instance;
	private static ObjectMapper objectMapper = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


	private ExecPlanJsonParser() {

	}

	/**
	 * @return Singleton Object
	 */
	public static synchronized ExecPlanJsonParser getInstance() {
		if (ExecPlanJsonParser.instance == null) {
			ExecPlanJsonParser.instance = new ExecPlanJsonParser();
		}
		return ExecPlanJsonParser.instance;
	}

	
	public PlanEnvelop parse(String json) throws JsonParseException, JsonMappingException, IOException {
		return objectMapper.readValue(json, PlanEnvelop.class);
	}
}
