package com.jobscheduler.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscheduler.api.dto.JobRequest;
import com.jobscheduler.handler.JobHandler;
import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.model.Priority;
import com.jobscheduler.repository.JobEventRepository;
import com.jobscheduler.repository.JobRepository;
import com.jobscheduler.retry.RetryHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for JobController using MockMvc and mocked dependencies.
 */
@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobRepository jobRepository;

    @MockBean
    private JobEventRepository jobEventRepository;

    @MockBean
    private RetryHandler retryHandler;

    @MockBean
    private Map<String, JobHandler> handlers;

    private Job sampleJob;
    private UUID jobId;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        sampleJob = Job.builder()
                .id(jobId)
                .name("test-job")
                .type("LOG")
                .payload(Map.of("key", "value"))
                .status(JobStatus.PENDING)
                .priority(Priority.MEDIUM)
                .scheduledAt(LocalDateTime.now().plusMinutes(30))
                .nextRunAt(LocalDateTime.now().plusMinutes(30))
                .maxRetries(3)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void submitValidJob_returns201() throws Exception {
        when(handlers.containsKey("LOG")).thenReturn(true);
        when(jobRepository.save(any(Job.class))).thenReturn(sampleJob);

        JobRequest request = JobRequest.builder()
                .name("test-job")
                .type("LOG")
                .scheduledAt(LocalDateTime.now().plusHours(1))
                .priority("MEDIUM")
                .maxRetries(3)
                .build();

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void submitJobWithPastScheduledAt_returns400() throws Exception {
        JobRequest request = JobRequest.builder()
                .name("past-job")
                .type("LOG")
                .scheduledAt(LocalDateTime.now().minusHours(1))  // past
                .priority("MEDIUM")
                .maxRetries(3)
                .build();

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getJobByValidId_returns200() throws Exception {
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));

        mockMvc.perform(get("/api/v1/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.name").value("test-job"));
    }

    @Test
    void getNonExistentJob_returns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(jobRepository.findById(unknownId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/jobs/{id}", unknownId))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelPendingJob_returns200() throws Exception {
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));
        Job cancelled = Job.builder()
                .id(jobId).name("test-job").type("LOG")
                .payload(Map.of()).status(JobStatus.CANCELLED)
                .priority(Priority.MEDIUM)
                .scheduledAt(sampleJob.getScheduledAt())
                .nextRunAt(sampleJob.getNextRunAt())
                .maxRetries(3).retryCount(0)
                .createdAt(sampleJob.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();
        when(jobRepository.save(any(Job.class))).thenReturn(cancelled);

        mockMvc.perform(delete("/api/v1/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelRunningJob_returns409() throws Exception {
        sampleJob.setStatus(JobStatus.RUNNING);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(sampleJob));

        mockMvc.perform(delete("/api/v1/jobs/{id}", jobId))
                .andExpect(status().isConflict());
    }
}
