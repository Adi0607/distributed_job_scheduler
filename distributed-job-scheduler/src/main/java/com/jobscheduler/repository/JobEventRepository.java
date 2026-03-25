package com.jobscheduler.repository;

import com.jobscheduler.model.JobEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for JobEvent entity providing job execution history queries.
 */
@Repository
public interface JobEventRepository extends JpaRepository<JobEvent, UUID> {

    /**
     * Fetches all events for a given job ordered by occurrence time ascending.
     *
     * @param jobId the job ID
     * @return list of events ordered by occurredAt ASC
     */
    List<JobEvent> findByJobIdOrderByOccurredAtAsc(UUID jobId);
}
