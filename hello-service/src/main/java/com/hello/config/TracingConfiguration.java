package com.hello.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hello.spring.jdbc.TracingWrappedJdbcTemplate;

@Configuration
public class TracingConfiguration {

	@Bean
	public FilterRegistrationBean tracingFilter() {
		FilterRegistrationBean registrationBean = new FilterRegistrationBean();
		registrationBean.setFilter(new TracingFilter());
		registrationBean.addUrlPatterns("/*");
		return registrationBean;
	}

	@Bean
	public JdbcTemplate getJdbcTemplate(DataSource ds) {
		return new TracingWrappedJdbcTemplate(ds);
	}


}
