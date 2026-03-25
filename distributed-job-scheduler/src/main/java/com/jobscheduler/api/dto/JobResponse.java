package com.jobscheduler.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for job representations returned by the API.
 */
@Data
@Builder
@Schema(description = "Represents a scheduled job")
public class JobResponse {

    @Schema(description = "Unique job identifier")
    private UUID id;

    @Schema(description = "Human-readable job name")
    private String name;

    @Schema(description = "Job type (EMAIL, HTTP_CALLBACK, LOG)")
    private String type;

    @Schema(description = "Job payload data")
    private Map<String, Object> payload;

    @Schema(description = "Current job status")
    private String status;

    @Schema(description = "Job priority")
    private String priority;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "When the job was originally scheduled")
    private LocalDateTime scheduledAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "When the job will next be attempted")
    private LocalDateTime nextRunAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "When execution started")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "When execution completed")
    private LocalDateTime completedAt;

    @Schema(description = "Number of retry attempts so far")
    private int retryCount;

    @Schema(description = "Maximum allowed retry attempts")
    private int maxRetries;

    @Schema(description = "Error message from last failure")
    private String lastError;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "When the job was created")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "When the job was last updated")
    private LocalDateTime updatedAt;
}
