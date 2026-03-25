package com.jobscheduler.model;

/**
 * Represents all possible lifecycle states of a scheduled job.
 */
public enum JobStatus {
    /** Job is queued and waiting to be picked up by the scheduler. */
    PENDING,
    /** Job is currently being executed by a worker. */
    RUNNING,
    /** Job completed successfully. */
    COMPLETED,
    /** Job failed but has remaining retry attempts. */
    FAILED,
    /** Job has exhausted all retry attempts and will not be retried. */
    DEAD,
    /** Job was explicitly cancelled by a client before execution. */
    CANCELLED
}
