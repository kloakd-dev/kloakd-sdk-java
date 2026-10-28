package dev.kloakd.sdk;

import dev.kloakd.sdk.errors.*;
import dev.kloakd.sdk.http.HttpTransport;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static dev.kloakd.sdk.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.4.0: every route that can answer 202 (kernel/sync_handoff.py's
 * {@code @hands_off_after_limit}, and evadr/fetch) is followed to its result.
 */
class HandOffTest {

    private MockWebServer server;
    private Kloakd client;
    private Routes routes;

    @BeforeEach
    void setUp() throws Exception {
        server = startServer();
        routes = routes(server);
        client = Kloakd.builder().apiKey(TEST_API_KEY).organizationId(TEST_ORG_ID)
                .baseUrl(server.url("/").toString()).maxRetries(0).timeout(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(1)).pollTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private static Map<String, Object> accepted(String pollUrl) {
        return Map.of("job_id", "job-1", "status", "running", "reason", "sync_limit_reached",
                "waited_seconds", 85, "poll_url", pollUrl,
                "message", "Still running; it continues as a job. Poll poll_url for the result.");
    }

    private static final String HANDOFF_POLL = "/api/v1/organizations/" + TEST_ORG_ID + "/handoff/job-1";

    /** route, poll route, the route's 200 body, the call, and a check of the typed result. */
    static Stream<Arguments> handOffRoutes() {
        return Stream.of(
                Arguments.of("kolektr/extract", "handoff/job-1",
                        Map.of("success", true, "url", "https://example.com", "total_records", 3),
                        (Function<Kloakd, Object>) c -> c.kolektr().extract("https://example.com"),
                        (Function<Object, Object>) r -> ((dev.kloakd.sdk.models.ExtractionResult) r).totalRecords(), 3),
                Arguments.of("fetchyr/authenticate", "handoff/job-1",
                        Map.of("success", true, "artifact_id", "art-s"),
                        (Function<Kloakd, Object>) c -> c.fetchyr().authenticate("login", "https://example.com"),
                        (Function<Object, Object>) r -> ((dev.kloakd.sdk.models.SessionResult) r).artifactId(), "art-s"),
                Arguments.of("fetchyr/form/detect", "handoff/job-1",
                        Map.of("url", "https://example.com", "forms_found", 2),
                        (Function<Kloakd, Object>) c -> c.fetchyr().detectForms("https://example.com", null),
                        (Function<Object, Object>) r -> ((dev.kloakd.sdk.models.FormDetectionResult) r).formsFound(), 2),
                Arguments.of("fetchyr/form/fill", "handoff/job-1",
                        Map.of("success", true, "submitted", true),
                        (Function<Kloakd, Object>) c -> c.fetchyr().fillForm("https://example.com", Map.of("q", "x"), null),
                        (Function<Object, Object>) r -> ((Map<?, ?>) r).get("submitted"), true),
                Arguments.of("fetchyr/mfa/detect", "handoff/job-1",
                        Map.of("mfa_detected", true, "mfa_type", "totp"),
                        (Function<Kloakd, Object>) c -> c.fetchyr().detectMfa("https://example.com", null),
                        (Function<Object, Object>) r -> ((dev.kloakd.sdk.models.MfaDetectionResult) r).mfaType(), "totp"),
                Arguments.of("nexus/analyze", "handoff/job-1",
                        Map.of("url", "https://example.com", "page_type", "listing"),
                        (Function<Kloakd, Object>) c -> c.nexus().analyze("https://example.com", "<html/>"),
                        (Function<Object, Object>) r -> ((dev.kloakd.sdk.models.NexusAnalyzeResult) r).pageType(), "listing"),
                Arguments.of("nexus/synthesize", "handoff/job-1",
                        Map.of("strategy_id", "st-1", "program", "p"),
                        (Function<Kloakd, Object>) c -> c.nexus().synthesize("https://example.com", "<html/>", "prices"),
                        (Function<Object, Object>) r -> ((dev.kloakd.sdk.models.NexusSynthesisResult) r).strategyId(), "st-1"),
                Arguments.of("nexus/execute", "handoff/job-1",
                        Map.of("strategy_id", "st-1", "success", true, "execution_result_id", "er-1"),
                        (Function<Kloakd, Object>) c -> c.nexus().execute("st-1", "https://example.com"),
                        (Function<Object, Object>) r -> ((dev.kloakd.sdk.models.NexusExecuteResult) r).executionResultId(), "er-1"),
                Arguments.of("evadr/fetch", "evadr/jobs/job-1",
                        Map.of("success", true, "url", "https://example.com", "tier_used", 3),
                        (Function<Kloakd, Object>) c -> c.evadr().fetch("https://example.com"),
                        (Function<Object, Object>) r -> ((dev.kloakd.sdk.models.FetchResult) r).tierUsed(), 3));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("handOffRoutes")
    void a202IsFollowedToTheSameTypedResult(String route, String pollRoute, Map<String, Object> page,
                                            Function<Kloakd, Object> call, Function<Object, Object> field,
                                            Object expected) {
        routes.json("POST " + route, 202, accepted("/api/v1/organizations/" + TEST_ORG_ID + "/" + pollRoute));
        routes.json("GET " + pollRoute, 200, Map.of("job_id", "job-1", "status", "running"));
        routes.json("GET " + pollRoute, 200, Map.of("job_id", "job-1", "status", "completed", "result", page));

        assertEquals(expected, field.apply(call.apply(client)));
        assertEquals(List.of("POST " + route, "GET " + pollRoute, "GET " + pollRoute), routes.seen);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("handOffRoutes")
    void a200IsNotPolled(String route, String pollRoute, Map<String, Object> page,
                         Function<Kloakd, Object> call, Function<Object, Object> field, Object expected) {
        routes.json("POST " + route, 200, page);

        assertEquals(expected, field.apply(call.apply(client)));
        assertEquals(List.of("POST " + route), routes.seen);
    }

    @Test
    void aFailedJobThrowsJobFailedWithItsId() {
        routes.json("POST nexus/execute", 202, accepted(HANDOFF_POLL));
        routes.json("GET handoff/job-1", 200, Map.of("job_id", "job-1", "kind", "nexus.execute",
                "status", "failed", "error", "Strategy Engine execution failed"));

        var ex = assertThrows(JobFailedException.class, () -> client.nexus().execute("st-1", "https://example.com"));
        assertEquals("job-1", ex.getJobId());
        assertEquals("job_failed", ex.getCode());
        assertEquals("Strategy Engine execution failed", ex.getMessage());
    }

    @Test
    void anExpiredJobThrowsJobFailed() {
        routes.json("POST kolektr/extract", 202, accepted(HANDOFF_POLL));
        routes.json("GET handoff/job-1", 404, Map.of("detail", "Job job-1 not found or expired"));

        var ex = assertThrows(JobFailedException.class, () -> client.kolektr().extract("https://example.com"));
        assertEquals("job-1", ex.getJobId());
        assertTrue(ex.getMessage().contains("not found or expired"), ex.getMessage());
    }

    @Test
    void anotherPollErrorIsRaisedAsIs() {
        routes.json("POST kolektr/extract", 202, accepted(HANDOFF_POLL));
        routes.json("GET handoff/job-1", 401, Map.of("detail", "bad key"));

        assertThrows(AuthenticationException.class, () -> client.kolektr().extract("https://example.com"));
    }

    @Test
    void aJobStillRunningAtTheTimeoutThrowsJobTimeout() {
        var impatient = Kloakd.builder().apiKey(TEST_API_KEY).organizationId(TEST_ORG_ID)
                .baseUrl(server.url("/").toString()).maxRetries(0)
                .pollInterval(Duration.ofMillis(1)).pollTimeout(Duration.ofMillis(30)).build();
        routes.json("POST fetchyr/form/detect", 202, accepted(HANDOFF_POLL));
        routes.json("GET handoff/job-1", 200, Map.of("job_id", "job-1", "status", "running"));

        var ex = assertThrows(JobTimeoutException.class,
                () -> impatient.fetchyr().detectForms("https://example.com", null));
        assertEquals("job-1", ex.getJobId());
        assertEquals("job_timeout", ex.getCode());
    }

    @Test
    void a202WithoutPollUrlPollsTheHandOffRoute() {
        routes.json("POST nexus/analyze", 202, Map.of("job_id", "job-1", "status", "running"));
        routes.json("GET handoff/job-1", 200, Map.of("status", "completed", "result", Map.of("page_type", "detail")));

        assertEquals("detail", client.nexus().analyze("https://example.com", "<html/>").pageType());
    }

    @Test
    void aCompletedJobWithoutResultIsAnEmptyBody() {
        routes.json("POST fetchyr/form/fill", 202, accepted(HANDOFF_POLL));
        routes.json("GET handoff/job-1", 200, Map.of("status", "completed"));

        assertEquals(Map.of(), client.fetchyr().fillForm("https://example.com", Map.of(), null));
    }

    @Test
    void theWaitGrowsBetweenPollsUpToTheCap() {
        var waits = new ArrayList<Long>();
        var t = new HttpTransport(TEST_API_KEY, TEST_ORG_ID, server.url("/").toString(),
                Duration.ofSeconds(5), 0, null) {
            @Override
            protected void sleep(long ms) { waits.add(ms); }
        };
        for (int i = 0; i < 7; i++) routes.json("GET handoff/job-1", 200, Map.of("status", "running"));
        routes.json("GET handoff/job-1", 200, Map.of("status", "completed", "result", Map.of("ok", true)));

        var result = t.followHandOff("job-1", HANDOFF_POLL, Duration.ofSeconds(4), Duration.ofHours(1));

        assertEquals(Map.of("ok", true), result);
        assertEquals(List.of(4000L, 6000L, 9000L, 13500L, 15000L, 15000L, 15000L, 15000L), waits);
    }

    @Test
    void pollUrlsResolveAgainstTheBaseUrlsOrigin() {
        var t = new HttpTransport(TEST_API_KEY, TEST_ORG_ID, "https://api.kloakd.dev/", Duration.ofSeconds(5), 0, null);
        assertEquals("https://api.kloakd.dev/api/v1/organizations/o/handoff/j", t.resolveUrl("/api/v1/organizations/o/handoff/j"));
        assertEquals("https://other.example/x", t.resolveUrl("https://other.example/x"));
        assertEquals("https://api.kloakd.dev/api/v1/organizations/" + TEST_ORG_ID + "/evadr/jobs/j", t.resolveUrl("evadr/jobs/j"));
    }

    @Test
    void thePollCarriesTheApiKey() throws Exception {
        routes.json("POST nexus/synthesize", 202, accepted(HANDOFF_POLL));
        routes.json("GET handoff/job-1", 200, Map.of("status", "completed", "result", Map.of("strategy_id", "s")));

        client.nexus().synthesize("https://example.com", "<html/>", "g");

        server.takeRequest();
        var poll = server.takeRequest();
        assertEquals(HANDOFF_POLL, poll.getPath());
        assertEquals("Bearer " + TEST_API_KEY, poll.getHeader("Authorization"));
        assertEquals("java/0.4.1", poll.getHeader("X-Kloakd-SDK"));
    }

    @Test
    void transportDefaultsAreThreeSecondsAndFifteenMinutes() {
        var t = new HttpTransport(TEST_API_KEY, TEST_ORG_ID, "https://api.kloakd.dev", Duration.ofSeconds(5), 0, null);
        assertEquals(Duration.ofSeconds(3), t.pollInterval());
        assertEquals(Duration.ofMinutes(15), t.pollTimeout());
    }
}
