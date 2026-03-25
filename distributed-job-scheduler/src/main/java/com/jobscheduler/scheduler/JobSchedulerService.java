package com.jobscheduler.scheduler;

import com.jobscheduler.lock.RedisDistributedLock;
import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.repository.JobRepository;
import com.jobscheduler.retry.RetryHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Core scheduler service that polls the database every N milliseconds for due jobs,
 * acquires Redis distributed locks, and submits jobs to the worker pool.
 *
 * <p>The distributed lock ensures that in a multi-instance deployment only one instance
 * processes each job. Optimistic locking on the Job entity provides a secondary
 * safety net against edge cases where the lock TTL expires during slow execution.
 */
@Slf4j
@Service
public class JobSchedulerService {

    private static final String LOCK_PREFIX = "job-lock:";
    private static final int LOCK_TTL_SECONDS = 60;

    private final JobRepository jobRepository;
    private final RedisDistributedLock distributedLock;
    private final WorkerPool workerPool;
    private final RetryHandler retryHandler;

    @Value("${scheduler.batch-size}")
    private int batchSize;

    public JobSchedulerService(JobRepository jobRepository,
                               RedisDistributedLock distributedLock,
                               WorkerPool workerPool,
                               RetryHandler retryHandler) {
        this.jobRepository = jobRepository;
        this.distributedLock = distributedLock;
        this.workerPool = workerPool;
        this.retryHandler = retryHandler;
    }

    /**
     * Scheduler tick — runs every 5 seconds (fixedDelay to avoid overlap).
     * Queries for due PENDING jobs, attempts Redis lock acquisition per job,
     * and submits locked jobs to the worker pool.
     */
    @Scheduled(fixedDelayString = "${scheduler.poll-interval-ms}")
    public void tick() {
        log.debug("Scheduler tick started at {}", LocalDateTime.now());

        List<Job> dueJobs = jobRepository.findDueJobs(
                LocalDateTime.now(),
                JobStatus.PENDING,
                PageRequest.of(0, batchSize));

        if (dueJobs.isEmpty()) {
            log.debug("No due jobs found in this tick");
            return;
        }

        log.debug("Found {} due job(s) in this tick", dueJobs.size());

        for (Job job : dueJobs) {
            String lockKey = LOCK_PREFIX + job.getId();
            log.debug("Attempting lock acquisition | jobId={} lockKey={}", job.getId(), lockKey);

            boolean acquired = distributedLock.tryAcquire(lockKey, LOCK_TTL_SECONDS);
            if (!acquired) {
                log.debug("Skipping job — lock held by another instance | jobId={}", job.getId());
                continue;
            }

            try {
                markRunning(job);
                workerPool.submit(job);
                log.debug("Job submitted to worker pool | jobId={} type={}", job.getId(), job.getType());
            } catch (Exception ex) {
                // If we fail to mark as RUNNING (e.g., optimistic lock conflict), release lock immediately
                log.warn("Failed to mark job RUNNING — releasing lock | jobId={} error={}", job.getId(), ex.getMessage());
                distributedLock.release(lockKey);
            }
        }
    }

    /**
     * Updates job status to RUNNING and records startedAt timestamp.
     * Uses optimistic locking via @Version — if another instance somehow
     * processes the same job, this will throw an OptimisticLockException.
     *
     * @param job the job to mark as running
     */
    @Transactional
    protected void markRunning(Job job) {
        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        jobRepository.save(job);
        retryHandler.recordEvent(job.getId(), JobStatus.PENDING, JobStatus.RUNNING,
                "Job picked up by scheduler and submitted to worker pool");
    }
}
