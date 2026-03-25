package com.jobscheduler.model;

/**
 * Priority levels for scheduled jobs. Higher weight = higher priority in execution order.
 */
public enum Priority {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int weight;

    Priority(int weight) {
        this.weight = weight;
    }

    /**
     * Returns the integer weight used for ordering jobs by priority.
     *
     * @return the priority weight
     */
    public int getWeight() {
        return weight;
    }
}
