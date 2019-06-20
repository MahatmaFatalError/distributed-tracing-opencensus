-- Drop table

-- DROP TABLE public.helloworld;

CREATE TABLE public.helloworld (
	id serial NOT NULL,
	"name" varchar NULL
);

INSERT INTO public.helloworld
("name")
VALUES('Julian');