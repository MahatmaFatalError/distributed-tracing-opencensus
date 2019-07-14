
-- AUTO EXPLAIN
LOAD 'auto_explain';
SET auto_explain.log_min_duration = 0;
SET auto_explain.log_analyze = true;
SET auto_explain.log_format = json;


-- LOG
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

create view pglog_last_min as (SELECT * from pglog WHERE log_time > current_timestamp - interval '1 minutes')