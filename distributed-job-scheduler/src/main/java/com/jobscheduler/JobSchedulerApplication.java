package com.jobscheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Distributed Job Scheduler application.
 *
 * <p>This application provides:
 * <ul>
 *   <li>A REST API for job submission, cancellation, and monitoring</li>
 *   <li>A background scheduler that polls for due jobs every 5 seconds</li>
 *   <li>Redis-based distributed locking so multiple instances don't double-execute jobs</li>
 *   <li>Exponential backoff retry logic with full audit history</li>
 *   <li>Pluggable job handlers (EMAIL, HTTP_CALLBACK, LOG)</li>
 * </ul>
 */
@SpringBootApplication
public class JobSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobSchedulerApplication.class, args);
    }
}
