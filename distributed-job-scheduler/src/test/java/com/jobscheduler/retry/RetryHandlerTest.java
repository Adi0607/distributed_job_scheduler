package com.jobscheduler.retry;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobEvent;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.model.Priority;
import com.jobscheduler.repository.JobEventRepository;
import com.jobscheduler.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for RetryHandler using Mockito.
 * Tests retry count increment, exponential backoff, DEAD transition, and event recording.
 */
@ExtendWith(MockitoExtension.class)
class RetryHandlerTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobEventRepository jobEventRepository;

    @InjectMocks
    private RetryHandler retryHandler;

    private Job job;

    @BeforeEach
    void setUp() {
        job = Job.builder()
                .id(UUID.randomUUID())
                .name("test-job")
                .type("EMAIL")
                .payload(Map.of())
                .status(JobStatus.RUNNING)
                .priority(Priority.MEDIUM)
                .scheduledAt(LocalDateTime.now())
                .nextRunAt(LocalDateTime.now())
                .retryCount(0)
                .maxRetries(3)
                .build();

        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jobEventRepository.save(any(JobEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /**
     * After first failure: retry count should increment to 1 and status reset to PENDING.
     */
    @Test
    void firstFailure_incrementsRetryCountAndSetsPending() {
        retryHandler.handleFailure(job, new RuntimeException("SMTP timeout"));

        assertThat(job.getRetryCount()).isEqualTo(1);
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getLastError()).contains("SMTP timeout");
    }

    /**
     * Exponential backoff: after retry 1, nextRunAt should be at least 2 seconds in future.
     * After retry 2, at least 4 seconds in future.
     */
    @Test
    void retryBackoff_isExponential() {
        LocalDateTime before = LocalDateTime.now();

        // First failure → backoff = 2^1 = 2 seconds
        retryHandler.handleFailure(job, new RuntimeException("error1"));
        LocalDateTime nextRun1 = job.getNextRunAt();
        assertThat(nextRun1).isAfter(before.plusSeconds(1));

        // Second failure (retryCount now 1 → becomes 2) → backoff = 2^2 = 4 seconds
        job.setStatus(JobStatus.RUNNING);
        retryHandler.handleFailure(job, new RuntimeException("error2"));
        LocalDateTime nextRun2 = job.getNextRunAt();
        assertThat(nextRun2).isAfter(before.plusSeconds(3));
    }

    /**
     * When retry count reaches maxRetries, job status should be set to DEAD.
     */
    @Test
    void afterMaxRetries_jobIsMarkedDead() {
        job.setRetryCount(2); // next failure pushes to 3 == maxRetries

        retryHandler.handleFailure(job, new RuntimeException("final error"));

        assertThat(job.getStatus()).isEqualTo(JobStatus.DEAD);
        assertThat(job.getRetryCount()).isEqualTo(3);
    }

    /**
     * Each handleFailure call must write a record to job_events.
     */
    @Test
    void eachFailure_writesJobEventRecord() {
        retryHandler.handleFailure(job, new RuntimeException("err"));

        ArgumentCaptor<JobEvent> eventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        verify(jobEventRepository, atLeastOnce()).save(eventCaptor.capture());

        JobEvent recorded = eventCaptor.getValue();
        assertThat(recorded.getJobId()).isEqualTo(job.getId());
        assertThat(recorded.getToStatus()).isIn(JobStatus.PENDING, JobStatus.DEAD);
    }

    /**
     * When job moves to DEAD, event should capture the DEAD transition.
     */
    @Test
    void deadTransition_recordsDeadEvent() {
        job.setRetryCount(2); // max retries = 3, so next failure → DEAD

        retryHandler.handleFailure(job, new RuntimeException("fatal error"));

        ArgumentCaptor<JobEvent> eventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        verify(jobEventRepository).save(eventCaptor.capture());

        JobEvent event = eventCaptor.getValue();
        assertThat(event.getToStatus()).isEqualTo(JobStatus.DEAD);
        assertThat(event.getMessage()).contains("3");
    }
}
