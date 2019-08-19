
-- AUTO EXPLAIN
-- LOAD 'auto_explain';
-- SET auto_explain.log_min_duration = 0;
-- SET auto_explain.log_analyze = true;
-- SET auto_explain.log_format = json;


-- CSV LOG VIEW
CREATE EXTENSION file_fdw;
CREATE SERVER pglog FOREIGN DATA WRAPPER file_fdw;

CREATE FOREIGN TABLE pglog (
  log_time timestamp(3) with time zone,
  user_name text,
  database_name text,
  process_id integer,
  connection_from text,
  session_id text,
  session_line_num bigint,
  command_tag text,
  session_start_time timestamp with time zone,
  virtual_transaction_id text,
  transaction_id bigint,
  error_severity text,
  sql_state_code text,
  message text,
  detail text,
  hint text,
  internal_query text,
  internal_query_pos integer,
  context text,
  query text,
  query_pos integer,
  location text,
  application_name text
) SERVER pglog
OPTIONS ( filename '/var/lib/postgresql/data/pg_log/pglog.csv', format 'csv' );

create view pglog_last_min as (SELECT * from pglog WHERE log_time > current_timestamp - interval '1 minutes');


CREATE OR REPLACE TABLE public.spans (
	id varchar NULL,
	"index" varchar NULL,
	score int NULL,
	"type" varchar NOT NULL,
	duration bigint NOT NULL,
	flags integer NULL,
	logs jsonb NULL,
	operationname varchar NULL,
	processservicename varchar NULL,
	processtags varchar NULL,
	"references" jsonb NULL,
	spanid varchar NOT NULL,
	starttime varchar NULL,
	starttimemillis varchar NULL,
	tags jsonb NULL,
	traceid varchar NOT NULL,
	scenario varchar NULL SET DEFAULT false,
	anomaly bool NULL,
	CONSTRAINT spans_pk PRIMARY KEY (id),
	CONSTRAINT spans_un UNIQUE (spanid)
);

CREATE OR REPLACE FUNCTION public.detect_outliers(IN scenarioName TEXT) RETURNS void
	LANGUAGE sql
AS $$

		WITH raw_data AS (
			SELECT operationname AS series,
			       duration AS value
			  FROM public.spans
			WHERE scenario = scenarioName
		),
		details AS (
			SELECT series,
			       value,
			       ROW_NUMBER() OVER (PARTITION BY series ORDER BY value) AS row_number,
			       SUM(1) OVER (PARTITION BY series) AS total
			  FROM raw_data
		),
		quartiles AS (
			SELECT series,
			       value,
			       AVG(CASE WHEN row_number >= (FLOOR(total/2.0)/2.0)
			                 AND row_number <= (FLOOR(total/2.0)/2.0) + 1
			                THEN value/1.0 ELSE NULL END
			          ) OVER (PARTITION BY series) AS q1,
			       AVG(CASE WHEN row_number >= (total/2.0)
			                 AND row_number <= (total/2.0) + 1
			                THEN value/1.0 ELSE NULL END
			          ) OVER (PARTITION BY series) AS median,
			       AVG(CASE WHEN row_number >= (CEIL(total/2.0) + (FLOOR(total/2.0)/2.0))
			                 AND row_number <= (CEIL(total/2.0) + (FLOOR(total/2.0)/2.0) + 1)
			                THEN value/1.0 ELSE NULL END
			          ) OVER (PARTITION BY series) AS q3
			  FROM details
		),
		outlierthreshold as (SELECT series,
		       q3 + ((q3-q1) * 3) as upper_outlier_threshold
		  FROM quartiles
		 GROUP BY series, upper_outlier_threshold
		 )
		 UPDATE public.spans set anomaly = true
		 FROM outlierthreshold
		 where spans.operationname = outlierthreshold.series and spans.duration > outlierthreshold.upper_outlier_threshold and spans.scenario = scenarioName
		;


$$
;


