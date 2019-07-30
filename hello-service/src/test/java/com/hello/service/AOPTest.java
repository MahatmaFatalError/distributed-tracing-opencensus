package com.hello.service;

import static org.junit.Assert.assertNotNull;

import org.aspectj.weaver.patterns.PatternParser;
import org.aspectj.weaver.patterns.Pointcut;
import org.junit.Test;

public class AOPTest {


	@Test
	public void test() {
		PatternParser pp = new PatternParser("execution(static java.sql.Connection org.springframework.jdbc.datasource.DataSourceUtils.getConnection(..))");
		Pointcut pointcut = pp.parsePointcut();
		assertNotNull(pointcut);

		//pointcut.resolve(scope)
	}
}
