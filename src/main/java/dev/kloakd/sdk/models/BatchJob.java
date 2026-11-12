package dev.kloakd.sdk.models;

/**
 * A batch job (kobe-scraper kernel/jobs_runs_router.py BatchJob).
 *
 * @param id             the batch's id
 * @param name           the batch's name
 * @param status         {@code pending}, {@code running}, {@code paused}, {@code held_quota},
 *                       {@code completed}, {@code failed} or {@code cancelled}. A paused batch
 *                       starts no further item until it is resumed; items that were already
 *                       running finish
 * @param totalItems     the batch's URLs
 * @param completedItems items that completed or failed
 * @param createdAt      ISO time
 * @param message        the API's note for a paused batch or one held for quota; null otherwise
 */
public record BatchJob(
        String id,
        String name,
        String status,
        int totalItems,
        int completedItems,
        String createdAt,
        String message
) {}
