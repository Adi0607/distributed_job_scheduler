package com.jobscheduler.handler;

import com.jobscheduler.model.Job;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * Handles jobs of type "HTTP_CALLBACK". Makes a real HTTP call to the configured URL
 * using the specified HTTP method. Throws an exception on non-2xx responses.
 */
@Slf4j
@Component
public class HttpCallbackJobHandler implements JobHandler {

    private final RestTemplate restTemplate;

    public HttpCallbackJobHandler(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${handlers.http-callback.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${handlers.http-callback.read-timeout-ms}") int readTimeoutMs) {
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    @Override
    public String getType() {
        return "HTTP_CALLBACK";
    }

    /**
     * Reads 'url' and 'method' from the job payload and performs an HTTP call.
     * The response body can optionally be provided in the 'body' payload field.
     * Throws an exception if the response status is non-2xx.
     *
     * @param job the job to execute
     * @throws Exception on HTTP error or connection failure
     */
    @Override
    public void execute(Job job) throws Exception {
        Map<String, Object> payload = job.getPayload();
        String url = (String) payload.get("url");
        String method = (String) payload.getOrDefault("method", "GET");

        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("HTTP_CALLBACK job payload must contain 'url'");
        }

        log.info("Making HTTP callback | jobId={} method={} url={}", job.getId(), method, url);

        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
        ResponseEntity<String> response = restTemplate.exchange(url, httpMethod, null, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException(
                    String.format("HTTP callback failed | status=%s url=%s", response.getStatusCode(), url));
        }

        log.info("HTTP callback succeeded | jobId={} status={} url={}", job.getId(), response.getStatusCode(), url);
    }
}
