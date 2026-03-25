package com.jobscheduler.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Paginated response wrapper for job listing.
 */
@Data
@Builder
public class PagedJobResponse {
    private List<JobResponse> jobs;
    private long totalCount;
    private int page;
    private int size;
    private int totalPages;
}
