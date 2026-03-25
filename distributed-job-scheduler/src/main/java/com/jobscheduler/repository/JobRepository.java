package com.jobscheduler.repository;

import com.jobscheduler.model.Job;
import com.jobscheduler.model.JobStatus;
import com.jobscheduler.model.Priority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Job entity providing custom queries for scheduler polling and filtering.
 */
@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {

    /**
     * Finds jobs that are due for execution, ordered by priority weight descending then
     * nextRunAt ascending. Used by the scheduler tick to pick up work.
     *
     * @param now       current timestamp
     * @param status    job status to filter by (PENDING)
     * @param pageable  pagination/limit
     * @return list of eligible jobs
     */
    @Query("SELECT j FROM Job j WHERE j.nextRunAt <= :now AND j.status = :status " +
           "ORDER BY j.priority DESC, j.nextRunAt ASC")
    List<Job> findDueJobs(@Param("now") LocalDateTime now,
                          @Param("status") JobStatus status,
                          Pageable pageable);

    /**
     * Filtered listing for the API with optional criteria.
     */
    @Query("SELECT j FROM Job j WHERE " +
           "(:status IS NULL OR j.status = :status) AND " +
           "(:priority IS NULL OR j.priority = :priority) AND " +
           "(:type IS NULL OR j.type = :type) AND " +
           "(:from IS NULL OR j.createdAt >= :from) AND " +
           "(:to IS NULL OR j.createdAt <= :to)")
    Page<Job> findWithFilters(@Param("status") JobStatus status,
                              @Param("priority") Priority priority,
                              @Param("type") String type,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to,
                              Pageable pageable);

    /**
     * Count jobs by status — used for metrics.
     */
    long countByStatus(JobStatus status);

    /**
     * Count jobs created within the last N hours — used for metrics.
     */
    @Query("SELECT COUNT(j) FROM Job j WHERE j.createdAt >= :since")
    long countJobsSince(@Param("since") LocalDateTime since);

    /**
     * Find top failing job types with their failure counts.
     */
    @Query("SELECT j.type, COUNT(j) as cnt FROM Job j WHERE j.status IN ('FAILED', 'DEAD') " +
           "GROUP BY j.type ORDER BY cnt DESC")
    List<Object[]> findTopFailingJobTypes();

    /**
     * Average execution time in milliseconds for COMPLETED jobs.
     */
    @Query("SELECT AVG(EXTRACT(EPOCH FROM (j.completedAt - j.startedAt)) * 1000) " +
           "FROM Job j WHERE j.completedAt IS NOT NULL AND j.startedAt IS NOT NULL")
    Optional<Double> findAvgExecutionTimeMs();

    /**
     * Count jobs grouped by priority — used for queue-depth metrics.
     */
    @Query("SELECT j.priority, COUNT(j) FROM Job j WHERE j.status = 'PENDING' GROUP BY j.priority")
    List<Object[]> countPendingByPriority();
}
