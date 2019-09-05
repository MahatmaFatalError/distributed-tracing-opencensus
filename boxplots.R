library(RPostgreSQL)
library(ggplot2)
library(tibble)
require(scales)
library(corrplot)

## connect to db
pg = dbDriver("PostgreSQL")
con = dbConnect(pg, user="postgres", password="postgres",
                host="localhost", port=5432, dbname="postgres")

dbquery = dbGetQuery(con, "select traceid, spanid, parent_span, operationname, duration, starttimemillis, anomaly, root_cause, scenario from spans where scenario = 'lock'")
dbquery = dbGetQuery(con, "select traceid, spanid, parent_span, operationname, duration, starttimemillis, anomaly, root_cause, scenario from spans where scenario = 'conn-pool'")
dbquery = dbGetQuery(con, "select traceid, spanid, parent_span, operationname, duration, starttimemillis, anomaly, root_cause, scenario from spans where scenario = 'cache_miss'")

summary(dbquery)
str(dbquery)


dbquery_tbl <- as_data_frame(dbquery)
dbquery_tbl$operationname <- as.factor(dbquery_tbl$operationname)

boxplot(duration~operationname,
        data=dbquery_tbl,
        main="Duration Distribution per Span",
        xlab="Span Operation Name",
        ylab="Duration in ms",
        col="orange",
        border="brown",
        horizontal = TRUE
)

p  <- ggplot(dbquery_tbl, aes(operationname, duration/1000)) +
  geom_boxplot(        # custom outliers
    outlier.colour="red",
    outlier.fill="red",
    outlier.size=2) +
  coord_flip() +
  ylab("duration in ms") 

# displays as you require
p + scale_y_continuous(labels = comma)





dbquery_corr = dbGetQuery(con, "select max(case when operationname = 'Request sent' then duration else null end) \"Request sent\",
                     max(case when operationname = 'GET /sleepquery' then duration else null end) \"GET /sleepquery\",
                     max(case when operationname = 'GreetingsService sleepquery' then duration else null end) \"GreetingsService sleepquery\",
                     max(case when operationname = 'execute statement' then duration else null end) \"execute statement\",
                     max(case when operationname = 'acquiring db connection from ProxyDataSourceInterceptor' then duration else null end) \"acquiring db connection\"
                     from spans
                     where scenario = 'conn-pool'
                     group by traceid;")

dbquery_tbl_corr <- as_data_frame(dbquery_corr)

fit.pea <- cor(dbquery_tbl_corr, use="complete.obs",method="pearson")
fit.pea


M <- cor(dbquery_tbl_corr)
corrplot(M, method = "circle") # in corrplot package, named "circle", "square", "ellipse", "number", "shade", "color", "pie".
corrplot(fit.pea, method = "circle") 


dbquery_corr2 = dbGetQuery(con, "select 
  max(case when operationname = 'Request sent' then duration else 0 end) \"Request sent\",
                           max(case when operationname = 'Cache Miss' then duration else 0 end) \"Cache Miss\",
                           max(case when operationname = 'GET /hellocache' then duration else 0 end) \"GET /hellocache\",
                           max(case when operationname = 'HelloController clear Cache' then duration else 0 end) \"HelloController clear Cache\",
                           max(case when operationname = 'Hash' then duration else 0 end) \"Hash\",
                           max(case when operationname = 'Aggregate' then duration else 0 end) \"Aggregate\",
                           max(case when operationname = 'Sort' then duration else 0 end) \"Sort\",
                           max(case when operationname = 'execPlan' then duration else 0 end) \"execPlan\",
                           max(case when operationname = 'Gather' then duration else 0 end) \"Gather\",
                           max(case when operationname = 'GET /clearcache' then duration else 0 end) \"GET /clearcache\",
                           max(case when operationname = 'Seq Scan' then duration else 0 end) \"Seq Scan\",
                           max(case when operationname = 'Cache Hit' then duration else 0 end) \"Cache Hit\",
                           max(case when operationname = 'GreetingsService hello' then duration else 0 end) \"GreetingsService hello\",
                           max(case when operationname = 'execute statement' then duration else 0 end) \"execute statement\",
                           max(case when operationname = 'acquiring db connection from ProxyDataSourceInterceptor' then duration else 0 end) \"acquiring db connection\"
                           from spans
                           where scenario = 'cache_miss'
                           group by traceid;")

dbquery_tbl_corr2 <- as_data_frame(dbquery_corr2)

fit.pea2 <- cor(dbquery_tbl_corr2, use="complete.obs",method="pearson")
corrplot(fit.pea2, method = "circle") 



dbquery_corr3 = dbGetQuery(con, "select traceid, scenario, count(anomaly) filter ( WHERE anomaly = true) anomaly_counter, count(root_cause) FILTER (WHERE root_cause = true) root_cause_counter from spans group by scenario,traceid having count(anomaly) filter ( WHERE anomaly = true) > 0 order by traceid;")

dbquery_tbl_corr3 <- as_data_frame(dbquery_corr3)
summary(dbquery_tbl_corr3)
str(dbquery_tbl_corr3)


library(reshape2)
dbquery_tbl_corr3.m <- melt(dbquery_tbl_corr3,id.vars='scenario', measure.vars=c('anomaly_counter','root_cause_counter'))
ggplot(dbquery_tbl_corr3.m) +
  geom_boxplot(aes(x=scenario, y=value, color=variable))+
  ylab("Amount per Trace")


#+facet_grid(. ~ scenario)


dbquery_tbl_stats = as_data_frame(dbGetQuery(con, "select scenario, operationname, count(*) span_count, 
count(anomaly) filter ( WHERE anomaly = true) anomaly_counter, 
                                             count(root_cause) FILTER (WHERE root_cause = true) root_cause_counter 
                                             from public.spans 
                                             group by scenario,operationname
                                             having count(anomaly) filter ( WHERE anomaly = true) > 0
                                             order by scenario,operationname;"))

summary(dbquery_tbl_stats)
