package com.hello.config;

import java.lang.reflect.Method;

import javax.sql.DataSource;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import io.opencensus.common.Scope;
import io.opencensus.trace.Span;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.samplers.Samplers;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import net.ttddyy.dsproxy.transform.QueryTransformer;
import net.ttddyy.dsproxy.transform.TransformInfo;

@Component
public class DatasourceProxyBeanPostProcessor implements BeanPostProcessor {

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (bean instanceof DataSource && !(bean instanceof ProxyDataSource)) {
			// Instead of directly returning a less specific datasource bean
			// (e.g.: HikariDataSource -> DataSource), return a proxy object.
			// See following links for why:
			// https://stackoverflow.com/questions/44237787/how-to-use-user-defined-database-proxy-in-datajpatest
			// https://gitter.im/spring-projects/spring-boot?at=5983602d2723db8d5e70a904
			// http://blog.arnoldgalovics.com/2017/06/26/configuring-a-datasource-proxy-in-spring-boot/
			final ProxyFactory factory = new ProxyFactory(bean);
			factory.setProxyTargetClass(true);
			factory.addAdvice(new ProxyDataSourceInterceptor((DataSource) bean));
			return factory.getProxy();
		}
		return bean;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		return bean;
	}

	private static class ProxyDataSourceInterceptor implements MethodInterceptor {
		private final DataSource dataSource;

		public ProxyDataSourceInterceptor(final DataSource actualDataSource) {

			this.dataSource = ProxyDataSourceBuilder.create(actualDataSource)
					.name("TracingAwareDS")
					.queryTransformer(transformInfo -> {
						String query = transformInfo.getQuery();
						SpanContext context = Tracing.getTracer().getCurrentSpan().getContext();
						return String.format("-- %s \n %s", context.toString(), query);
					})
					.build();
		}

		@Override
		public Object invoke(final MethodInvocation invocation) throws Throwable {
			final Method proxyMethod = ReflectionUtils.findMethod(this.dataSource.getClass(), invocation.getMethod().getName());
			if (proxyMethod != null) {
				Span span = Tracing.getTracer().spanBuilder("acquiring db connection from ProxyDataSourceInterceptor").setRecordEvents(true).setSampler(Samplers.alwaysSample()).startSpan();
		     	 try (Scope ws = Tracing.getTracer().withSpan(span)) {
		     		return proxyMethod.invoke(this.dataSource, invocation.getArguments());
		     	 } finally {
		 			span.end();
		 		 }


			}
			return invocation.proceed();
		}
	}

	/**
	 * propagates tracing context as sql comment
	 */
	private static class TracingQueryTransformer implements QueryTransformer {

		@Override
		public String transformQuery(TransformInfo transformInfo) {
			String query = transformInfo.getQuery();
			SpanContext context = Tracing.getTracer().getCurrentSpan().getContext();
			return String.format("-- %s \n %s", context.toString(), query);
		}
	}

}
