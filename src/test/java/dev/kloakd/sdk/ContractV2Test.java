package dev.kloakd.sdk;

import dev.kloakd.sdk.errors.ApiException;
import dev.kloakd.sdk.errors.ForbiddenException;
import dev.kloakd.sdk.errors.JobFailedException;
import dev.kloakd.sdk.errors.JobTimeoutException;
import dev.kloakd.sdk.errors.KloakdException;
import dev.kloakd.sdk.models.CrawlOptions;
import dev.kloakd.sdk.models.ExportDownload;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static dev.kloakd.sdk.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * After 0.6.0: crawl-extract, its downloads and the batch export. The route
 * list is kobe-scraper main 6bd19e9b's. Every answer here is a mock's.
 */
class ContractV2Test {

    private static final String CE = "webgrph/crawl-extract";
    private static final String NDJSON = "{\"title\":\"A\"}\n{\"title\":\"B\"}\n";

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

    private static MockResponse file(String contentType, String filename, String jobStatus, Buffer body) {
        var r = new MockResponse().setResponseCode(200).addHeader("Content-Type", contentType)
                .addHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"").setBody(body);
        return jobStatus == null ? r : r.addHeader("X-Job-Status", jobStatus);
    }

    private static Buffer text(String s) {
        return new Buffer().writeUtf8(s);
    }

    private String query(String name) throws InterruptedException {
        var request = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request, "no request was made");
        return request.getRequestUrl().queryParameter(name);
    }

    // ── route list ───────────────────────────────────────────────────────────

    @Test
    void theRouteListHasTheNewRoutesAndNotNexusExecute() throws IOException {
        List<String> lines;
        try (var in = getClass().getResourceAsStream("/routes.tsv")) {
            lines = List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"));
        }
        String p = "\t/api/v1/organizations/{organization_id}/";
        for (String route : List.of("POST" + p + CE, "GET" + p + CE + "/{job_id}",
                "GET" + p + CE + "/{job_id}/results", "GET" + p + CE + "/{job_id}/pages",
                "GET" + p + CE + "/{job_id}/export", "POST" + p + CE + "/{job_id}/cancel",
                "GET" + p + "batch-jobs/{batch_job_id}/export",
                "POST" + p + "webgrph/crawl/{crawl_id}/continue", "POST" + p + "fetchyr/login-sessions",
                "GET" + p + "fetchyr/sessions/{artifact_id}",
                "POST" + p + "fetchyr/sessions/{artifact_id}/resume")) {
            assertTrue(lines.contains(route), route);
        }
        assertFalse(lines.contains("POST" + p + "nexus/execute"), "POST nexus/execute was removed from the API");
    }

    // ── start ────────────────────────────────────────────────────────────────

    @Test
    void startCrawlExtract_sendsOnlyTheUrlByDefault() {
        routes.json("POST " + CE, 202, Map.of("success", true, "job_id", "job-1", "crawl_id", "cr-1",
                "url", "https://example.com", "status", "queued",
                "limits", Map.of("max_pages", 100, "max_depth", 3),
                "links", Map.of("status", "/x/status", "export", Map.of("csv", "/x/export?format=csv"))));

        var job = client.webgrph().startCrawlExtract("https://example.com");

        assertEquals("job-1", job.get("job_id"));
        assertEquals("cr-1", job.get("crawl_id"));
        assertEquals("queued", job.get("status"));
        assertEquals(Map.of("max_pages", 100, "max_depth", 3), job.get("limits"));
        assertEquals(Map.of("url", "https://example.com"), routes.bodies.get("POST " + CE));
        assertEquals(List.of("POST " + CE), routes.seen, "the 202 is the answer, not a hand-off to poll");
    }

    @Test
    void startCrawlExtract_sendsTheCrawlOptionsAndTheExtractObject() {
        routes.json("POST " + CE, 202, Map.of("success", true, "job_id", "job-1"));
        var extract = Map.<String, Object>of("schema", Map.of("item_selector", "li.product",
                "fields", List.of(Map.of("name", "title", "selector", "h3"))));

        client.webgrph().startCrawlExtract("https://example.com", CrawlOptions.builder()
                .maxDepth(2).maxPages(200).includeExternalLinks(true).sessionArtifactId("art-1")
                .scope(Map.of("same_host", true)).budget(Map.of("max_seconds", 60)).captureMode("content")
                .buildAsSet(), extract);

        assertEquals(Map.of("url", "https://example.com", "max_depth", 2, "max_pages", 200,
                "include_external_links", true, "session_artifact_id", "art-1",
                "scope", Map.of("same_host", true), "budget", Map.of("max_seconds", 60),
                "capture_mode", "content", "extract", extract), routes.bodies.get("POST " + CE));
    }

    @Test
    void startCrawlExtract_aRefusalIsATypedError() {
        routes.json("POST " + CE, 403, Map.of("detail", Map.of("error", "test_key_not_allowed",
                "message", "Crawl-extract jobs need a live key.")));

        var ex = assertThrows(ForbiddenException.class, () -> client.webgrph().startCrawlExtract("https://example.com"));
        assertEquals("test_key_not_allowed", ex.getCode());
        assertEquals("Crawl-extract jobs need a live key.", ex.getMessage());
    }

    // ── status and waiting ───────────────────────────────────────────────────

    @Test
    void getCrawlExtract_returnsTheJobsState() {
        routes.json("GET " + CE + "/job-1", 200, Map.of("job_id", "job-1", "crawl_id", "cr-1",
                "status", "extracting", "cancel_requested", false,
                "progress", Map.of("pages_crawled", 12, "pages_total", 40, "pages_extracted", 5,
                        "pages_failed", 1, "items", 80),
                "pages_by_method", Map.of("schema", 5), "shapes", List.of(Map.of("shape", "s1", "rows", 80))));

        var status = client.webgrph().getCrawlExtract("job-1");

        assertEquals("extracting", status.get("status"));
        assertEquals(80, ((Map<?, ?>) status.get("progress")).get("items"));
        assertEquals(List.of(Map.of("shape", "s1", "rows", 80)), status.get("shapes"));
    }

    @Test
    void getCrawlExtract_anUnknownJobIs404AndTheIdIsEncoded() {
        var ex = assertThrows(KloakdException.class, () -> client.webgrph().getCrawlExtract("a/b"));
        assertEquals(404, ex.getStatusCode());
        assertEquals(List.of("GET " + CE + "/a%2Fb"), routes.seen);
    }

    @Test
    void waitForCrawlExtract_pollsUntilTheJobEnds() {
        routes.json("GET " + CE + "/job-1", 200, Map.of("job_id", "job-1", "status", "crawling"));
        routes.json("GET " + CE + "/job-1", 200, Map.of("job_id", "job-1", "status", "extracting"));
        routes.json("GET " + CE + "/job-1", 200, Map.of("job_id", "job-1", "status", "completed"));

        var status = client.webgrph().waitForCrawlExtract("job-1", Duration.ofMillis(1), Duration.ofSeconds(5));

        assertEquals("completed", status.get("status"));
        assertEquals(3, routes.seen.size());
    }

    @Test
    void waitForCrawlExtract_aJobStillRunningIsATimeoutWithItsId() {
        routes.json("GET " + CE + "/job-1", 200, Map.of("job_id", "job-1", "status", "crawling"));

        var ex = assertThrows(JobTimeoutException.class,
                () -> client.webgrph().waitForCrawlExtract("job-1", Duration.ofMillis(1), Duration.ofMillis(20)));
        assertEquals("job-1", ex.getJobId());
    }

    @Test
    void crawlExtract_startsWaitsAndReturnsTheLastState() {
        routes.json("POST " + CE, 202, Map.of("success", true, "job_id", "job-1"));
        routes.json("GET " + CE + "/job-1", 200, Map.of("job_id", "job-1", "status", "queued"));
        routes.json("GET " + CE + "/job-1", 200, Map.of("job_id", "job-1", "status", "completed"));

        var status = client.webgrph().crawlExtract("https://example.com", null, null,
                Duration.ofMillis(1), Duration.ofSeconds(5));

        assertEquals("completed", status.get("status"));
        assertEquals(List.of("POST " + CE, "GET " + CE + "/job-1", "GET " + CE + "/job-1"), routes.seen);
    }

    @Test
    void crawlExtract_aFailedJobThrowsWithItsErrorAndId() {
        routes.json("POST " + CE, 202, Map.of("success", true, "job_id", "job-1"));
        routes.json("GET " + CE + "/job-1", 200, Map.of("job_id", "job-1", "status", "failed",
                "error", "the crawl found no pages", "error_code", "crawl_failed"));

        var ex = assertThrows(JobFailedException.class, () -> client.webgrph().crawlExtract(
                "https://example.com", null, null, Duration.ofMillis(1), Duration.ofSeconds(5)));
        assertEquals("job-1", ex.getJobId());
        assertEquals("the crawl found no pages", ex.getMessage());
    }

    @Test
    void crawlExtract_aCancelledJobIsReturned() {
        routes.json("POST " + CE, 202, Map.of("success", true, "job_id", "job-1"));
        routes.json("GET " + CE + "/job-1", 200, Map.of("job_id", "job-1", "status", "cancelled"));

        assertEquals("cancelled", client.webgrph().crawlExtract("https://example.com", null, null,
                Duration.ofMillis(1), Duration.ofSeconds(5)).get("status"));
    }

    @Test
    void crawlExtract_anAnswerWithoutAJobIdIsAnError() {
        routes.json("POST " + CE, 202, Map.of("success", true));

        assertThrows(KloakdException.class, () -> client.webgrph().crawlExtract("https://example.com", null, null));
        assertEquals(List.of("POST " + CE), routes.seen);
    }

    // ── rows and pages ───────────────────────────────────────────────────────

    @Test
    void getCrawlExtractResults_sendsTheCursorLimitAndFilters() throws Exception {
        routes.json("GET " + CE + "/job-1/results", 200, Map.of("job_id", "job-1", "rows", List.of()));

        client.webgrph().getCrawlExtractResults("job-1", "c-2", 500, "s1", "product", "item");

        var url = server.takeRequest(1, TimeUnit.SECONDS).getRequestUrl();
        assertEquals("c-2", url.queryParameter("cursor"));
        assertEquals("500", url.queryParameter("limit"));
        assertEquals("s1", url.queryParameter("shape"));
        assertEquals("product", url.queryParameter("page_type"));
        assertEquals("item", url.queryParameter("kind"));
        assertEquals(List.of("GET " + CE + "/job-1/results"), routes.seen);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getCrawlExtractResults_sendsNoQueryByDefaultAndReturnsTheRows() throws Exception {
        routes.json("GET " + CE + "/job-1/results", 200, Map.of("job_id", "job-1", "status", "completed",
                "total", 1, "count", 1, "rows", List.of(Map.of("page_url", "https://example.com/a",
                        "page_type", "product", "row_kind", "item", "item_index", 0,
                        "data", Map.of("title", "A"), "shape", "s1"))));

        var page = client.webgrph().getCrawlExtractResults("job-1", null, 0);

        assertEquals(1, page.get("total"));
        assertEquals(Map.of("title", "A"), ((List<Map<String, Object>>) page.get("rows")).get(0).get("data"));
        assertNull(page.get("next_cursor"));
        assertNull(server.takeRequest(1, TimeUnit.SECONDS).getRequestUrl().query());
    }

    @Test
    void getCrawlExtractResults_aLimitOutOfRangeIsRefusedBeforeTheRequest() {
        assertThrows(IllegalArgumentException.class, () -> client.webgrph().getCrawlExtractResults("job-1", null, 1001));
        assertThrows(IllegalArgumentException.class, () -> client.webgrph().getCrawlExtractResults("job-1", null, -1));
        assertThrows(IllegalArgumentException.class, () -> client.webgrph().getCrawlExtractPages("job-1", 1001, 0, null));
        assertThrows(IllegalArgumentException.class, () -> client.webgrph().getCrawlExtractPages("job-1", 0, -1, null));
        assertEquals(List.of(), routes.seen);
    }

    @Test
    void crawlExtractAllResults_followsTheCursorToTheEnd() throws Exception {
        routes.json("GET " + CE + "/job-1/results", 200, Map.of("rows", List.of(Map.of("item_index", 0),
                Map.of("item_index", 1)), "next_cursor", "c-2"));
        routes.json("GET " + CE + "/job-1/results", 200, Map.of("rows", List.of(Map.of("item_index", 2))));

        var rows = client.webgrph().crawlExtractAllResults("job-1", null, null, "item");

        assertEquals(List.of(0, 1, 2), rows.stream().map(r -> r.get("item_index")).toList());
        var first = server.takeRequest(1, TimeUnit.SECONDS).getRequestUrl();
        assertNull(first.queryParameter("cursor"));
        assertEquals("1000", first.queryParameter("limit"));
        assertEquals("item", first.queryParameter("kind"));
        var second = server.takeRequest(1, TimeUnit.SECONDS).getRequestUrl();
        assertEquals("c-2", second.queryParameter("cursor"));
        assertEquals("item", second.queryParameter("kind"));
        assertEquals(2, routes.seen.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getCrawlExtractPages_sendsLimitOffsetAndStatus() throws Exception {
        routes.json("GET " + CE + "/job-1/pages", 200, Map.of("job_id", "job-1", "status", "completed",
                "count", 1, "offset", 50, "pages", List.of(Map.of("page_seq", 51,
                        "page_url", "https://example.com/x", "status", "failed", "method", "schema",
                        "item_count", 0, "detail", "no items matched"))));

        var pages = client.webgrph().getCrawlExtractPages("job-1", 25, 50, "failed");

        assertEquals(50, pages.get("offset"));
        assertEquals("no items matched", ((List<Map<String, Object>>) pages.get("pages")).get(0).get("detail"));
        var url = server.takeRequest(1, TimeUnit.SECONDS).getRequestUrl();
        assertEquals("25", url.queryParameter("limit"));
        assertEquals("50", url.queryParameter("offset"));
        assertEquals("failed", url.queryParameter("status"));
    }

    // ── export and download ──────────────────────────────────────────────────

    @Test
    void exportCrawlExtract_streamsTheBodyWithWhatTheHeadersSay() throws Exception {
        routes.add("GET " + CE + "/job-1/export", file("application/x-ndjson", "crawl-extract-job-1.ndjson",
                "extracting", text(NDJSON)));

        try (ExportDownload export = client.webgrph().exportCrawlExtract("job-1", "ndjson", "s1", "product", "item")) {
            assertEquals("application/x-ndjson", export.contentType());
            assertEquals("crawl-extract-job-1.ndjson", export.filename());
            assertEquals("extracting", export.jobStatus());
            assertEquals(NDJSON, new String(export.body().readAllBytes(), StandardCharsets.UTF_8));
        }
        var url = server.takeRequest(1, TimeUnit.SECONDS).getRequestUrl();
        assertEquals("ndjson", url.queryParameter("format"));
        assertEquals("s1", url.queryParameter("shape"));
        assertEquals("product", url.queryParameter("page_type"));
        assertEquals("item", url.queryParameter("kind"));
    }

    @Test
    void exportCrawlExtract_theCsvKeepsItsByteOrderMark() {
        byte[] csv = new Buffer().write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF})
                .writeUtf8("title\r\nA\r\n").readByteArray();
        routes.add("GET " + CE + "/job-1/export", file("text/csv; charset=utf-8", "crawl-extract-job-1.csv",
                "completed", new Buffer().write(csv)));

        byte[] got = client.webgrph().exportCrawlExtract("job-1", "csv").readAllBytes();

        assertArrayEquals(csv, got);
        assertEquals((byte) 0xEF, got[0]);
    }

    @Test
    void export_aFormatTheApiDoesNotHaveIsRefusedBeforeTheRequest(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class, () -> client.webgrph().exportCrawlExtract("job-1", "xlsx"));
        assertThrows(IllegalArgumentException.class, () -> client.webgrph().exportCrawlExtract("job-1", null));
        assertThrows(IllegalArgumentException.class,
                () -> client.webgrph().downloadCrawlExtract("job-1", dir.resolve("rows.xlsx"), "xlsx"));
        assertThrows(IllegalArgumentException.class, () -> client.exportBatchJob("b-1", "xml"));
        assertEquals(List.of(), routes.seen);
        assertFalse(Files.exists(dir.resolve("rows.xlsx")));
    }

    @Test
    void downloadCrawlExtract_writesTheFile(@TempDir Path dir) throws Exception {
        routes.add("GET " + CE + "/job-1/export", file("application/x-ndjson", "crawl-extract-job-1.ndjson",
                "completed", text(NDJSON)));
        Path path = dir.resolve("rows.ndjson");

        var saved = client.webgrph().downloadCrawlExtract("job-1", path, "ndjson");

        assertEquals(NDJSON, Files.readString(path));
        assertEquals(path, saved.path());
        assertEquals(NDJSON.length(), saved.bytes());
        assertEquals("application/x-ndjson", saved.contentType());
        assertEquals("crawl-extract-job-1.ndjson", saved.filename());
        assertEquals("completed", saved.jobStatus());
    }

    @Test
    void downloadCrawlExtract_aRefusedRequestLeavesNoFile(@TempDir Path dir) {
        routes.json("GET " + CE + "/job-1/export", 404, Map.of("detail", "Crawl-extract job not found"));
        Path path = dir.resolve("rows.json");

        var ex = assertThrows(KloakdException.class, () -> client.webgrph().downloadCrawlExtract("job-1", path, "json"));

        assertEquals(404, ex.getStatusCode());
        assertEquals("Crawl-extract job not found", ex.getMessage());
        assertFalse(Files.exists(path));
    }

    @Test
    void export_anErrorStatusIsRetriedAsForAnyGet() {
        var retrying = Kloakd.builder().apiKey(TEST_API_KEY).organizationId(TEST_ORG_ID)
                .baseUrl(server.url("/").toString()).maxRetries(2).timeout(Duration.ofSeconds(5)).build();
        routes.add("GET " + CE + "/job-1/export", new MockResponse().setResponseCode(503)
                .addHeader("Retry-After", "0.01").setBody("{\"detail\":\"busy\"}"));
        routes.add("GET " + CE + "/job-1/export", file("application/json", "crawl-extract-job-1.json",
                "completed", text("[]")));

        byte[] got = retrying.webgrph().exportCrawlExtract("job-1", "json").readAllBytes();

        assertEquals("[]", new String(got, StandardCharsets.UTF_8));
        assertEquals(2, routes.seen.size());
    }

    @Test
    void export_theRequestTimeoutDoesNotBoundTheBody() {
        var quick = Kloakd.builder().apiKey(TEST_API_KEY).organizationId(TEST_ORG_ID)
                .baseUrl(server.url("/").toString()).maxRetries(0).timeout(Duration.ofMillis(300)).build();
        routes.add("GET " + CE + "/job-1/export", file("application/x-ndjson", "crawl-extract-job-1.ndjson",
                "completed", text(NDJSON)).setBodyDelay(900, TimeUnit.MILLISECONDS));

        byte[] got = quick.webgrph().exportCrawlExtract("job-1", "ndjson").readAllBytes();

        assertEquals(NDJSON, new String(got, StandardCharsets.UTF_8));
    }

    // ── cancel ───────────────────────────────────────────────────────────────

    @Test
    void cancelCrawlExtract_returnsTheApisAnswer() {
        routes.json("POST " + CE + "/job-1/cancel", 200, Map.of("success", true, "job_id", "job-1",
                "crawl_id", "cr-1", "status", "cancelling"));

        var result = client.webgrph().cancelCrawlExtract("job-1");

        assertEquals("cancelling", result.get("status"));
        assertEquals(List.of("POST " + CE + "/job-1/cancel"), routes.seen);
    }

    @Test
    void cancelCrawlExtract_aJobThatHasEndedIs409() {
        routes.json("POST " + CE + "/job-1/cancel", 409, Map.of("detail", "The job has already completed"));

        var ex = assertThrows(ApiException.class, () -> client.webgrph().cancelCrawlExtract("job-1"));
        assertEquals(409, ex.getStatusCode());
        assertEquals("The job has already completed", ex.getMessage());
    }

    // ── batch export ─────────────────────────────────────────────────────────

    @Test
    void exportBatchJob_streamsTheFile() throws Exception {
        routes.add("GET batch-jobs/b-1/export", file("text/csv; charset=utf-8", "batch-b-1.csv", null,
                text("url,status\r\nhttps://example.com,ok\r\n")));

        try (ExportDownload export = client.exportBatchJob("b-1", "csv")) {
            assertEquals("batch-b-1.csv", export.filename());
            assertNull(export.jobStatus(), "the batch export sends no X-Job-Status");
            assertNull(export.crawl(), "nor any X-Crawl-* header");
            assertTrue(new String(export.body().readAllBytes(), StandardCharsets.UTF_8).startsWith("url,status"));
        }
        assertEquals("csv", query("format"));
        assertEquals(List.of("GET batch-jobs/b-1/export"), routes.seen);
    }

    @Test
    void downloadBatchJob_writesTheFileAndA404LeavesNone(@TempDir Path dir) throws Exception {
        routes.add("GET batch-jobs/b-1/export", file("application/x-ndjson", "batch-b-1.ndjson", null, text(NDJSON)));

        var saved = client.downloadBatchJob("b-1", dir.resolve("batch.ndjson"), "ndjson");

        assertEquals(NDJSON, Files.readString(dir.resolve("batch.ndjson")));
        assertEquals(NDJSON.length(), saved.bytes());
        assertEquals("batch-b-1.ndjson", saved.filename());

        Path missing = dir.resolve("missing.json");
        var ex = assertThrows(KloakdException.class, () -> client.downloadBatchJob("unknown", missing, "json"));
        assertEquals(404, ex.getStatusCode());
        assertFalse(Files.exists(missing));
    }

    // ── the SDK's surface ────────────────────────────────────────────────────

    @Test
    void theClientHasTheNewMethods() {
        var webgrph = Arrays.stream(dev.kloakd.sdk.modules.WebgrphNamespace.class.getMethods())
                .map(java.lang.reflect.Method::getName).toList();
        for (String name : List.of("startCrawlExtract", "crawlExtract", "getCrawlExtract", "waitForCrawlExtract",
                "getCrawlExtractResults", "crawlExtractAllResults", "getCrawlExtractPages", "exportCrawlExtract",
                "downloadCrawlExtract", "cancelCrawlExtract", "waitForCrawl", "continueCrawl",
                "isCrawlExtractHeld")) {
            assertTrue(webgrph.contains(name), name);
        }
        var fetchyr = Arrays.stream(dev.kloakd.sdk.modules.FetchyrNamespace.class.getMethods())
                .map(java.lang.reflect.Method::getName).toList();
        for (String name : List.of("startLoginSession", "getSession", "resumeSession")) {
            assertTrue(fetchyr.contains(name), name);
        }
        assertEquals(List.of("json", "ndjson", "csv"), ExportDownload.FORMATS);
    }
}
