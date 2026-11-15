package dev.kloakd.sdk.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A job's data use against its limit, read from a status answer:
 * {@code GET webgrph/crawl/{id}} or {@code GET webgrph/crawl-extract/{job_id}}
 * ({@code data_used_mb}, {@code max_data_mb}, {@code include_resources};
 * cost-control contract of 15 Nov 2026, section 3). A job that reaches its
 * limit ends partial with {@code partial_reason} {@code data_limit}.
 *
 * @param dataUsedMb       MB used so far, or null when not reported
 * @param maxDataMb        the job's data limit in MB, or null when it has none
 * @param includeResources the page extras the job loads (empty: none, the default), or null when not reported
 */
public record CrawlDataUse(Double dataUsedMb, Double maxDataMb, List<String> includeResources) {

    /** Read the three fields from a status map (for example {@code webgrph().getCrawlExtract(jobId)}). */
    public static CrawlDataUse from(Map<String, Object> status) {
        if (status == null) return new CrawlDataUse(null, null, null);
        List<String> include = null;
        if (status.get("include_resources") instanceof List<?> list) {
            var out = new ArrayList<String>();
            for (var item : list) if (item instanceof String s) out.add(s);
            include = Collections.unmodifiableList(out);
        }
        return new CrawlDataUse(
                status.get("data_used_mb") instanceof Number n ? n.doubleValue() : null,
                status.get("max_data_mb") instanceof Number n ? n.doubleValue() : null,
                include);
    }
}
