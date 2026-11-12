package dev.kloakd.sdk.models;

import java.util.Map;

/**
 * One item (URL) of a batch, as {@code GET batch-jobs/{batch_job_id}/results} lists it.
 *
 * @param jobId         the item's job id
 * @param url           the item's URL
 * @param status        {@code pending}, {@code running}, {@code held_quota}, {@code completed},
 *                      {@code failed} or {@code cancelled}
 * @param extractedData what the item extracted (a list or a map); null until it completes
 * @param apiData       records of a detected API the item's URL names; null otherwise
 */
public record BatchJobItem(
        String jobId,
        String url,
        String status,
        Object extractedData,
        Map<String, Object> apiData
) {}
