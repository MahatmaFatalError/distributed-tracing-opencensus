package com.hello.spring.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ParameterDisposer;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.SqlProvider;
import org.springframework.jdbc.core.StatementCallback;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import io.opencensus.common.Scope;
import io.opencensus.trace.Span;
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

	// -------------------------------------------------------------------------
	// Methods dealing with prepared statements
	// -------------------------------------------------------------------------

	@Override
	@Nullable
	public <T> T execute(PreparedStatementCreator psc, PreparedStatementCallback<T> action) throws DataAccessException {

		Assert.notNull(psc, "PreparedStatementCreator must not be null");
		Assert.notNull(action, "Callback object must not be null");
		if (logger.isDebugEnabled()) {
			String sql = getSql(psc);
			logger.debug("Executing prepared SQL statement" + (sql != null ? " [" + sql + "]" : ""));
		}

		Connection con = getTracedConnection();

		Span queryExecSpan = tracer.spanBuilder("execute statement").setRecordEvents(true).setSampler(Samplers.alwaysSample()).startSpan();

		PreparedStatement ps = null;
		try (Scope ws = tracer.withSpan(queryExecSpan)) {
			ps = psc.createPreparedStatement(con);
			applyStatementSettings(ps);
			queryExecSpan.addAnnotation("Statement:" + ps.toString());
			T result = action.doInPreparedStatement(ps);
			handleWarnings(ps);
			return result;
		} catch (SQLException ex) {
			// Release Connection early, to avoid potential connection pool deadlock
			// in the case when the exception translator hasn't been initialized yet.
			if (psc instanceof ParameterDisposer) {
				((ParameterDisposer) psc).cleanupParameters();
			}
			String sql = getSql(psc);
			JdbcUtils.closeStatement(ps);
			ps = null;
			DataSourceUtils.releaseConnection(con, getDataSource());
			con = null;
			throw translateException("PreparedStatementCallback", sql, ex);
		} finally {
			if (psc instanceof ParameterDisposer) {
				((ParameterDisposer) psc).cleanupParameters();
			}
			JdbcUtils.closeStatement(ps);
			DataSourceUtils.releaseConnection(con, getDataSource());
			queryExecSpan.end();
		}
	}

	@Override
	@Nullable
	public <T> T execute(StatementCallback<T> action) throws DataAccessException {
		Assert.notNull(action, "Callback object must not be null");

		Connection con = getTracedConnection();

		Span queryExecSpan = tracer.spanBuilder("execute statement").setRecordEvents(true).setSampler(Samplers.alwaysSample()).startSpan();
		Statement stmt = null;
		try (Scope ws = tracer.withSpan(queryExecSpan)) {
			stmt = con.createStatement();
			applyStatementSettings(stmt);
			queryExecSpan.addAnnotation("Statement:" + stmt.toString());
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
			queryExecSpan.end();
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

	private Connection getTracedConnection() {
		// init trace context
//		Span span = tracer.spanBuilder("acquiring db connection from pool").setRecordEvents(true).setSampler(Samplers.alwaysSample()).startSpan();
//
//		Connection con;
//		try (Scope ws = tracer.withSpan(span)) {
//			con = DataSourceUtils.getConnection(obtainDataSource()); // calls the underlying pool
//		} finally {
//			span.end();
//		}
//		return con;

		return DataSourceUtils.getConnection(obtainDataSource());
	}

}
