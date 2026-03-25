package com.jobscheduler.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Request DTO for creating a new scheduled job.
 */
@Data
@Builder
@Schema(description = "Request body for creating a new job")
public class JobRequest {

    @NotBlank(message = "Job name is required")
    @Schema(description = "Human-readable name for this job", example = "send-welcome-email")
    private String name;

    @NotBlank(message = "Job type is required")
    @Schema(description = "Job type — must match a registered handler (EMAIL, HTTP_CALLBACK, LOG)", example = "EMAIL")
    private String type;

    @Schema(description = "Arbitrary job payload as JSON object", example = "{\"to\":\"user@example.com\",\"subject\":\"Welcome\"}")
    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();

    @NotNull(message = "scheduledAt is required")
    @Future(message = "scheduledAt must be in the future")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "When the job should run (must be future)", example = "2024-06-01T10:00:00")
    private LocalDateTime scheduledAt;

    @Schema(description = "Job priority level", example = "HIGH", allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"})
    @Builder.Default
    private String priority = "MEDIUM";

    @Min(value = 0, message = "maxRetries must be >= 0")
    @Max(value = 10, message = "maxRetries must be <= 10")
    @Schema(description = "Maximum number of retry attempts on failure", example = "3")
    @Builder.Default
    private int maxRetries = 3;
}
