package cloud.jindujun.executionplan;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.util.ResourceUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ExecPlanTest {

	@Before
	public void setup() {

	}

	@Test
	public void test() throws IOException {
		File file = ResourceUtils.getFile("classpath:plan.json");
		String content = new String(Files.readAllBytes(file.toPath()));

		PlanWrapper planWrapper = ExecPlanJsonParser.parse(content);

		System.out.println(planWrapper);
		

	}

}
