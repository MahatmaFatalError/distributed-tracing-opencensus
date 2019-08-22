package com.greetings.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.greetings.service.GreetingsService;
import com.greetings.utils.HttpUtils;
import com.greetings.utils.SpanUtils;

import io.opencensus.common.Scope;
import io.opencensus.trace.Span;
import io.opencensus.trace.Status;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;

@RestController
@RequestMapping("/greetings")
public class GreetingsController {

    private static final Logger LOG = LoggerFactory.getLogger(GreetingsController.class);

	private final String helloServiceHost;

    private static final Tracer tracer = Tracing.getTracer();

    private final GreetingsService greetingsService;

    @Autowired
    public GreetingsController(GreetingsService greetingsService, @Value("${service.helloHost}") String helloServiceHost) {
        this.greetingsService = greetingsService;
        this.helloServiceHost = helloServiceHost;
    }

    @GetMapping("/hellocache")
    public String getHelloCache() {
    	Span span = SpanUtils.buildSpan(tracer, "GreetingsService hello").startSpan();
        String result;
        String url = "http://" + helloServiceHost + ":8888/hellocache";

        try (Scope ws = tracer.withSpan(span)) {
            result = HttpUtils.callEndpoint(url, HttpMethod.GET);
        } catch (Exception e) {
            span.setStatus(Status.ABORTED);
            span.addAnnotation("Error while calling service");
            LOG.error("Error while calling service: {}", e.getMessage());
            result = e.getMessage();
        } finally {
        	span.end();
		}

        return result;
  }

    @RequestMapping(value = "/sleepquery", method = RequestMethod.GET)
    public void execSleepQuery(@RequestParam(name = "duration", defaultValue = "400") Integer millis) {
    	Span span = SpanUtils.buildSpan(tracer, "GreetingsService sleepquery").startSpan();
        String result;
        String url = "http://" + helloServiceHost + ":8888/sleepquery?duration="+millis;

        try (Scope ws = tracer.withSpan(span)) {
            result = HttpUtils.callEndpoint(url, HttpMethod.GET);
        } catch (Exception e) {
            span.setStatus(Status.ABORTED);
            span.addAnnotation("Error while calling service");
            LOG.error("Error while calling service: {}", e.getMessage());
            result = e.getMessage();
        } finally {
        	span.end();
		}

  }

    @GetMapping("/hello")
    public String getHello() {
        String result = greetingsService.printHello();
        return result;
    }

}
