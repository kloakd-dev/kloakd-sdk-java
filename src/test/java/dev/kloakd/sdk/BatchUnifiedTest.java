package dev.kloakd.sdk;

import dev.kloakd.sdk.errors.ApiException;
import dev.kloakd.sdk.errors.ConcurrencyLimitException;
import dev.kloakd.sdk.errors.ForbiddenException;
import dev.kloakd.sdk.errors.JobFailedException;
import dev.kloakd.sdk.models.BatchJob;
import dev.kloakd.sdk.models.BatchJobItem;
import dev.kloakd.sdk.models.BatchJobOptions;
import dev.kloakd.sdk.models.UnifiedEscalation;
import dev.kloakd.sdk.models.UnifiedScrapeOptions;
import dev.kloakd.sdk.models.UnifiedScrapeProgress;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static dev.kloakd.sdk.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Batch jobs and the unified scrape, checked against kobe-scraper
 * integration/2026-11-12b (228857dc): kernel/jobs_runs_router.py,
 * application/jobs/batch_control.py, api/v1/endpoints/unified_scraping.py
 * and kernel/sync_handoff.py. Every answer here is a mock's, shaped as the
 * backend's response models.
 */
class BatchUnifiedTest {

    /** application/jobs/batch_control.py PAUSED_MESSAGE */
    private static final String PAUSED_MESSAGE =
            "paused: items that were already running finish; the rest wait until the batch is resumed.";

    private MockWebServer server;
    private Kloakd client;
    private Routes routes;

    @BeforeEach
    void setUp() throws Exception {
        server = startServer();
        routes = routes(server);
        client = client(server);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    /** A client that retries, to show what is not retried. */
    private Kloakd retrying() {
        return Kloakd.builder().apiKey(TEST_API_KEY).organizationId(TEST_ORG_ID)
                .baseUrl(server.url("/").toString()).maxRetries(3).timeout(Duration.ofSeconds(5)).build();
    }

    /** kernel/jobs_runs_router.py BatchJob */
    private static Map<String, Object> batch(String status, String message) {
        var m = new HashMap<String, Object>();
        m.put("id", "b1");
        m.put("name", "Batch 2 URLs");
        m.put("status", status);
        m.put("total_items", 2);
        m.put("completed_items", 1);
        m.put("created_at", "2026-11-12T10:00:00+00:00");
        m.put("message", message);
        return m;
    }

    private static Map<String, Object> detail(String code, String message, Map<String, Object> extra) {
        var d = new HashMap<String, Object>(extra);
        d.put("error", code);
        d.put("message", message);
        return Map.of("detail", d);
    }

    /** kernel/jobs_runs_router.py _batch_transition_refused */
    private static Map<String, Object> refused(String action, String status, String message) {
        return detail("invalid_batch_transition", message, Map.of("action", action, "status", status));
    }

    private static Map<String, Object> concurrentLimit() {
        return detail("concurrent_limit", "Your plan runs 2 batches at once.",
                Map.of("tier", "free", "concurrent_limit", 2, "active_batches", 2));
    }

    private BatchJob control(Kloakd c, String action, String id) {
        BiFunction<Kloakd, String, BatchJob> call = switch (action) {
            case "pause" -> Kloakd::pauseBatchJob;
            case "resume" -> Kloakd::resumeBatchJob;
            default -> Kloakd::cancelBatchJob;
        };
        return call.apply(c, id);
    }

    /** The query string of the latest request. */
    private String query() throws InterruptedException {
        String path = null;
        for (var request = server.takeRequest(0, TimeUnit.MILLISECONDS); request != null;
             request = server.takeRequest(0, TimeUnit.MILLISECONDS)) {
            path = request.getPath();
        }
        assertNotNull(path, "no request was made");
        int q = path.indexOf('?');
        return q < 0 ? "" : path.substring(q + 1);
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Test
    void createBatchJob_sendsTheUrlsAlone() {
        routes.json("POST batch-jobs", 201, batch("pending", null));

        var job = client.createBatchJob(List.of("https://a.example", "https://b.example"));

        assertEquals(new BatchJob("b1", "Batch 2 URLs", "pending", 2, 1, "2026-11-12T10:00:00+00:00", null), job);
        assertEquals(Map.of("urls", List.of("https://a.example", "https://b.example")),
                routes.bodies.get("POST batch-jobs"));
    }

    @Test
    void createBatchJob_sendsEveryFieldTheRouteReads() {
        routes.json("POST batch-jobs", 201, batch("pending", null));

        client.createBatchJob(List.of("https://a.example"), BatchJobOptions.builder()
                .extractionConfig(Map.of("selectors", Map.of("title", "h1")))
                .crawlId("c1").entityType("product").fields(List.of("title", "price")).build());

        assertEquals(Map.of("urls", List.of("https://a.example"),
                "extraction_config", Map.of("selectors", Map.of("title", "h1")),
                "crawl_id", "c1", "entity_type", "product", "fields", List.of("title", "price")),
                routes.bodies.get("POST batch-jobs"));
    }

    @Test
    void createBatchJob_refusalsCarryTheApiCode() {
        routes.json("POST batch-jobs", 403, detail("test_key_not_allowed", "Batch jobs need a live API key.", Map.of()));
        routes.json("POST batch-jobs", 413, detail("batch_too_large", "Your plan allows 100 URLs per batch.",
                Map.of("tier", "free", "max_items", 100, "requested_items", 101)));
        routes.json("POST batch-jobs", 409, concurrentLimit());
        var urls = List.of("https://a.example");

        var forbidden = assertThrows(ForbiddenException.class, () -> client.createBatchJob(urls));
        assertEquals("test_key_not_allowed", forbidden.getCode());
        var tooLarge = assertThrows(ApiException.class, () -> client.createBatchJob(urls));
        assertEquals(413, tooLarge.getStatusCode());
        assertEquals("batch_too_large", tooLarge.getCode());
        var full = assertThrows(ConcurrencyLimitException.class, () -> client.createBatchJob(urls));
        assertEquals(2, full.getConcurrentLimit());
        assertEquals("free", full.getTier());
    }

    @Test
    void createBatchJob_notQueuedIs503DispatchFailedAndIsNotRetried() {
        routes.json("POST batch-jobs", 503, detail("dispatch_failed", "The batch could not be queued.",
                Map.of("batch_job_id", "b1")));

        var ex = assertThrows(ApiException.class, () -> retrying().createBatchJob(List.of("https://a.example")));

        assertEquals(503, ex.getStatusCode());
        assertEquals("dispatch_failed", ex.getCode());
        assertEquals(List.of("POST batch-jobs"), routes.seen);
    }

    // ── read ─────────────────────────────────────────────────────────────────

    @Test
    void getBatchJob_readsTheState() {
        routes.json("GET batch-jobs/b1", 200, batch("paused", PAUSED_MESSAGE));

        var job = client.getBatchJob("b1");

        assertEquals("paused", job.status());
        assertEquals(2, job.totalItems());
        assertEquals(1, job.completedItems());
        assertEquals(PAUSED_MESSAGE, job.message());
    }

    @Test
    void getBatchJob_notYoursIs404() {
        routes.json("GET batch-jobs/nope", 404, Map.of("detail", "Batch job not found"));

        var ex = assertThrows(ApiException.class, () -> client.getBatchJob("nope"));
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    void listBatchJobs_pages() throws Exception {
        var other = batch("completed", null);
        other.put("id", "b0");
        routes.json("GET batch-jobs", 200, Map.of("total", 7, "batch_jobs", List.of(batch("running", null), other)));

        var page = client.listBatchJobs();
        assertEquals("", query(), "no paging is sent unless asked for");
        assertEquals(7, page.total());
        assertEquals(List.of("b1", "b0"), page.batchJobs().stream().map(BatchJob::id).toList());
        assertEquals("completed", page.batchJobs().get(1).status());

        client.listBatchJobs(2, 50);
        assertEquals("page=2&page_size=50", query());
    }

    @Test
    void getBatchJobResults_listsTheItems() throws Exception {
        var failed = new HashMap<String, Object>();
        failed.put("job_id", "j2");
        failed.put("url", "https://b.example");
        failed.put("status", "failed");
        failed.put("extracted_data", null);
        routes.json("GET batch-jobs/b1/results", 200, Map.of("batch_job_id", "b1",
                "results", List.of(Map.of("job_id", "j1", "url", "https://a.example", "status", "completed",
                        "extracted_data", List.of(Map.of("title", "A")),
                        "api_data", Map.of("records", List.of(Map.of("id", 1)))), failed),
                "total_jobs", 2, "completed_jobs", 1, "failed_jobs", 1));

        var results = client.getBatchJobResults("b1");
        assertEquals("", query(), "no limit is sent unless asked for");
        assertEquals("b1", results.batchJobId());
        assertEquals(new BatchJobItem("j1", "https://a.example", "completed", List.of(Map.of("title", "A")),
                Map.of("records", List.of(Map.of("id", 1)))), results.results().get(0));
        assertEquals(new BatchJobItem("j2", "https://b.example", "failed", null, null), results.results().get(1));
        assertEquals(List.of(2, 1, 1), List.of(results.totalJobs(), results.completedJobs(), results.failedJobs()));

        client.getBatchJobResults("b1", 100, 200);
        assertEquals("limit=100&offset=200", query());
    }

    // ── pause, resume, cancel ────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({"pause,paused", "resume,running", "cancel,cancelled"})
    void aControl_postsNoFieldsAndReturnsTheBatch(String action, String status) {
        String message = "pause".equals(action) ? PAUSED_MESSAGE : null;
        routes.json("POST batch-jobs/b1/" + action, 200, batch(status, message));

        var job = control(client, action, "b1");

        assertEquals("b1", job.id());
        assertEquals(status, job.status());
        assertEquals(message, job.message());
        assertEquals(Map.of(), routes.bodies.get("POST batch-jobs/b1/" + action));
    }

    @ParameterizedTest
    @CsvSource({"pause,paused", "resume,running", "cancel,cancelled"})
    void aControl_repeatedIs200AndNotAnError(String action, String status) {
        // batch_control.py: a repeat answers the batch unchanged.
        routes.json("POST batch-jobs/b1/" + action, 200, batch(status, null));

        var first = control(client, action, "b1");
        var second = control(client, action, "b1");

        assertEquals(first, second);
        assertEquals(status, second.status());
        assertEquals(2, routes.seen.size());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "pause|completed|Only a running batch can be paused (current status: completed).",
            "resume|cancelled|Only a paused batch can be resumed (current status: cancelled).",
            "cancel|completed|A batch that has completed cannot be cancelled."})
    void aControl_refusedIs409InvalidBatchTransition(String action, String status, String message) {
        routes.json("POST batch-jobs/b1/" + action, 409, refused(action, status, message));

        var ex = assertThrows(ApiException.class, () -> control(retrying(), action, "b1"));

        assertEquals(409, ex.getStatusCode());
        assertEquals("invalid_batch_transition", ex.getCode());
        assertEquals(message, ex.getMessage());
        assertEquals(1, routes.seen.size(), "a refusal is not retried");
    }

    @Test
    void resumeBatchJob_notQueuedIs503DispatchFailedAndIsNotRetried() {
        routes.json("POST batch-jobs/b1/resume", 503, detail("dispatch_failed",
                "The batch could not be resumed; it is paused again.",
                Map.of("batch_job_id", "b1", "items_not_queued", 2)));

        var ex = assertThrows(ApiException.class, () -> retrying().resumeBatchJob("b1"));

        assertEquals(503, ex.getStatusCode());
        assertEquals("dispatch_failed", ex.getCode());
        assertEquals("The batch could not be resumed; it is paused again.", ex.getMessage());
        assertEquals(List.of("POST batch-jobs/b1/resume"), routes.seen);
    }

    @Test
    void resumeBatchJob_atTheRunningBatchLimitIsAConcurrencyLimit() {
        routes.json("POST batch-jobs/b1/resume", 409, concurrentLimit());

        var ex = assertThrows(ConcurrencyLimitException.class, () -> client.resumeBatchJob("b1"));

        assertEquals("concurrent_limit", ex.getCode());
        assertEquals(2, ex.getConcurrentLimit());
    }

    @ParameterizedTest
    @ValueSource(strings = {"pause", "resume", "cancel"})
    void aControl_onABatchThatIsNotYoursIs404(String action) {
        routes.json("POST batch-jobs/nope/" + action, 404, Map.of("detail", "Batch job not found"));

        var ex = assertThrows(ApiException.class, () -> control(client, action, "nope"));
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    void aBatchIdIsOnePathSegment() throws Exception {
        routes.json("POST batch-jobs/a%2Fb/pause", 200, batch("paused", PAUSED_MESSAGE));

        assertEquals("paused", client.pauseBatchJob("a/b").status());
        assertEquals(List.of("POST batch-jobs/a%2Fb/pause"), routes.seen);
    }

    // ── unified scrape ───────────────────────────────────────────────────────

    /** api/v1/endpoints/unified_scraping.py UnifiedScrapeResponse */
    private static Map<String, Object> unified(String jobId) {
        var m = new HashMap<String, Object>();
        m.put("success", true);
        m.put("job_id", jobId);
        m.put("level", "level2");
        m.put("status", "completed");
        m.put("escalation_path", List.of(Map.of("from", "level1", "to", "level2", "reason", "no records",
                "timestamp", "2026-11-12T10:00:01+00:00")));
        m.put("message", "Scrape completed.");
        m.put("metadata", Map.of("records", 3));
        m.put("auth_required", false);
        m.put("discovery_id", null);
        return m;
    }

    @Test
    void unifiedScrape_sendsTheUrlAloneByDefault() {
        routes.json("POST scraping/unified", 200, unified("u1"));

        var result = client.unifiedScrape("https://x.example");

        assertEquals(Map.of("url", "https://x.example"), routes.bodies.get("POST scraping/unified"));
        assertTrue(result.success());
        assertEquals("u1", result.jobId());
        assertEquals("level2", result.level());
        assertEquals("completed", result.status());
        assertEquals(List.of(new UnifiedEscalation("level1", "level2", "no records", "2026-11-12T10:00:01+00:00")),
                result.escalationPath());
        assertEquals("Scrape completed.", result.message());
        assertEquals(Map.of("records", 3), result.metadata());
        assertFalse(result.authRequired());
        assertNull(result.discoveryId());
    }

    @Test
    void unifiedScrape_sendsEveryFieldTheRouteReads() {
        routes.json("POST scraping/unified", 200, unified("u1"));

        client.unifiedScrape("https://x.example", UnifiedScrapeOptions.builder()
                .prompt("the prices").mode("level2").schema(Map.of("price", "the product's price"))
                .options(Map.of("max_pages", 2)).context(Map.of("site", "shop"))
                .constraints(Map.of("max_seconds", 60)).sessionArtifactId("art-1")
                .pollInterval(Duration.ofMillis(1)).pollTimeout(Duration.ofSeconds(5)).build());

        var expected = new HashMap<String, Object>();
        expected.put("url", "https://x.example");
        expected.put("prompt", "the prices");
        expected.put("mode", "level2");
        expected.put("schema", Map.of("price", "the product's price"));
        expected.put("options", Map.of("max_pages", 2));
        expected.put("context", Map.of("site", "shop"));
        expected.put("constraints", Map.of("max_seconds", 60));
        expected.put("session_artifact_id", "art-1");
        assertEquals(expected, routes.bodies.get("POST scraping/unified"),
                "the poll settings are the SDK's and are not sent");
    }

    @Test
    void unifiedScrape_reportsALoginWall() {
        // unified_scraping.py: the preflight answer when the URL is behind a login.
        var answer = unified("u9");
        answer.put("level", "preflight");
        answer.put("status", "auth_required");
        answer.put("escalation_path", List.of());
        answer.put("metadata", null);
        answer.put("auth_required", true);
        answer.put("discovery_id", "d1");
        routes.json("POST scraping/unified", 200, answer);

        var result = client.unifiedScrape("https://x.example/account");

        assertEquals("preflight", result.level());
        assertEquals("auth_required", result.status());
        assertTrue(result.authRequired());
        assertEquals("d1", result.discoveryId());
        assertEquals(List.of(), result.escalationPath());
        assertNull(result.metadata());
    }

    private static Map<String, Object> handedOff() {
        // kernel/sync_handoff.py: the 202 body
        return Map.of("job_id", "h-1", "status", "running", "reason", "sync_limit_reached",
                "waited_seconds", 85.0,
                "poll_url", "/api/v1/organizations/" + TEST_ORG_ID + "/handoff/h-1",
                "message", "Still running; it continues as a job.");
    }

    @Test
    void unifiedScrape_followsA202HandOff() {
        routes.json("POST scraping/unified", 202, handedOff());
        routes.json("GET handoff/h-1", 200, Map.of("job_id", "h-1", "kind", "x", "status", "running"));
        routes.json("GET handoff/h-1", 200, Map.of("job_id", "h-1", "kind", "x", "status", "completed",
                "result", unifiedNoNulls("u1")));

        var result = client.unifiedScrape("https://x.example", UnifiedScrapeOptions.builder()
                .pollInterval(Duration.ofMillis(1)).pollTimeout(Duration.ofSeconds(5)).build());

        assertEquals("u1", result.jobId());
        assertEquals("level2", result.level());
        assertEquals("completed", result.status());
        assertEquals(List.of("POST scraping/unified", "GET handoff/h-1", "GET handoff/h-1"), routes.seen);
    }

    private static Map<String, Object> unifiedNoNulls(String jobId) {
        var m = unified(jobId);
        m.remove("discovery_id");
        return m;
    }

    @Test
    void unifiedScrape_aHandedOffScrapeThatFailsThrowsJobFailed() {
        routes.json("POST scraping/unified", 202, handedOff());
        routes.json("GET handoff/h-1", 200, Map.of("job_id", "h-1", "status", "failed", "error", "Scrape failed: boom"));

        var ex = assertThrows(JobFailedException.class, () -> client.unifiedScrape("https://x.example",
                UnifiedScrapeOptions.builder().pollInterval(Duration.ofMillis(1)).build()));

        assertEquals("h-1", ex.getJobId());
        assertEquals("Scrape failed: boom", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"test_key_not_allowed", "module_not_entitled"})
    void unifiedScrape_refusalsAreForbiddenWithTheApiCode(String code) {
        // unified_scraping.py unified_scrape_access
        routes.json("POST scraping/unified", 403, detail(code, "Not allowed.", Map.of()));

        var ex = assertThrows(ForbiddenException.class, () -> client.unifiedScrape("https://x.example"));
        assertEquals(code, ex.getCode());
    }

    @Test
    void unifiedScrape_isNotRetriedOnAServerError() {
        routes.json("POST scraping/unified", 500, Map.of("detail", "boom"));

        assertThrows(ApiException.class, () -> retrying().unifiedScrape("https://x.example"));
        assertEquals(List.of("POST scraping/unified"), routes.seen);
    }

    @Test
    void getUnifiedScrape_readsARunningJob() {
        // unified_scraping.py JobStatusResponse and ProgressDetail
        var answer = new HashMap<String, Object>();
        answer.put("id", "u1");
        answer.put("status", "running");
        answer.put("level", "level1");
        answer.put("progress", 40);
        answer.put("progress_detail", Map.of("percentage", 40, "current_step", "extracting",
                "items_found", 4, "items_target", 10, "partial_results", List.of(Map.of("title", "A"))));
        answer.put("created_at", "2026-11-12T10:00:00+00:00");
        answer.put("updated_at", "2026-11-12T10:00:05+00:00");
        answer.put("completed_at", null);
        answer.put("error", null);
        answer.put("result", null);
        answer.put("escalation_path", List.of());
        routes.json("GET scraping/unified/u1", 200, answer);

        var status = client.getUnifiedScrape("u1");

        assertEquals("u1", status.id());
        assertEquals("running", status.status());
        assertEquals("level1", status.level());
        assertEquals(40, status.progress());
        assertEquals(new UnifiedScrapeProgress(40, "extracting", 4, 10, List.of(Map.of("title", "A"))),
                status.progressDetail());
        assertEquals("2026-11-12T10:00:00+00:00", status.createdAt());
        assertEquals("2026-11-12T10:00:05+00:00", status.updatedAt());
        assertNull(status.completedAt());
        assertNull(status.error());
        assertNull(status.result());
        assertEquals(List.of(), status.escalationPath());
    }

    @Test
    void getUnifiedScrape_readsAFinishedJob() {
        var answer = new HashMap<String, Object>();
        answer.put("id", "u1");
        answer.put("status", "completed");
        answer.put("level", "level2");
        answer.put("progress", 100);
        answer.put("progress_detail", null);
        answer.put("created_at", "2026-11-12T10:00:00+00:00");
        answer.put("completed_at", "2026-11-12T10:01:00+00:00");
        answer.put("result", Map.of("data", List.of(Map.of("price", "9.99"))));
        answer.put("escalation_path", List.of(Map.of("from", "level1", "to", "level2", "reason", "blocked",
                "timestamp", "t")));
        routes.json("GET scraping/unified/u1", 200, answer);

        var status = client.getUnifiedScrape("u1");

        assertNull(status.progressDetail());
        assertEquals(Map.of("data", List.of(Map.of("price", "9.99"))), status.result());
        assertEquals("2026-11-12T10:01:00+00:00", status.completedAt());
        assertEquals(List.of(new UnifiedEscalation("level1", "level2", "blocked", "t")), status.escalationPath());
    }

    @Test
    void getUnifiedScrape_notYoursIs404() {
        routes.json("GET scraping/unified/nope", 404, Map.of("detail", "Job nope not found"));

        var ex = assertThrows(ApiException.class, () -> client.getUnifiedScrape("nope"));
        assertEquals(404, ex.getStatusCode());
    }

    // ── the route list ───────────────────────────────────────────────────────

    private static List<String> fixture() throws IOException {
        try (var in = BatchUnifiedTest.class.getResourceAsStream("/routes.tsv")) {
            return List.of(new String(in.readAllBytes()).split("\n"));
        }
    }

    @Test
    void theNewMethodsCallLiveRoutes() throws IOException {
        var live = new HashSet<String>();
        var prefix = "/api/v1/organizations/{organization_id}/";
        for (String line : fixture()) {
            var parts = line.split("\t", 2);
            if (parts.length == 2 && parts[1].startsWith(prefix)) {
                live.add(parts[0] + " " + parts[1].substring(prefix.length()).replaceAll("\\{[^}]+}", "{}"));
            }
        }
        var job = Map.of("id", "b1", "job_id", "u1", "status", "running");
        for (String route : List.of("POST batch-jobs", "GET batch-jobs", "GET batch-jobs/b1",
                "GET batch-jobs/b1/results", "POST batch-jobs/b1/pause", "POST batch-jobs/b1/resume",
                "POST batch-jobs/b1/cancel", "POST scraping/unified", "GET scraping/unified/u1")) {
            routes.json(route, 200, job);
        }

        client.createBatchJob(List.of("https://a.example"));
        client.listBatchJobs();
        client.getBatchJob("b1");
        client.getBatchJobResults("b1");
        client.pauseBatchJob("b1");
        client.resumeBatchJob("b1");
        client.cancelBatchJob("b1");
        client.unifiedScrape("https://x.example");
        client.getUnifiedScrape("u1");

        var called = new ArrayList<String>();
        for (String seen : routes.seen) called.add(seen.replace("/b1", "/{}").replace("/u1", "/{}"));
        assertEquals(List.of("POST batch-jobs", "GET batch-jobs", "GET batch-jobs/{}", "GET batch-jobs/{}/results",
                "POST batch-jobs/{}/pause", "POST batch-jobs/{}/resume", "POST batch-jobs/{}/cancel",
                "POST scraping/unified", "GET scraping/unified/{}"), called);
        assertEquals(List.of(), called.stream().filter(c -> !live.contains(c)).toList(),
                "calls to routes that do not exist");
    }

    @Test
    void theRouteListHas280RoutesAndNoPerformanceRoutes() throws IOException {
        // kobe-scraper integration/2026-11-12b (228857dc) removed its 12
        // performance/... routes; monitoring/performance is another route.
        var lines = fixture().stream().filter(l -> !l.isBlank()).toList();
        assertEquals(280, lines.size());
        assertEquals(List.of(), lines.stream()
                .filter(l -> l.contains("{organization_id}/performance/")).toList());
    }
}
