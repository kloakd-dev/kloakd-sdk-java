package dev.kloakd.sdk;

import dev.kloakd.sdk.errors.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static dev.kloakd.sdk.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

/** 0.3.0: the 202 hand-off, the typed billing errors, and every call a live route (route list updated for 0.4.0). */
class LiveRoutesTest {

    private MockWebServer server;
    private Kloakd client;
    private Routes routes;

    private static final Map<String, Object> HANDED_OFF = Map.of(
            "job_id", "job-1", "status", "running", "reason", "sync_limit_reached", "waited_seconds", 85,
            "poll_url", "/api/v1/organizations/" + TEST_ORG_ID + "/evadr/jobs/job-1", "message", "still running");

    private static final Map<String, Object> PAGE = Map.of(
            "success", true, "url", "https://example.com", "status_code", 200, "tier_used", 2,
            "html", "<html>ok</html>");

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

    private FetchResultHolder fetch(Duration timeout) {
        return new FetchResultHolder(client.evadr().fetch("https://example.com", false, false, null,
                Duration.ofMillis(1), timeout));
    }

    private record FetchResultHolder(dev.kloakd.sdk.models.FetchResult result) {}

    // ── 202 ──────────────────────────────────────────────────────────────────

    @Test
    void fetch_followsAHandedOffJobToItsResult() {
        routes.json("POST evadr/fetch", 202, HANDED_OFF);
        routes.json("GET evadr/jobs/job-1", 200, Map.of("job_id", "job-1", "status", "running"));
        routes.json("GET evadr/jobs/job-1", 200, Map.of("job_id", "job-1", "status", "completed", "result", PAGE));

        var result = fetch(Duration.ofSeconds(5)).result();

        assertTrue(result.success());
        assertEquals(2, result.tierUsed());
        assertEquals("<html>ok</html>", result.html());
        assertEquals(List.of("POST evadr/fetch", "GET evadr/jobs/job-1", "GET evadr/jobs/job-1"), routes.seen);
    }

    @Test
    void fetch_aFailedJobIsAnError() {
        routes.json("POST evadr/fetch", 202, HANDED_OFF);
        routes.json("GET evadr/jobs/job-1", 200, Map.of("job_id", "job-1", "status", "failed", "error", "blocked"));

        var ex = assertThrows(ApiException.class, () -> fetch(Duration.ofSeconds(5)));
        assertEquals("job_failed", ex.getCode());
        assertEquals("blocked", ex.getMessage());
    }

    @Test
    void fetch_pollTimeout() {
        routes.json("POST evadr/fetch", 202, HANDED_OFF);
        routes.json("GET evadr/jobs/job-1", 200, Map.of("job_id", "job-1", "status", "running"));

        var ex = assertThrows(ApiException.class, () -> fetch(Duration.ofMillis(20)));
        assertEquals("job_timeout", ex.getCode());
    }

    @Test
    void fetch_answeredInTimeIsNotPolled() {
        routes.json("POST evadr/fetch", 200, PAGE);

        assertTrue(fetch(Duration.ofSeconds(5)).result().success());
        assertEquals(List.of("POST evadr/fetch"), routes.seen);
    }

    // ── typed billing errors ─────────────────────────────────────────────────

    private static Map<String, Object> detail(String code, String message, Map<String, Object> extra) {
        var d = new HashMap<String, Object>(extra);
        d.put("error", code);
        d.put("message", message);
        return Map.of("detail", d);
    }

    @Test
    void error402_paymentRequired() {
        routes.json("POST evadr/fetch", 402, detail("payment_required", "Your last payment did not go through.",
                Map.of("upgrade_url", "/settings/billing")));

        var ex = assertThrows(PaymentRequiredException.class, () -> client.evadr().fetch("https://example.com"));
        assertEquals("/settings/billing", ex.getUpgradeUrl());
        assertEquals("Your last payment did not go through.", ex.getMessage());
    }

    @Test
    void error429_gbExhaustedIsNotRetried() {
        var retrying = Kloakd.builder().apiKey(TEST_API_KEY).organizationId(TEST_ORG_ID)
                .baseUrl(server.url("/").toString()).maxRetries(3).timeout(Duration.ofSeconds(5)).build();
        routes.json("POST evadr/fetch", 429, detail("gb_exhausted", "allocation used",
                Map.of("residential_gb_used", 10, "residential_gb_limit", 10)));

        var ex = assertThrows(QuotaExceededException.class, () -> retrying.evadr().fetch("https://example.com"));
        assertEquals(10.0, ex.getGbUsed());
        assertEquals(1, routes.seen.size(), "an exhausted quota must not be retried");
    }

    @Test
    void error429_rateLimitedReadsRetryAfter() {
        routes.add("POST evadr/fetch", new MockResponse().setResponseCode(429).addHeader("Retry-After", "7")
                .setBody("{\"detail\":{\"error\":\"rate_limited\",\"message\":\"slow down\"}}"));

        var ex = assertThrows(RateLimitException.class, () -> client.evadr().fetch("https://example.com"));
        assertEquals(7, ex.getRetryAfter());
        assertEquals("rate_limited", ex.getCode());
    }

    @Test
    void error503_billingUnavailable() {
        routes.add("POST evadr/fetch", new MockResponse().setResponseCode(503).addHeader("Retry-After", "30")
                .setBody("{\"detail\":{\"error\":\"billing_unavailable\",\"message\":\"Usage could not be checked\"}}"));

        var ex = assertThrows(BillingUnavailableException.class, () -> client.evadr().fetch("https://example.com"));
        assertEquals(30, ex.getRetryAfter());
    }

    // ── contract: every call the SDK makes is a live route ───────────────────

    @Test
    void everySdkCallIsALiveRoute() throws IOException {
        var live = new HashSet<String>();
        var prefix = "/api/v1/organizations/{organization_id}/";
        try (var in = getClass().getResourceAsStream("/routes.tsv")) {
            for (String line : new String(in.readAllBytes()).split("\n")) {
                var parts = line.split("\t", 2);
                if (parts.length == 2 && parts[1].startsWith(prefix)) {
                    live.add(parts[0] + " " + parts[1].substring(prefix.length()).replaceAll("\\{[^}]+}", "{}"));
                }
            }
        }
        var verbs = Map.of("get", "GET", "post", "POST", "put", "PUT", "patch", "PATCH", "delete", "DELETE",
                "stream", "POST", "streamWithEvents", "POST", "postAsync", "POST", "getStream", "GET",
                "postFollowing", "POST");
        var call = Pattern.compile("\\bt\\.(get|post|put|patch|delete|stream|streamWithEvents|postAsync|getStream|postFollowing|request|send)\\(\\s*"
                + "(?:\"(GET|POST|PUT|PATCH|DELETE)\",\\s*)?((?:\"[^\"]*\"|[A-Za-z_][\\w.()]*)(?:\\s*\\+\\s*(?:\"[^\"]*\"|[A-Za-z_][\\w.()]*))*)");
        int found = 0;
        var problems = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = call.matcher(Files.readString(file));
                while (m.find()) {
                    String method = m.group(2) != null ? m.group(2) : verbs.get(m.group(1));
                    var parts = m.group(3).split("\\s*\\+\\s*");
                    if (!parts[0].startsWith("\"")) continue;
                    var path = new StringBuilder();
                    for (String p : parts) path.append(p.startsWith("\"") ? p.substring(1, p.length() - 1) : "{}");
                    found++;
                    String key = method + " " + path;
                    if (!live.contains(key)) problems.add(file.getFileName() + ": " + key);
                }
            }
        }
        // 0.4.0 makes 91 calls (0.3.0: 123; 32 went with removed routes).
        assertTrue(found >= 85, "found only " + found + " calls; the scan is broken");
        assertEquals(List.of(), problems, "calls to routes that do not exist");
    }

    // ── 0.4.0: routes removed from the API, and the hand-off poll route ─────

    private static List<String> fixture() throws IOException {
        try (var in = LiveRoutesTest.class.getResourceAsStream("/routes.tsv")) {
            return List.of(new String(in.readAllBytes()).split("\n"));
        }
    }

    @Test
    void removedRoutesAreAbsentAndTheHandOffPollRouteIsPresent() throws IOException {
        var lines = fixture();
        assertTrue(lines.stream().noneMatch(l -> l.contains("/recommendations/")),
                "nexus/recommendations/* were removed from the API");
        assertFalse(lines.contains("POST\t/api/v1/organizations/{organization_id}/kolektr/jobs"),
                "POST kolektr/jobs was removed from the API");
        // kobe-scraper d7d25134: routes that answered invented results.
        for (String gone : List.of("kolektr/jobs/{job_id}/progress", "kolektr/jobs/{job_id}/progress/summary",
                "kolektr/jobs/{job_id}/progress/events", "kolektr/jobs/{job_id}/progress/latest",
                "kolektr/progress/phases", "kolektr/progress/phases/{phase_name}",
                "kolektr/progress/phases/{phase_name}/steps", "kolektr/progress/summary",
                "kolektr/pipeline/{pipeline_id}/stream", "kolektr/pipeline/{pipeline_id}/events",
                "webgrph/analytics/job-trends", "webgrph/analytics/error-summary", "evadr/profiles")) {
            assertFalse(lines.contains("GET\t/api/v1/organizations/{organization_id}/" + gone), gone + " was removed");
        }
        assertTrue(lines.contains("GET\t/api/v1/organizations/{organization_id}/kolektr/jobs"));
        assertTrue(lines.contains("GET\t/api/v1/organizations/{organization_id}/kolektr/jobs/{job_id}"));
        assertTrue(lines.contains("GET\t/api/v1/organizations/{organization_id}/evadr/vendors"));
        assertTrue(lines.contains("GET\t/api/v1/organizations/{organization_id}/handoff/{job_id}"));
    }

    @Test
    void theSdkDoesNotCallTheDashboardOnlyAnalyticsRoutes() throws IOException {
        // modules/webgrph/analytics_router.py serves the dashboard; the public
        // API spec leaves /webgrph/analytics/ out (generate_public_openapi.py EXCLUDED).
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                assertFalse(Files.readString(file).contains("webgrph/analytics"), file.toString());
            }
        }
    }

    @Test
    void theSdkHasNoMethodsForRemovedRoutes() {
        var nexus = Arrays.stream(dev.kloakd.sdk.modules.NexusNamespace.class.getMethods())
                .map(java.lang.reflect.Method::getName).toList();
        for (String gone : List.of("recommendAnalyze", "listRecommendationApplications", "getCacheStatistics",
                "cleanupCache", "invalidateCache", "getHooksStatus", "enableHook", "disableHook",
                "createPreference", "getPreferences", "updatePreference", "deletePreference",
                "getRecommendationStatistics")) {
            assertFalse(nexus.contains(gone), gone);
        }
        var kolektr = Arrays.stream(dev.kloakd.sdk.modules.KolektrNamespace.class.getMethods())
                .map(java.lang.reflect.Method::getName).toList();
        for (String gone : List.of("createJob", "getJobProgress", "getJobProgressEvents", "getJobProgressLatest",
                "getJobProgressSummary", "getPipelineEvents", "getPipelineStream", "listProgressPhases",
                "getProgressPhase", "getProgressPhaseSteps", "getProgressSummary")) {
            assertFalse(kolektr.contains(gone), gone);
        }
        assertTrue(kolektr.contains("listJobs"));
        assertTrue(kolektr.contains("getJob"));
        var webgrph = Arrays.stream(dev.kloakd.sdk.modules.WebgrphNamespace.class.getMethods())
                .map(java.lang.reflect.Method::getName).toList();
        for (String gone : List.of("getDashboardSummary", "getErrorSummary", "getJobTrends", "getDiscoveryPatterns",
                "getEfficiencyMetrics", "getSiteMappingTrends", "getUserBehaviorInsights")) {
            assertFalse(webgrph.contains(gone), gone);
        }
        var evadr = Arrays.stream(dev.kloakd.sdk.modules.EvadrNamespace.class.getMethods())
                .map(java.lang.reflect.Method::getName).toList();
        assertFalse(evadr.contains("listProfiles"));
        assertTrue(evadr.contains("listVendors"));
    }
}
