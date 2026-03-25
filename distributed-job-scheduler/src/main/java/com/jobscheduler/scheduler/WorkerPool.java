package com.jobscheduler.scheduler;

import com.jobscheduler.model.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * Worker pool that submits jobs for asynchronous execution.
 * Delegates to the configured ThreadPoolTaskExecutor ("jobWorkerExecutor").
 */
@Slf4j
@Component
public class WorkerPool {

    private final Executor jobWorkerExecutor;
    private final JobExecutor jobExecutor;

    public WorkerPool(@Qualifier("jobWorkerExecutor") Executor jobWorkerExecutor,
                      JobExecutor jobExecutor) {
        this.jobWorkerExecutor = jobWorkerExecutor;
        this.jobExecutor = jobExecutor;
    }

    /**
     * Submits a job for asynchronous execution in the worker thread pool.
     * Returns immediately; job execution happens in the background.
     *
     * @param job the job to execute
     */
    public void submit(Job job) {
        log.debug("Submitting job to worker pool | jobId={} type={}", job.getId(), job.getType());
        jobWorkerExecutor.execute(() -> jobExecutor.execute(job));
    }
}
