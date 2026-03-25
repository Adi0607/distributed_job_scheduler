package com.jobscheduler.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Thread pool configuration for the job worker pool.
 * All sizing parameters are sourced from application.yml.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class ThreadPoolConfig {

    @Value("${scheduler.worker.core-pool-size}")
    private int corePoolSize;

    @Value("${scheduler.worker.max-pool-size}")
    private int maxPoolSize;

    @Value("${scheduler.worker.queue-capacity}")
    private int queueCapacity;

    /**
     * Creates the thread pool executor used by WorkerPool to run job handlers
     * asynchronously with controlled concurrency.
     *
     * @return configured ThreadPoolTaskExecutor
     */
    @Bean(name = "jobWorkerExecutor")
    public Executor jobWorkerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("job-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
