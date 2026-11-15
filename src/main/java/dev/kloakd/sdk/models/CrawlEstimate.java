package dev.kloakd.sdk.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a crawl or crawl-extract would do, before it starts
 * ({@code POST webgrph/crawl/estimate} and {@code POST webgrph/crawl-extract/estimate};
 * contract of 14 Nov 2026, section 1). The estimate is not metered: it costs
 * no quota and takes no work slot.
 *
 * <p>A crawl maps the site's pages and links; crawl-extract also returns the
 * items on each page as rows.
 *
 * @param mode             {@code crawl} or {@code crawl_extract}
 * @param url              the start URL
 * @param maxDepth         the depth the crawl would use, after clamping to the plan
 * @param maxPages         the page limit the crawl would use, after clamping to the plan
 * @param planLimits       the plan's limits ({@code plan_limits}), or null when not sent
 * @param clamped          true when a requested value was lowered to the plan's limit
 * @param site             what the probe of the site found, or null when not sent
 * @param estPages         the estimated pages to fetch ({@code est_pages}), or null when not sent
 * @param willHitPageCap   true when the site seems larger than {@code maxPages}
 * @param timeBudgetS      the time budget in seconds the crawl will use, or null
 * @param estDurationS     the estimated duration in seconds ({@code est_duration_s}), or null when not sent
 * @param estGb            the estimated data use in GB
 * @param gbRemaining      the organisation's GB remaining, or null when unknown
 * @param willExhaustQuota true when the estimate is more than what remains
 * @param warning          a plain sentence to show the user, or null
 * @param explanation      what this mode does, in the API's words
 * @param includeResources the page extras the estimate assumed ({@code include_resources}; empty: none,
 *                         the default), or null when not sent
 * @param data             the data the job would use ({@code data}), or null when not sent
 * @param shareOfRemaining the share of the remaining allowance it would use, as fractions
 *                         ({@code share_of_remaining}: 0.04 is 4%), or null when not sent or unknown
 * @param consentRequired  true when the job is large against what remains: a dashboard session must
 *                         start it with {@code estimateId} and {@code costAcknowledged}
 * @param risks            plain sentences about what may go wrong or cost more (empty when none sent)
 * @param suggestedMaxDataMb a data limit to start the job with ({@code suggested_max_data_mb}: the high
 *                         estimate, rounded up), or null when not sent
 * @param estimateId       an opaque token for this estimate, valid 30 minutes ({@code estimate_id}),
 *                         or null when not sent
 */
public record CrawlEstimate(
        String mode,
        String url,
        int maxDepth,
        int maxPages,
        PlanLimits planLimits,
        boolean clamped,
        Site site,
        Pages estPages,
        boolean willHitPageCap,
        Integer timeBudgetS,
        Range estDurationS,
        double estGb,
        Double gbRemaining,
        boolean willExhaustQuota,
        String warning,
        String explanation,
        List<String> includeResources,
        DataUse data,
        DataRange shareOfRemaining,
        boolean consentRequired,
        List<String> risks,
        Double suggestedMaxDataMb,
        String estimateId
) {

    /** The 14 Nov components only (no data, consent or estimate id). */
    public CrawlEstimate(String mode, String url, int maxDepth, int maxPages, PlanLimits planLimits,
            boolean clamped, Site site, Pages estPages, boolean willHitPageCap, Integer timeBudgetS,
            Range estDurationS, double estGb, Double gbRemaining, boolean willExhaustQuota, String warning,
            String explanation) {
        this(mode, url, maxDepth, maxPages, planLimits, clamped, site, estPages, willHitPageCap, timeBudgetS,
                estDurationS, estGb, gbRemaining, willExhaustQuota, warning, explanation,
                null, null, null, false, List.of(), null, null);
    }

    /**
     * A data range in MB (or, for {@code shareOfRemaining}, a fraction).
     *
     * @param low     the low estimate, or null when the API gives none (only {@code total_mb} has one)
     * @param typical the typical estimate
     * @param high    the high estimate
     */
    public record DataRange(Double low, double typical, double high) {}

    /**
     * The data the job would use ({@code data}; cost-control contract of 15 Nov 2026, section 4).
     *
     * @param source             where the page weight came from: {@code site_profile} (measured pages
     *                           of the host), {@code root_page} (the start page) or {@code typical}
     * @param perPageMb          MB per page with the chosen extras ({@code per_page_mb})
     * @param totalMb            MB for the whole job ({@code total_mb})
     * @param totalMbAllResources MB for the whole job if every extra were loaded
     * @param savingMb           MB saved by leaving the other extras out ({@code saving_mb})
     * @param byCategoryMbPerPage MB per page by category: document, script, data, images, media,
     *                           fonts, stylesheets, tracking
     * @param savingByCategoryMb MB the job saves by leaving each extra out
     */
    public record DataUse(String source, DataRange perPageMb, DataRange totalMb, DataRange totalMbAllResources,
            DataRange savingMb, Map<String, Double> byCategoryMbPerPage, Map<String, Double> savingByCategoryMb) {}

    /**
     * The plan's crawl limits ({@code plan_limits}).
     *
     * @param maxDepth the plan's deepest crawl
     * @param maxPages the plan's most pages
     * @param planTier the plan's tier, for example {@code free}
     */
    public record PlanLimits(int maxDepth, int maxPages, String planTier) {}

    /**
     * What the probe of the site found ({@code site}).
     *
     * @param reachable     whether the start page answered
     * @param sitemapPages  pages listed in the sitemap, or null when no sitemap was found
     * @param homepageLinks same-host links on the start page, or null when it was not fetched
     * @param probeError    why the probe failed, or null
     */
    public record Site(boolean reachable, Integer sitemapPages, Integer homepageLinks, String probeError) {}

    /**
     * The estimated pages to fetch ({@code est_pages}).
     *
     * @param low    the low estimate
     * @param high   the high estimate (never above {@code maxPages})
     * @param source {@code sitemap}, {@code homepage_links} or {@code limit} (only the cap is known)
     */
    public record Pages(int low, int high, String source) {}

    /**
     * A range in seconds ({@code est_duration_s}).
     *
     * @param low  the low estimate
     * @param high the high estimate (never above the time budget)
     */
    public record Range(double low, double high) {}

    /** Read an estimate from the API's answer. */
    public static CrawlEstimate from(Map<String, Object> m) {
        var limits = map(m.get("plan_limits"));
        var site = map(m.get("site"));
        var pages = map(m.get("est_pages"));
        var duration = map(m.get("est_duration_s"));
        return new CrawlEstimate(
                str(m, "mode"), str(m, "url"), integer(m, "max_depth"), integer(m, "max_pages"),
                limits == null ? null
                        : new PlanLimits(integer(limits, "max_depth"), integer(limits, "max_pages"), str(limits, "plan_tier")),
                bool(m, "clamped"),
                site == null ? null
                        : new Site(bool(site, "reachable"), optInt(site, "sitemap_pages"),
                                optInt(site, "homepage_links"), str(site, "probe_error")),
                pages == null ? null : new Pages(integer(pages, "low"), integer(pages, "high"), str(pages, "source")),
                bool(m, "will_hit_page_cap"),
                optInt(m, "time_budget_s"),
                duration == null ? null : new Range(dbl(duration, "low"), dbl(duration, "high")),
                dbl(m, "est_gb"),
                optDbl(m, "gb_remaining"),
                bool(m, "will_exhaust_quota"),
                str(m, "warning"),
                str(m, "explanation"),
                strings(m.get("include_resources")),
                dataUse(map(m.get("data"))),
                range(map(m.get("share_of_remaining"))),
                bool(m, "consent_required"),
                m.get("risks") instanceof List<?> ? strings(m.get("risks")) : List.of(),
                optDbl(m, "suggested_max_data_mb"),
                str(m, "estimate_id"));
    }

    private static DataUse dataUse(Map<String, Object> d) {
        if (d == null) return null;
        return new DataUse(str(d, "source"), range(map(d.get("per_page_mb"))), range(map(d.get("total_mb"))),
                range(map(d.get("total_mb_all_resources"))), range(map(d.get("saving_mb"))),
                mbMap(map(d.get("by_category_mb_per_page"))), mbMap(map(d.get("saving_by_category_mb"))));
    }

    private static DataRange range(Map<String, Object> r) {
        return r == null ? null : new DataRange(optDbl(r, "low"), dbl(r, "typical"), dbl(r, "high"));
    }

    private static Map<String, Double> mbMap(Map<String, Object> m) {
        if (m == null) return Map.of();
        var out = new LinkedHashMap<String, Double>();
        for (var e : m.entrySet()) if (e.getValue() instanceof Number n) out.put(e.getKey(), n.doubleValue());
        return Collections.unmodifiableMap(out);
    }

    private static List<String> strings(Object v) {
        if (!(v instanceof List<?> list)) return null;
        var out = new ArrayList<String>();
        for (var item : list) if (item instanceof String s) out.add(s);
        return Collections.unmodifiableList(out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object v) {
        return v instanceof Map<?, ?> mm ? (Map<String, Object>) mm : null;
    }

    private static String str(Map<String, Object> m, String k) {
        return m.get(k) instanceof String s ? s : null;
    }

    private static boolean bool(Map<String, Object> m, String k) {
        return Boolean.TRUE.equals(m.get(k));
    }

    private static int integer(Map<String, Object> m, String k) {
        return m.get(k) instanceof Number n ? n.intValue() : 0;
    }

    private static Integer optInt(Map<String, Object> m, String k) {
        return m.get(k) instanceof Number n ? n.intValue() : null;
    }

    private static double dbl(Map<String, Object> m, String k) {
        return m.get(k) instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static Double optDbl(Map<String, Object> m, String k) {
        return m.get(k) instanceof Number n ? n.doubleValue() : null;
    }
}
