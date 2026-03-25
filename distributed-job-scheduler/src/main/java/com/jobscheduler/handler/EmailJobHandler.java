package com.jobscheduler.handler;

import com.jobscheduler.model.Job;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles jobs of type "EMAIL". Simulates email sending with a configurable random
 * delay and a configurable failure rate for realistic I/O simulation.
 */
@Slf4j
@Component
public class EmailJobHandler implements JobHandler {

    @Value("${handlers.email.failure-rate}")
    private double failureRate;

    @Value("${handlers.email.min-delay-ms}")
    private int minDelayMs;

    @Value("${handlers.email.max-delay-ms}")
    private int maxDelayMs;

    @Override
    public String getType() {
        return "EMAIL";
    }

    /**
     * Simulates email sending by reading 'to', 'subject', and 'body' from the job payload.
     * Applies a configurable random sleep to mimic real I/O, then optionally throws
     * a RuntimeException based on configured failure rate.
     *
     * @param job the job to execute
     * @throws Exception if the simulated failure rate triggers
     */
    @Override
    public void execute(Job job) throws Exception {
        Map<String, Object> payload = job.getPayload();
        String to = (String) payload.getOrDefault("to", "unknown@example.com");
        String subject = (String) payload.getOrDefault("subject", "(no subject)");
        String body = (String) payload.getOrDefault("body", "");

        log.info("Sending email | jobId={} to={} subject='{}'", job.getId(), to, subject);

        // Simulate real I/O latency
        int delay = ThreadLocalRandom.current().nextInt(minDelayMs, maxDelayMs + 1);
        Thread.sleep(delay);

        // Simulate configurable failure rate
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new RuntimeException(
                    String.format("Simulated email delivery failure for recipient '%s'", to));
        }

        log.info("Email sent successfully | jobId={} to={} subject='{}' delayMs={}", job.getId(), to, subject, delay);
    }
}
