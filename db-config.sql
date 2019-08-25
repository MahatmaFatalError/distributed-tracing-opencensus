
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


CREATE TABLE public.spans (
	id varchar NOT NULL,
	"index" varchar NULL,
	score int4 NULL,
	"type" varchar NOT NULL,
	duration int8 NOT NULL,
	flags int4 NULL,
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
	scenario varchar NULL,
	anomaly bool NULL DEFAULT false,
	parent_span varchar NULL,
	root_cause bool NULL,
	CONSTRAINT spans_check CHECK (((parent_span)::text = (spanid)::text)),
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

CREATE OR REPLACE FUNCTION public.detect_lock_outliers() RETURNS void
	LANGUAGE sql
AS $$
	UPDATE public.spans set anomaly = true where spans.operationname like 'wait for Table Lock%';

$$
;

CREATE OR REPLACE FUNCTION public.detect_cache_outliers() RETURNS void
	LANGUAGE sql
AS $$
	UPDATE public.spans set anomaly = true where spans.operationname = 'Cache Miss'
--logs->0->'fields'->0->>'key' = 'cache_miss'
$$
;


update spans set parent_span = "references"->0->>'spanID'
where "references"->0->>'refType' = 'CHILD_OF' and traceid = "references"->0->>'traceID';




CREATE OR REPLACE FUNCTION public.isRootCause(IN spanId TEXT) RETURNS BOOLEAN AS
$$
WITH RECURSIVE rec (traceid, spanid, parent_span, anomaly) as
(
  SELECT traceid, spanid, spans.parent_span, anomaly from spans where spanid = $1
  UNION ALL
  SELECT spans.traceid, spans.spanid, spans.parent_span, spans.anomaly from rec, spans where spans.parent_span = rec.spanid -- and spans.anomaly = true
)
SELECT not EXISTS( SELECT * FROM rec where anomaly = true offset 1)
$$ LANGUAGE sql;



CREATE OR REPLACE FUNCTION updateRCA() RETURNS void AS $$
DECLARE
    temprow record;
BEGIN
    FOR temprow IN select distinct spanid from spans where anomaly = true
    loop
    	update spans set root_cause = isRootCause(temprow.spanid) where spanid = temprow.spanid;
    END LOOP;
END;
$$ LANGUAGE plpgsql;


select detect_cache_outliers(), detect_lock_outliers(), detect_outliers('cache_miss') , detect_outliers('conn-pool') , detect_outliers('lock');
select updateRCA();