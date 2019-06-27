package com.hello.spring.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.SqlProvider;
import org.springframework.jdbc.core.StatementCallback;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import io.opencensus.common.Scope;
import io.opencensus.trace.Span;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.samplers.Samplers;

public class TracingWrappedJdbcTemplate extends org.springframework.jdbc.core.JdbcTemplate {

	private static final Tracer tracer = Tracing.getTracer();

	/**
	 * Construct a new JdbcTemplate for bean usage.
	 * <p>
	 * Note: The DataSource has to be set before using the instance.
	 *
	 * @see #setDataSource
	 */
	public TracingWrappedJdbcTemplate() {
	}

	/**
	 * Construct a new JdbcTemplate, given a DataSource to obtain connections from.
	 * <p>
	 * Note: This will not trigger initialization of the exception translator.
	 *
	 * @param dataSource the JDBC DataSource to obtain connections from
	 */
	public TracingWrappedJdbcTemplate(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	@Nullable
	public <T> T execute(StatementCallback<T> action) throws DataAccessException {
		Assert.notNull(action, "Callback object must not be null");

		// init trace context
		Span span = tracer.spanBuilder("acquiring db connection from pool").setRecordEvents(true)
				.setSampler(Samplers.alwaysSample()).startSpan();

		Connection con;
		try (Scope ws = tracer.withSpan(span)) {
			con = DataSourceUtils.getConnection(obtainDataSource());
		} finally {
			span.end();
		}

		Span span2 = tracer.spanBuilder("execute statement").setRecordEvents(true).setSampler(Samplers.alwaysSample())
				.startSpan();
		Statement stmt = null;
		try (Scope ws = tracer.withSpan(span2)) {
			stmt = con.createStatement();
			applyStatementSettings(stmt);
			span2.addAnnotation("Statement:" + stmt.toString());
			T result = action.doInStatement(stmt);
			handleWarnings(stmt);
			return result;
		} catch (SQLException ex) {
			// Release Connection early, to avoid potential connection pool deadlock
			// in the case when the exception translator hasn't been initialized yet.
			String sql = getSql(action);
			JdbcUtils.closeStatement(stmt);
			stmt = null;
			DataSourceUtils.releaseConnection(con, getDataSource());
			con = null;
			throw translateException("StatementCallback", sql, ex);
		} finally {
			JdbcUtils.closeStatement(stmt);
			DataSourceUtils.releaseConnection(con, getDataSource());
			span2.end();
		}
	}

	/**
	 * Determine SQL from potential provider object.
	 *
	 * @param sqlProvider object that's potentially a SqlProvider
	 * @return the SQL string, or {@code null}
	 * @see SqlProvider
	 */
	@Nullable
	private static String getSql(Object sqlProvider) {
		if (sqlProvider instanceof SqlProvider) {
			return ((SqlProvider) sqlProvider).getSql();
		} else {
			return null;
		}
	}

	@Override
	@Nullable
	public <T> T query(final String sql, final ResultSetExtractor<T> rse) throws DataAccessException {
		Assert.notNull(sql, "SQL must not be null");
		Assert.notNull(rse, "ResultSetExtractor must not be null");
		if (logger.isDebugEnabled()) {
			logger.debug("Executing SQL query [" + sql + "]");
		}

		SpanContext context = tracer.getCurrentSpan().getContext();
		String traceSQL = String.format("-- %s \n %s", context.toString(), sql);

		class QueryStatementCallback implements StatementCallback<T>, SqlProvider {
			@Override
			@Nullable
			public T doInStatement(Statement stmt) throws SQLException {
				ResultSet rs = null;
				try {
					rs = stmt.executeQuery(traceSQL);
					return rse.extractData(rs);
				} finally {
					JdbcUtils.closeResultSet(rs);
				}
			}

			@Override
			public String getSql() {
				return sql;
			}
		}

		return execute(new QueryStatementCallback());
	}
}
