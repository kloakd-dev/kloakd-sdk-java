package dev.kloakd.sdk.models;

import java.util.List;

/**
 * One page of the organisation's batch jobs, newest first (kobe-scraper
 * kernel/jobs_runs_router.py BatchJobsList).
 *
 * @param total     all of the organisation's batch jobs, not only this page
 * @param batchJobs this page
 */
public record BatchJobsPage(int total, List<BatchJob> batchJobs) {}
