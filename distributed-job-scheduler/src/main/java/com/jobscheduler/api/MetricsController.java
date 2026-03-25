package com.jobscheduler.api;

import com.jobscheduler.metrics.SchedulerMetrics;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for scheduler metrics and observability.
 */
@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
@Tag(name = "Metrics", description = "Scheduler observability and queue depth endpoints")
public class MetricsController {

    private final SchedulerMetrics schedulerMetrics;

    /**
     * Returns a comprehensive summary of scheduler health, including job counts
     * by status, success rate, average execution time, and top failing job types.
     *
     * @return metrics summary map
     */
    @GetMapping("/summary")
    @Operation(summary = "Get scheduler metrics summary",
               description = "Returns total job counts, success rate, avg execution time, and top failing types.")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(schedulerMetrics.getSummary());
    }

    /**
     * Returns the current queue depth of PENDING jobs broken down by priority level.
     *
     * @return map of priority name to count
     */
    @GetMapping("/queue-depth")
    @Operation(summary = "Get queue depth by priority",
               description = "Returns the number of PENDING jobs for each priority level.")
    public ResponseEntity<Map<String, Long>> getQueueDepth() {
        return ResponseEntity.ok(schedulerMetrics.getQueueDepthByPriority());
    }
}
