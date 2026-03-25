package com.jobscheduler.handler;

import com.jobscheduler.model.Job;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles jobs of type "LOG". Logs the payload at INFO level. Never fails.
 * Used for smoke testing and verifying the scheduler pipeline works end-to-end.
 */
@Slf4j
@Component
public class LogJobHandler implements JobHandler {

    @Override
    public String getType() {
        return "LOG";
    }

    /**
     * Logs the job payload at INFO level. This handler never throws an exception,
     * making it ideal for verifying scheduler and retry logic in integration tests.
     *
     * @param job the job to execute
     */
    @Override
    public void execute(Job job) throws Exception {
        log.info("LOG job executed | jobId={} name={} payload={}", job.getId(), job.getName(), job.getPayload());
    }
}
