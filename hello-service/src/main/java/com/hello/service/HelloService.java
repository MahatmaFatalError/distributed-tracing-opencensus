package com.hello.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;

@Service
public class HelloService {

	@Autowired
	@Lazy
	JdbcTemplate template;

	private static final Logger LOG = LoggerFactory.getLogger(HelloService.class);
	private static final Tracer tracer = Tracing.getTracer();


	public String printHello() {
		String helloStr = "Hello from ";
		// useless long running query
		List<Map<String, Object>> result = template.queryForList(
				"select d1.name, STRING_AGG(d1.SHORT_DESCRIPTION, ',' order by age(d1.LAST_UPDATED, d1.CREATED)) from DOCUMENT_TEMPLATE d1 inner join DOCUMENT_TEMPLATE d2 on d1.id = d2.id where d1.author = ? OR d1.author = ? group by d1.name having count(*) > ? order by d1.name",
				"Jimmy", "Jessica" , 100);

		String name = (String) result.get(0).get("name");
		helloStr += name;
		return helloStr;
	}

}
