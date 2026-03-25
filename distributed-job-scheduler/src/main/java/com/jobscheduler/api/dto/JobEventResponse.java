package com.jobscheduler.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO representing a single job status transition event.
 */
@Data
@Builder
@Schema(description = "Represents a job status transition event in the execution history")
public class JobEventResponse {

    @Schema(description = "Unique event identifier")
    private UUID id;

    @Schema(description = "Job ID this event belongs to")
    private UUID jobId;

    @Schema(description = "Previous status (null for first event)")
    private String fromStatus;

    @Schema(description = "New status after this transition")
    private String toStatus;

    @Schema(description = "Human-readable message describing the transition")
    private String message;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "When this transition occurred")
    private LocalDateTime occurredAt;
}
