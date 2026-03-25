package com.jobscheduler.scheduler;

import com.jobscheduler.handler.JobHandler;
import com.jobscheduler.lock.RedisDistributedLock;
import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.repository.JobRepository;
import com.jobscheduler.retry.RetryHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Executes a single job by dispatching to the appropriate JobHandler.
 * Measures execution time, marks the job COMPLETED on success, or
 * delegates to RetryHandler on failure. Always releases the Redis lock.
 */
@Slf4j
@Component
public class JobExecutor {

    private static final String LOCK_PREFIX = "job-lock:";

    private final Map<String, JobHandler> handlers;
    private final JobRepository jobRepository;
    private final RetryHandler retryHandler;
    private final RedisDistributedLock distributedLock;

    public JobExecutor(Map<String, JobHandler> handlers,
                       JobRepository jobRepository,
                       RetryHandler retryHandler,
                       RedisDistributedLock distributedLock) {
        this.handlers = handlers;
        this.jobRepository = jobRepository;
        this.retryHandler = retryHandler;
        this.distributedLock = distributedLock;
    }

    /**
     * Executes the given job end-to-end:
     * <ol>
     *   <li>Finds the matching JobHandler by type</li>
     *   <li>Calls handler.execute(job)</li>
     *   <li>On success: marks job COMPLETED, sets completedAt, records execution time</li>
     *   <li>On failure: delegates to RetryHandler for retry/dead logic</li>
     *   <li>Always releases the Redis distributed lock in finally block</li>
     * </ol>
     *
     * @param job the job to execute
     */
    public void execute(Job job) {
        String lockKey = LOCK_PREFIX + job.getId();
        long startNano = System.nanoTime();

        try {
            JobHandler handler = handlers.get(job.getType());
            if (handler == null) {
                throw new IllegalStateException("No handler registered for job type: " + job.getType());
            }

            log.info("Executing job | jobId={} type={} name={}", job.getId(), job.getType(), job.getName());
            handler.execute(job);

            long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;
            markCompleted(job, elapsedMs);

        } catch (Exception ex) {
            long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;
            log.error("Job execution failed | jobId={} type={} elapsedMs={} error={}",
                    job.getId(), job.getType(), elapsedMs, ex.getMessage());
            retryHandler.handleFailure(job, ex);
        } finally {
            distributedLock.release(lockKey);
        }
    }

    @Transactional
    protected void markCompleted(Job job, long elapsedMs) {
        // Reload fresh version to avoid stale optimistic lock state
        Job fresh = jobRepository.findById(job.getId()).orElse(job);
        fresh.setStatus(JobStatus.COMPLETED);
        fresh.setCompletedAt(LocalDateTime.now());
        jobRepository.save(fresh);

        retryHandler.recordEvent(job.getId(), JobStatus.RUNNING, JobStatus.COMPLETED,
                String.format("Job completed in %dms", elapsedMs));

        log.info("Job COMPLETED | jobId={} type={} elapsedMs={}", job.getId(), job.getType(), elapsedMs);
    }
}
