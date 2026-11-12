package dev.kloakd.sdk.models;

import java.util.List;

/**
 * A batch's items and their results (kobe-scraper kernel/jobs_runs_router.py
 * BatchJobResults). The three counts are of the whole batch when a limit
 * was given; without a limit they count the items returned.
 *
 * @param batchJobId    the batch's id
 * @param results       the items asked for: all of them, or {@code limit} from {@code offset},
 *                      in creation order
 * @param totalJobs     items
 * @param completedJobs items that completed
 * @param failedJobs    items that failed
 */
public record BatchJobResults(
        String batchJobId,
        List<BatchJobItem> results,
        int totalJobs,
        int completedJobs,
        int failedJobs
) {}
