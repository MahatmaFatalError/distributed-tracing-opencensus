# OpenCensus - A stats collection and distributed tracing framework

[OpenCensus](https://opencensus.io/) is a toolkit for collecting application performance and behavior data.
Opencensus is a single distribution of libraries that automatically collects traces and metrics from your app, displays them locally, and sends them to any analysis tool.

Although OpenCensus records stats or traces, in this example we will only export traces and propagate context between 2 services.

## Exporter
Opencensus includes exporters for storage and analysis tools. Right now the list includes Zipkin, Prometheus, Jaeger, Stackdriver, and SignalFx.
In this example will use the [Jaeger](https://www.jaegertracing.io/docs/) exporter using the all-in-one docker image.

### THE EXAMPLE
In this example we are running 2 services:
 * hello-service
 * greetings-service

_Hello service_
This service will run on **port 8888** and exposes the enpoint **/hello**.
This endpoint returns the String "Hello from XYZ" while XYZ is the result of a heavy SQL query. Note that the response will be cached.

_Greetings service_
This service will run on **port 8080** and exposes the enpoint **/greetings/hello**.
When calling this endpoint, this service calls the _hello-service_ and returns the String received in the response.

Additionally, PostgreSQL 11.3 is integrated with a custom postgresql.conf with ['auto_exlain'](https://www.postgresql.org/docs/current/auto-explain.html) plugin enabled to log execution plans automatically.

![Example](https://image.ibb.co/b8OEFJ/example.png)

When a request is process, traces are sent to Jaeger tool where you will get a detailed view of the operation. You can access at http://localhost:16686/


#### Build/run the example
Example can be run with docker-compose.
**Build**:
```
docker-compose build
```

**Run**:
```
docker-compose up
```
Once PostgreSQL is up initially, execute both db-config.sql and db-data.sql. The first one will provide a VIEW to access PostgreSQL Logs. The latter one will generate dummy data.


**Call endpoint**: http://localhost:8080/greetings/hello

**Clear cache of /hello endpoint**: http://localhost:8888/clearcache
