package com.jobscheduler.api;

import com.jobscheduler.api.dto.*;
import com.jobscheduler.handler.JobHandler;
import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobEvent;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.model.Priority;
import com.jobscheduler.repository.JobEventRepository;
import com.jobscheduler.repository.JobRepository;
import com.jobscheduler.retry.RetryHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for job management operations.
 * Provides CRUD-style endpoints plus retry and execution history.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Jobs", description = "Job scheduling and management endpoints")
public class JobController {

    private final JobRepository jobRepository;
    private final JobEventRepository jobEventRepository;
    private final RetryHandler retryHandler;
    private final Map<String, JobHandler> handlers;

    public JobController(JobRepository jobRepository,
                         JobEventRepository jobEventRepository,
                         RetryHandler retryHandler,
                         Map<String, JobHandler> handlers) {
        this.jobRepository = jobRepository;
        this.jobEventRepository = jobEventRepository;
        this.retryHandler = retryHandler;
        this.handlers = handlers;
    }

    /**
     * Creates and schedules a new job.
     *
     * @param request the job creation request
     * @return 201 Created with the full job representation
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a new job", description = "Creates a new scheduled job. The job type must match a registered handler.")
    @ApiResponse(responseCode = "201", description = "Job created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request — validation failed or unknown job type")
    public ResponseEntity<JobResponse> createJob(@Valid @RequestBody JobRequest request) {
        String type = request.getType().toUpperCase();
        if (!handlers.containsKey(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown job type: " + type + ". Registered types: " + handlers.keySet());
        }

        Priority priority;
        try {
            priority = Priority.valueOf(request.getPriority().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid priority: " + request.getPriority());
        }

        Job job = Job.builder()
                .name(request.getName())
                .type(type)
                .payload(request.getPayload() != null ? request.getPayload() : Map.of())
                .priority(priority)
                .status(JobStatus.PENDING)
                .scheduledAt(request.getScheduledAt())
                .nextRunAt(request.getScheduledAt())
                .maxRetries(request.getMaxRetries())
                .build();

        Job saved = jobRepository.save(job);
        retryHandler.recordEvent(saved.getId(), null, JobStatus.PENDING, "Job created and scheduled");

        log.info("Job created | jobId={} type={} scheduledAt={}", saved.getId(), saved.getType(), saved.getScheduledAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    /**
     * Retrieves a job by its unique identifier.
     *
     * @param id job UUID
     * @return 200 with job details or 404 if not found
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get job by ID")
    @ApiResponse(responseCode = "200", description = "Job found")
    @ApiResponse(responseCode = "404", description = "Job not found")
    public ResponseEntity<JobResponse> getJob(@PathVariable UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + id));
        return ResponseEntity.ok(toResponse(job));
    }

    /**
     * Lists jobs with optional filtering, pagination, and sorting.
     *
     * @param status   filter by status
     * @param priority filter by priority
     * @param type     filter by job type
     * @param from     filter by createdAt >= from
     * @param to       filter by createdAt <= to
     * @param page     zero-indexed page number
     * @param size     page size
     * @return paginated job list
     */
    @GetMapping
    @Operation(summary = "List jobs with optional filters",
               description = "Returns a paginated list of jobs. All filter parameters are optional.")
    public ResponseEntity<PagedJobResponse> listJobs(
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            @Parameter(description = "Filter by priority") @RequestParam(required = false) String priority,
            @Parameter(description = "Filter by job type") @RequestParam(required = false) String type,
            @Parameter(description = "Created at or after (ISO datetime)") @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Created at or before (ISO datetime)") @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        JobStatus statusEnum = parseEnum(status, JobStatus.class, "status");
        Priority priorityEnum = parseEnum(priority, Priority.class, "priority");

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Job> jobs = jobRepository.findWithFilters(statusEnum, priorityEnum,
                type != null ? type.toUpperCase() : null, from, to, pageRequest);

        List<JobResponse> responses = jobs.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        PagedJobResponse response = PagedJobResponse.builder()
                .jobs(responses)
                .totalCount(jobs.getTotalElements())
                .page(page)
                .size(size)
                .totalPages(jobs.getTotalPages())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Cancels a PENDING job. Returns 409 if the job is already running or completed.
     *
     * @param id job UUID
     * @return 200 with updated job or 409 if not cancellable
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a job", description = "Cancels a job. Only PENDING jobs can be cancelled.")
    @ApiResponse(responseCode = "200", description = "Job cancelled")
    @ApiResponse(responseCode = "404", description = "Job not found")
    @ApiResponse(responseCode = "409", description = "Job is not in PENDING state and cannot be cancelled")
    public ResponseEntity<JobResponse> cancelJob(@PathVariable UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + id));

        if (job.getStatus() != JobStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot cancel job in status " + job.getStatus() + ". Only PENDING jobs can be cancelled.");
        }

        JobStatus previousStatus = job.getStatus();
        job.setStatus(JobStatus.CANCELLED);
        Job saved = jobRepository.save(job);
        retryHandler.recordEvent(job.getId(), previousStatus, JobStatus.CANCELLED, "Job cancelled via API");

        log.info("Job cancelled | jobId={}", id);
        return ResponseEntity.ok(toResponse(saved));
    }

    /**
     * Manually retries a FAILED or DEAD job by resetting retry count and scheduling immediately.
     *
     * @param id job UUID
     * @return 200 with updated job or 400 if job is not in a retriable state
     */
    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry a failed or dead job", description = "Resets retry count and schedules the job for immediate re-execution.")
    @ApiResponse(responseCode = "200", description = "Job rescheduled for retry")
    @ApiResponse(responseCode = "400", description = "Job is not in FAILED or DEAD state")
    @ApiResponse(responseCode = "404", description = "Job not found")
    public ResponseEntity<JobResponse> retryJob(@PathVariable UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + id));

        if (job.getStatus() != JobStatus.FAILED && job.getStatus() != JobStatus.DEAD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only FAILED or DEAD jobs can be retried. Current status: " + job.getStatus());
        }

        JobStatus previousStatus = job.getStatus();
        job.setRetryCount(0);
        job.setStatus(JobStatus.PENDING);
        job.setNextRunAt(LocalDateTime.now());
        job.setLastError(null);
        Job saved = jobRepository.save(job);
        retryHandler.recordEvent(job.getId(), previousStatus, JobStatus.PENDING, "Manual retry requested via API");

        log.info("Job manually retried | jobId={} previousStatus={}", id, previousStatus);
        return ResponseEntity.ok(toResponse(saved));
    }

    /**
     * Returns the full execution history (status transitions) for a job.
     *
     * @param id job UUID
     * @return list of job events ordered by occurrence time
     */
    @GetMapping("/{id}/logs")
    @Operation(summary = "Get job execution history", description = "Returns all status transitions for a job, ordered by time.")
    @ApiResponse(responseCode = "200", description = "Event history returned")
    @ApiResponse(responseCode = "404", description = "Job not found")
    public ResponseEntity<List<JobEventResponse>> getJobLogs(@PathVariable UUID id) {
        if (!jobRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + id);
        }

        List<JobEvent> events = jobEventRepository.findByJobIdOrderByOccurredAtAsc(id);
        List<JobEventResponse> responses = events.stream()
                .map(this::toEventResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    // ======== Mappers ========

    private JobResponse toResponse(Job job) {
        return JobResponse.builder()
                .id(job.getId())
                .name(job.getName())
                .type(job.getType())
                .payload(job.getPayload())
                .status(job.getStatus().name())
                .priority(job.getPriority().name())
                .scheduledAt(job.getScheduledAt())
                .nextRunAt(job.getNextRunAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .retryCount(job.getRetryCount())
                .maxRetries(job.getMaxRetries())
                .lastError(job.getLastError())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    private JobEventResponse toEventResponse(JobEvent event) {
        return JobEventResponse.builder()
                .id(event.getId())
                .jobId(event.getJobId())
                .fromStatus(event.getFromStatus() != null ? event.getFromStatus().name() : null)
                .toStatus(event.getToStatus().name())
                .message(event.getMessage())
                .occurredAt(event.getOccurredAt())
                .build();
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> enumClass, String fieldName) {
        if (value == null) return null;
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid " + fieldName + " value: " + value);
        }
    }
}
