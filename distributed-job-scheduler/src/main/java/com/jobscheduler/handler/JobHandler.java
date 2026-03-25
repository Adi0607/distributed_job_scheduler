package com.jobscheduler.handler;

import com.jobscheduler.model.Job;

/**
 * Strategy interface for job type handlers.
 * Each implementation handles a specific job type (e.g., EMAIL, HTTP_CALLBACK, LOG).
 * All implementations must be registered as Spring beans.
 */
public interface JobHandler {

    /**
     * Returns the job type identifier this handler supports (e.g., "EMAIL").
     * Must be unique across all registered handlers.
     *
     * @return the job type string
     */
    String getType();

    /**
     * Executes the given job. Implementations should read configuration
     * from the job's payload map.
     *
     * @param job the job to execute
     * @throws Exception if execution fails — the scheduler will trigger retry logic
     */
    void execute(Job job) throws Exception;
}
