package com.jobscheduler.metrics;

import com.jobscheduler.model.JobStatus;
import com.jobscheduler.model.Priority;
import com.jobscheduler.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides aggregated metrics about the job scheduler system.
 * All calculations are performed via optimized DB queries to avoid loading full datasets.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerMetrics {

    private final JobRepository jobRepository;

    /**
     * Returns a comprehensive summary of scheduler health and performance.
     *
     * @return map containing counts by status, success rate, avg execution time, etc.
     */
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();

        long pending = jobRepository.countByStatus(JobStatus.PENDING);
        long running = jobRepository.countByStatus(JobStatus.RUNNING);
        long completed = jobRepository.countByStatus(JobStatus.COMPLETED);
        long failed = jobRepository.countByStatus(JobStatus.FAILED);
        long dead = jobRepository.countByStatus(JobStatus.DEAD);
        long cancelled = jobRepository.countByStatus(JobStatus.CANCELLED);
        long total = pending + running + completed + failed + dead + cancelled;

        double successRate = (total > 0)
                ? Math.round(((double) completed / total) * 10000.0) / 100.0
                : 0.0;

        Double avgMs = jobRepository.findAvgExecutionTimeMs().orElse(0.0);

        long last24h = jobRepository.countJobsSince(LocalDateTime.now().minusHours(24));

        List<String> topFailing = jobRepository.findTopFailingJobTypes()
                .stream()
                .limit(5)
                .map(row -> (String) row[0])
                .collect(Collectors.toList());

        summary.put("totalJobs", total);
        summary.put("pending", pending);
        summary.put("running", running);
        summary.put("completed", completed);
        summary.put("failed", failed);
        summary.put("dead", dead);
        summary.put("cancelled", cancelled);
        summary.put("avgExecutionTimeMs", Math.round(avgMs));
        summary.put("successRate", successRate);
        summary.put("jobsLast24h", last24h);
        summary.put("topFailingJobTypes", topFailing);

        return summary;
    }

    /**
     * Returns the count of PENDING jobs broken down by priority level.
     *
     * @return map of priority name to pending count
     */
    public Map<String, Long> getQueueDepthByPriority() {
        Map<String, Long> depth = new LinkedHashMap<>();
        // Initialize all priorities with 0 to ensure full map even if no jobs exist
        for (Priority p : Priority.values()) {
            depth.put(p.name(), 0L);
        }

        List<Object[]> rows = jobRepository.countPendingByPriority();
        for (Object[] row : rows) {
            Priority priority = (Priority) row[0];
            Long count = (Long) row[1];
            depth.put(priority.name(), count);
        }

        return depth;
    }
}
