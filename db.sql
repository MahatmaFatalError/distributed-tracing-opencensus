-- Drop table

-- DROP TABLE public.helloworld;

CREATE TABLE public.helloworld (
	id serial NOT NULL,
	"name" varchar NULL
);

INSERT INTO public.helloworld
("name")
VALUES('Julian');

LOAD 'auto_explain';
SET auto_explain.log_min_duration = 0;
SET auto_explain.log_analyze = true;
SET auto_explain.log_format = json;