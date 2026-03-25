package com.jobscheduler.retry;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobEvent;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.repository.JobEventRepository;
import com.jobscheduler.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Handles retry logic for failed jobs using exponential backoff.
 * Records every status transition to the job_events table for full audit history.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetryHandler {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final JobRepository jobRepository;
    private final JobEventRepository jobEventRepository;

    /**
     * Processes a job failure by either scheduling a retry (with exponential backoff)
     * or marking the job as DEAD if all retries are exhausted.
     *
     * <p>Exponential backoff formula: nextRunAt = now + 2^retryCount seconds
     * (e.g., 2s after 1st failure, 4s after 2nd, 8s after 3rd).
     *
     * @param job   the job that failed
     * @param error the exception that caused the failure
     */
    @Transactional
    public void handleFailure(Job job, Throwable error) {
        JobStatus fromStatus = job.getStatus();
        int newRetryCount = job.getRetryCount() + 1;
        String errorMessage = truncate(error.getMessage(), MAX_ERROR_LENGTH);

        job.setRetryCount(newRetryCount);
        job.setLastError(errorMessage);

        if (newRetryCount < job.getMaxRetries()) {
            // Schedule retry with exponential backoff: 2^retryCount seconds
            long backoffSeconds = (long) Math.pow(2, newRetryCount);
            LocalDateTime nextRun = LocalDateTime.now().plusSeconds(backoffSeconds);
            job.setNextRunAt(nextRun);
            job.setStatus(JobStatus.PENDING);

            log.info("Scheduling retry | jobId={} type={} attempt={}/{} backoffSeconds={} nextRunAt={}",
                    job.getId(), job.getType(), newRetryCount, job.getMaxRetries(), backoffSeconds, nextRun);

            recordEvent(job.getId(), fromStatus, JobStatus.PENDING,
                    String.format("Retry %d/%d scheduled in %ds. Error: %s",
                            newRetryCount, job.getMaxRetries(), backoffSeconds, errorMessage));
        } else {
            // Exhausted all retries — mark as DEAD
            job.setStatus(JobStatus.DEAD);
            log.warn("Job moved to DEAD | jobId={} type={} retries={} lastError={}",
                    job.getId(), job.getType(), newRetryCount, errorMessage);

            recordEvent(job.getId(), fromStatus, JobStatus.DEAD,
                    String.format("All %d retries exhausted. Final error: %s",
                            job.getMaxRetries(), errorMessage));
        }

        jobRepository.save(job);
    }

    /**
     * Records a job status transition event to the job_events table.
     *
     * @param jobId      the job ID
     * @param fromStatus previous status
     * @param toStatus   new status
     * @param message    human-readable message describing the transition
     */
    @Transactional
    public void recordEvent(java.util.UUID jobId, JobStatus fromStatus, JobStatus toStatus, String message) {
        JobEvent event = JobEvent.builder()
                .jobId(jobId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .message(message)
                .occurredAt(LocalDateTime.now())
                .build();
        jobEventRepository.save(event);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "Unknown error";
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
