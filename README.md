# kloakd-sdk-java

Official Java SDK for the [KLOAKD API](https://kloakd.dev) — anti-bot bypassing, site crawling, API discovery, authenticated browsing, and intelligent data extraction.

## Requirements

- Java 21+
- Maven 3.8+ or Gradle

## Installation

Maven (`pom.xml`):

```xml
<dependency>
  <groupId>dev.kloakd</groupId>
  <artifactId>kloakd-sdk</artifactId>
  <version>0.6.0</version>
</dependency>
```

Gradle (`build.gradle.kts`):

```kotlin
dependencies {
    implementation("dev.kloakd:kloakd-sdk:0.6.0")
}
```

Gradle (Groovy `build.gradle`):

```groovy
dependencies {
    implementation 'dev.kloakd:kloakd-sdk:0.6.0'
}
```

## Quickstart

```java
import dev.kloakd.sdk.Kloakd;
import dev.kloakd.sdk.models.*;   // FetchOptions, ExtractOptions, CrawlOptions, ChatTurn, ...

Kloakd client = Kloakd.builder()
    .apiKey(System.getenv("KLOAKD_API_KEY"))
    .organizationId(System.getenv("KLOAKD_ORG_ID"))
    .build();

// 1. Anti-bot fetch
var page = client.evadr().fetch("https://target.com");
System.out.println("Bypassed: " + page.antiBotBypassed());

// 2. Site crawl (waits for the crawl, returns every page)
var crawl = client.webgrph().crawl("https://target.com");
System.out.println("Pages found: " + crawl.totalPages());

// 3. Data extraction (reuse fetch artifact)
var data = client.kolektr().extract("https://target.com/products",
    null, page.artifactId(), null, null);
data.records().forEach(System.out::println);
```

## Modules

| Namespace | Description |
|-----------|-------------|
| `client.evadr()` | Anti-bot fetch, analysis, proxy storage |
| `client.webgrph()` | Site crawl, crawl pages and events, detected APIs, reader view, sessions |
| `client.skanyr()` | Synced sites, resources, search, extraction |
| `client.nexus()` | AI page analysis, strategy synthesis, verification, reasoning |
| `client.parlyr()` | Natural language → scrape intent parsing |
| `client.fetchyr()` | Login sessions, authenticated browser sessions, workflows |
| `client.kolektr()` | Structured data extraction |

## API Patterns

### Synchronous (default)

```java
var result = client.evadr().fetch("https://example.com");
```

The API answers within about 85 s. A request still running then is handed
off as a job (HTTP 202 with a `poll_url`); the SDK polls it and returns the
same result as a direct answer. This applies to `evadr().fetch`,
`kolektr().extract`, `fetchyr().authenticate`, `detectForms`, `fillForm`,
`detectMfa`, `nexus().analyze`, `synthesize` and `client.unifiedScrape`.
Polls start
after `pollInterval` and wait 1.5 times longer each time (at most 15 s), up
to `pollTimeout`; a failed or expired job throws `JobFailedException`, one
still running `JobTimeoutException`. Set the wait per client, or per call
for `fetch`:

```java
Kloakd client = Kloakd.builder()
    .apiKey("sk-live-...")
    .organizationId("your-org-uuid")
    .pollInterval(Duration.ofSeconds(3))   // default
    .pollTimeout(Duration.ofMinutes(15))   // default
    .build();

var result = client.evadr().fetch(url, FetchOptions.builder()
        .forceBrowser(true)
        .sessionArtifactId(session.artifactId())   // from fetchyr().authenticate(...)
        .pollInterval(Duration.ofSeconds(3))
        .pollTimeout(Duration.ofMinutes(15))
        .build());
```

A call that gives up waiting throws `JobTimeoutException`; the job keeps
running. Resume it with its id:

```java
try {
    var data = client.kolektr().extract("https://example.com/slow");
} catch (JobTimeoutException e) {
    Map<String, Object> body = client.waitForHandoff(e.getJobId());   // GET handoff/{job_id}
    // or one look: client.getHandoff(e.getJobId()).get("status")
}
```

A page behind a login comes back with `authRequired()` true and a
`discoveryId()` (fetch and extract results); follow it with
`client.fetchyr().getAuthChainStatus(id)` and answer it with
`resumeAuthChainCredentials` / `resumeAuthChainMfa`.

### Extraction schemas

`kolektr().extract`, `extractHtml` and `client.crawl(...)`'s `extractSchema`
take either the shorthand `{field: "css:selector"}` (the `css:` prefix is
optional) or the API's own `{"fields": [{"name", "selector", "attribute"?, "type"?}]}`;
the shorthand is sent as the latter.

```java
var data = client.kolektr().extract("https://example.com/products", ExtractOptions.builder()
        .schema(Map.of("title", "css:h3 a", "price", "p.price_color"))
        .level("level2")                 // optional: level1, level2, or automatic
        .build());
```

#### One record per item (`itemSelector`)

On a list page, an item selector (CSS) makes one record per element it
matches, and each field's selector is read inside that element: the first
match gives the value (every match for a `list` field), its text or the
named `attribute`; a field with no match in an item is `null`; a field with
no selector reads the item element itself. Without an item selector the
result is one record for the page, as before. A blank or invalid CSS item
selector, or one without fields, is answered with 422
(`ExtractSchema.build()` and `ExtractOptions.itemSelector` check the first
two before sending).

```java
// Typed schema
var schema = ExtractSchema.builder()
        .itemSelector("article.product_pod")
        .field("title", "h3 a", "title")        // the title attribute of the first match
        .field("price", "p.price_color")        // its text
        .field("link", "h3 a", "href")
        .listField("tags", "ul.tags li")        // every match, as a list
        .build();
var books = client.kolektr().extract("https://books.toscrape.com", ExtractOptions.builder()
        .schema(schema.toMap())
        .build());
books.records();   // one map per article.product_pod

// Shorthand with an item selector
client.kolektr().extract("https://books.toscrape.com", ExtractOptions.builder()
        .schema(Map.of("title", "css:h3 a", "price", "p.price_color"))
        .itemSelector("article.product_pod")
        .build());
// ... or in the shorthand map itself: {"item_selector": "article.product_pod", "title": "css:h3 a"}

// HTML you already have
client.kolektr().extractHtml(html, url, Map.of("title", "h3 a"), "article.product_pod");
```

### Asynchronous (`CompletableFuture`)

```java
CompletableFuture<FetchResult> future = client.evadr().fetchAsync("https://example.com",
        FetchOptions.builder().webhookUrl("https://hooks.example.com/kloakd").build());
future.thenAccept(r -> System.out.println(r.html()));   // html() may be null
```

### SSE Streaming (`Stream<T>`)

Streams are lazy: events arrive as the API sends them. Close the stream
(try-with-resources) to close the connection.

```java
try (var stream = client.evadr().fetchStream("https://example.com")) {
    // STATUS / COMPLETE / FAILED, or pipeline events (pipelineEvent() set)
    stream.forEach(event -> System.out.println(event.type() + " " + event.status()));
}

try (var stream = client.webgrph().crawlStream("https://example.com")) {
    // PipelineEvent: eventType() pipeline_step ... pipeline_complete
    stream.forEach(e -> System.out.println(e.eventType() + " " + e.subStatus()));
}

try (var stream = client.crawlStream("https://example.com", CrawlOptions.builder().maxPages(20).build())) {
    // discovery_complete, page_fetching, page_fetched / page_failed, crawl_complete
    stream.forEach(e -> System.out.println(e.type() + " " + e.url()));
}
```

### Parlyr chat

```java
ChatTurn turn = client.parlyr().chat("session-1", "get the prices from books.toscrape.com");
System.out.println(turn.response());          // text and clarification contents
Map<String, Object> plan = turn.interpretation();
if (!turn.ok()) System.out.println(turn.errors().get(0).content());

try (var events = client.parlyr().chatStream("session-1", "start")) {
    events.forEach(e -> System.out.println(e.type() + ": " + e.content()));
}
```

### Crawls

Crawls run asynchronously. `crawl` starts one, waits for it and reads every
page by cursor; `startCrawl` returns at once with the crawl ID and no status
(`POST webgrph/crawl` does not answer with one: `status()` is null until a
wait or `getCrawlStatus` reads it).

```java
List<PageNode> pages = client.webgrph().crawlAll("https://example.com", 3, 500);
var started = client.webgrph().startCrawl("https://example.com", CrawlOptions.builder()
        .maxDepth(3).maxPages(500)
        .scope(Map.of("same_host", true))
        .captureMode("content")
        .buildAsSet());                  // unset limits are not sent: the tier's defaults apply
var done = client.webgrph().waitForCrawl(started.crawlId(), Duration.ofSeconds(3), Duration.ofMinutes(60));
done = client.webgrph().waitForCrawl(started.crawlId());   // the same wait with the defaults (3 s, 60 min)
```

`waitForCrawl` takes only the crawl ID, so a crawl started elsewhere (or in
an earlier run) can be waited for. The status route does not return the
crawl's URL: `url()` is null on a result read by ID, and is the URL you
passed on a result from `crawl(url, ...)`.

**Held crawls.** When the organisation's bandwidth allowance is used up the
API pauses the crawl with status `held_quota` and resumes it itself when the
allowance returns. A held crawl has not ended, so the waits stop and return
it instead of polling to the timeout:

```java
CrawlResult r = client.webgrph().crawl("https://example.com", CrawlOptions.builder().buildAsSet());
if (r.held()) {
    // r.status() is "held_quota", r.message() is the API's text, r.error() is null,
    // r.success() is false and r.pages() is empty.
    // Later: client.webgrph().waitForCrawl(r.crawlId())
}
```

`crawlAll` returns an empty list for a held crawl, and `client.crawl(url,
options)` returns a `SiteCrawlResult` with `held()` true, `crawlId()`,
`status()` and `message()`, and fetches nothing. Check `held()` before
reading an empty result as a site with no pages.

**Partial crawls and continue.** The status says how the crawl ended:
`isPartial()`, `partialReason()`, `resumable()`, `pagesDiscovered()`,
`pagesFetched()`, `pagesUnfetched()` and `errorCode()` (each null when the
API did not report it). A crawl that stopped at its time budget ends
`completed` with `isPartial()` and `resumable()` true; `continueCrawl` puts
it back in the queue with a new time budget, and pages already fetched are
not fetched again:

```java
if (Boolean.TRUE.equals(done.resumable())) {
    client.webgrph().continueCrawl(done.crawlId());        // 202: status "queued"
    done = client.webgrph().waitForCrawl(done.crawlId());
}
```

`continueCrawl` throws `ApiException` (409) when the crawl is not one that
stopped at its time budget, has not ended, or is already being continued.
An ID the API does not know is answered the same way (409, not 404).

Pages are read from `webgrph/crawl/{id}/pages` (`getCrawlPages(crawlId,
cursor, limit[, includeGrouping])`): `limit` is 1–1000 (0 for the API's
default of 100; anything else throws `IllegalArgumentException`), and the
cursor is the previous page's `next_cursor` (any other value is answered
with 422). Only fetched pages are listed, each under the page the crawl found
it on, and the crawl's `totalPages()` and `maxDepthReached()` count fetched
pages only. `includeGrouping` (also on `waitForCrawl(id, interval, timeout,
true)`) adds the hierarchy's grouping folders, which were never fetched:
`PageNode.isGrouping()` (`metadata.is_grouping`) is true for them. The raw
pages in a crawl's `pipeline_complete` event (`metadata.discovery_response.pages`)
include the folders and carry `is_grouping` and `crawl_parent_url`.
`PageNode.fetchStatus()` and `fetchError()` read `metadata.fetch_status` and
`metadata.fetch_error` (null when the page has neither).

```java
var page = client.webgrph().getCrawlPages(crawlId, null, 1000, true);
```

`client.crawl(url, options)` also fetches every page (and extracts it when
`extractSchema` is set). Its `success()` and `error()` are the crawl's.
`CrawlOptions.builder().build()` fills the orchestrator's own defaults
(depth 3, 100 pages) and sends them; `buildAsSet()` sends only what was set. A
page that fails becomes a failed page, but billing and authentication
errors (`PaymentRequiredException`, `QuotaExceededException`,
`BillingUnavailableException`, `NotEntitledException`,
`AuthenticationException`, `RateLimitException`) are thrown.

### Crawl, extract and download

One job crawls a site, extracts every page and keeps the rows for download
(unreleased; after 0.6.0). The plan must include Kolektr, and a test key is
refused (403).

```java
Map<String, Object> job = client.webgrph().startCrawlExtract("https://example.com",
        CrawlOptions.builder().maxDepth(2).maxPages(200).buildAsSet(),
        Map.of("schema", Map.of("item_selector", "li.product",
                "fields", List.of(Map.of("name", "title", "selector", "h3")))));
String jobId = (String) job.get("job_id");
Map<String, Object> status = client.webgrph().waitForCrawlExtract(jobId);   // polls to the end
// status.get("status"), status.get("progress"), status.get("shapes"),
// status.get("crawl") (the job's crawl), status.get("held") (added by the SDK)

// or start and wait in one call (throws JobFailedException when the job fails;
// a held job is returned, not thrown)
status = client.webgrph().crawlExtract("https://example.com", null, null);

// Rows, a page at a time (limit 1-1000, 0 for the default) or all of them
var page = client.webgrph().getCrawlExtractResults(jobId, null, 500, null, null, "item");
List<Map<String, Object>> rows = client.webgrph().crawlExtractAllResults(jobId);

// The crawled pages and how each was extracted
var failed = client.webgrph().getCrawlExtractPages(jobId, 0, 0, "failed");

// Download: json, ndjson or csv, streamed to a file ...
SavedExport saved = client.webgrph().downloadCrawlExtract(jobId, Path.of("rows.csv"), "csv");
// ... or as a stream
try (ExportDownload export = client.webgrph().exportCrawlExtract(jobId, "ndjson")) {
    export.body().transferTo(out);
}

client.webgrph().cancelCrawlExtract(jobId);   // 409 (ApiException) once it has ended
```

The JSON answers are maps holding the API's own fields. `extract` is the
API's own object: `entity_type`, `fields` (a list of names) or `schema`
(`item_selector` and `fields` of `{name, selector, attribute, type}`);
null lets the API choose what to extract. `waitForCrawlExtract` polls every
3 s for at most 60 min by default and throws `JobTimeoutException` while the
job still runs; a cancelled job is returned, not thrown.

The status carries a `crawl` object for the job's crawl (null until the
crawl has a state): `status`, `is_partial`, `partial_reason`,
`pages_discovered`, `pages_fetched`, `pages_unfetched`, `cancelled`,
`root_unfetched`. When `crawl.status` is `held_quota` the job is waiting for
the organisation's allowance; the job's own `status` stays as it was.
`waitForCrawlExtract` and `crawlExtract` then stop and return the status
with `"held": true` (a key the SDK adds; it is `false` on every other
return). `WebgrphNamespace.isCrawlExtractHeld(status)` reads the same thing
from a status fetched with `getCrawlExtract`.

An export of a job that has not completed holds the rows stored so far
(`jobStatus()`, from the `X-Job-Status` header). The export also says how
the crawl ended, from its `X-Crawl-*` headers:

```java
try (ExportDownload export = client.webgrph().exportCrawlExtract(jobId, "csv")) {
    ExportCrawl crawl = export.crawl();     // null when the API sent no X-Crawl-* header
    if (crawl != null && Boolean.TRUE.equals(crawl.isPartial())) {
        // crawl.partialReason(), crawl.pagesFetched(), crawl.pagesUnfetched(),
        // crawl.pagesDiscovered(), crawl.status(), crawl.cancelled(), crawl.rootUnfetched()
    }
}
```

`SavedExport.crawl()` holds the same for a download written to a file. A
header the API did not send is null. The CSV starts with a UTF-8 byte order
mark. A refused download leaves no file.

A batch job's results download the same way:

```java
client.downloadBatchJob(batchJobId, Path.of("batch.ndjson"), "ndjson");
try (ExportDownload export = client.exportBatchJob(batchJobId, "csv")) {
    byte[] data = export.readAllBytes();
}
```

The batch export sends no `X-Job-Status` and no `X-Crawl-*` header:
`jobStatus()` and `crawl()` are null for it.

### Batch jobs

A batch extracts a list of URLs, one item per URL, and holds one work slot
until it ends. It needs a live key (a test key is refused with 403).

```java
BatchJob batch = client.createBatchJob(
        List.of("https://shop.example/p/1", "https://shop.example/p/2"),
        BatchJobOptions.builder().entityType("products").fields(List.of("name", "price")).build());

client.pauseBatchJob(batch.id());                    // items already running finish; the rest wait
client.resumeBatchJob(batch.id());                   // the waiting items are queued again, each once
BatchJob state = client.getBatchJob(batch.id());     // status(), totalItems(), completedItems(), message()

BatchJobResults page = client.getBatchJobResults(batch.id(), 100, 0);
for (BatchJobItem item : page.results()) {
    System.out.println(item.jobId() + " " + item.status() + " " + item.extractedData());
}

client.cancelBatchJob(batch.id());   // items not yet ended are cancelled; finished ones keep their results
```

- `status()` is `pending`, `running`, `paused`, `held_quota`, `completed`,
  `failed` or `cancelled`. Only a running batch can be paused, only a
  paused one resumed, and a batch that has completed or failed cannot be
  cancelled: the API answers 409 and the SDK throws an `ApiException` with
  `getCode()` `invalid_batch_transition` (the message names the batch's
  status).
- Repeating a call is not an error: pausing a paused batch, resuming a
  running one and cancelling a cancelled one answer the batch as it is.
- `resumeBatchJob` can throw a 503 `ApiException` with `getCode()`
  `dispatch_failed`: the items could not be queued and the batch is paused
  again, so call it again. `createBatchJob` throws the same when the batch
  could not be queued; nothing was started. Neither is retried by the SDK.
- Creating or resuming over the plan's running batches throws
  `ConcurrencyLimitException`; more URLs than the plan's limit is a 413
  `ApiException` with `getCode()` `batch_too_large`.
- `listBatchJobs()` and `listBatchJobs(page, pageSize)` list the
  organisation's batches, newest first (`BatchJobsPage.total()`,
  `batchJobs()`). `exportBatchJob` and `downloadBatchJob` (above) give the
  rows as a file.

### Unified scrape

`client.unifiedScrape(url, options)` lets the API choose how to scrape a
URL: level 1 (direct extraction) or level 2 (the Strategy Engine),
escalating when the first fails. It needs a live key whose plan has Kolektr
and Nexus (403 `ForbiddenException` with `getCode()` `test_key_not_allowed`
or `module_not_entitled` otherwise).

```java
UnifiedScrapeResult scrape = client.unifiedScrape("https://shop.example/list",
        UnifiedScrapeOptions.builder()
                .prompt("product names and prices")
                .mode("auto")   // or "level1", "level2"
                .schema(Map.of("name", "product name", "price", "price"))
                .build());
if (scrape.authRequired()) {
    // The URL is behind a login: nothing was scraped. scrape.discoveryId() names the auth-chain discovery.
} else {
    UnifiedScrapeStatus job = client.getUnifiedScrape(scrape.jobId());   // status(), level(), progress(), result(), escalationPath()
}
```

Options: `prompt`, `mode`, `schema`, `options`, `context`, `constraints`,
`sessionArtifactId` (a login session), and `pollInterval` / `pollTimeout`
for a request the API hands off (202), which is followed like every other
long request.

### Login sessions

A login session lets later requests run as a logged-in account
(`fetchyr()`). There are two ways to make one.

```java
// 1. Interactive (the default): the API opens the site's login and waits.
LoginSession s = client.fetchyr().startLoginSession("https://shop.example/account");
// s.status() is "waiting_for_login"; s.discoveryId() and s.next() name the
// auth-chain calls that take the credentials (getAuthChainStatus,
// resumeAuthChainCredentials, resumeAuthChainMfa).

// 2. Cookies from your own browser. user_agent is required with cookies.
LoginSession ready = client.fetchyr().startLoginSession("https://shop.example/account",
        LoginSessionOptions.builder()
                .method("cookies")
                .cookies(exportedCookies)            // List<Map<String, Object>>: name, value, domain, ...
                .userAgent(browserUserAgent)
                .country("KE")
                .build());
// ready.status() is "ready"; ready.sessionArtifactId() is the session;
// ready.cookiesKept() and ready.cookiesDropped() say which cookies were used.

SessionStatus status = client.fetchyr().getSession(ready.sessionArtifactId());
// status.status(), pausedAt(), pauseReason(), pauseMessage(), expiresAt(),
// requestsThisHour(), protections(), identity()
if ("paused".equals(status.status())) {
    status = client.fetchyr().resumeSession(status.artifactId());
}
```

Only the options you set are sent. The interactive answer is a 202 and the
cookies answer a 201; both are returned as they are (the 202 is not a
hand-off to poll). A request the API refuses is an `ApiException` (422).
`resumeSession` throws `ApiException` (409) when the session is not paused
or the site signed it out (log in again). `getSession` here takes the
session's artifact ID; `webgrph().getSession(sessionId)` is a different,
older route.

### Lists and paging

```java
client.kolektr().listJobs(1, 50, "completed");        // page, limit, status_filter
client.kolektr().listScrapers(1, 20, true);           // page, page_size, is_template
client.kolektr().listContent(1, 20, "shoes");         // page, page_size, search
client.fetchyr().listWorkflows(1, 20);                // page, page_size
client.kolektr().getApiDataPaginated("https://api.shop.com/v1/items", 0, 100, null);
client.webgrph().listSessions(false);                 // active_only is always sent
List<Map<String, Object>> sites = client.skanyr().listSites();
byte[] zip = client.skanyr().exportSite(siteId, null, 0);
```

Path values (ids, and the API endpoint URL of `kolektr/api-data/...`) are
percent-encoded by the SDK.

## Configuration

```java
Kloakd client = Kloakd.builder()
    .apiKey("sk-live-...")
    .organizationId("your-org-uuid")
    .baseUrl("https://api.kloakd.dev")   // optional
    .timeout(Duration.ofSeconds(100))     // optional: per attempt, default 100 s (the API hands off at 85 s)
    .maxRetries(3)                        // optional, default 3
    .pollInterval(Duration.ofSeconds(3))  // optional: first wait on a 202 hand-off
    .pollTimeout(Duration.ofMinutes(15))  // optional: limit on following one
    .build();
```

Every request sends `User-Agent: kloakd-sdk-java/0.6.0`.

### Retries

- 500, 502, 503, 504 and network errors are retried only for GET, HEAD and
  DELETE; POST, PUT and PATCH are never retried on them (the work may have
  been done).
- 429 `rate_limited` is retried on any method after `Retry-After` (or
  `retry_after`, or `reset_at`); a wait over 60 s is thrown instead. The
  playground quota (429 with `retry_after` 0 and no reset) is not retried:
  `RateLimitException.isQuotaExhausted()` is true and it carries
  `getQuotaUsed()`, `getQuotaLimit()` and `getUpgradeUrl()`.
- 503 `billing_unavailable` is retried on any method (nothing was started).
- 409 `concurrent_limit` is never retried. Every metered call holds one of
  the organisation's work slots while it runs, and running or queued crawls
  and batches hold one each; the plan sets how many run at once (Free 2,
  Starter 5, Pro 10, Pro+ 20, Developer 50, Enterprise 100). The exception
  carries `getConcurrentLimit()`, `getActiveCrawls()`, `getActiveRequests()`
  and `getTier()` (test keys report only the limit).
- Otherwise backoff is 1 s × 2^attempt, at most 60 s.

## Error Handling

```java
import dev.kloakd.sdk.errors.*;

try {
    var result = client.evadr().fetch("https://example.com");
} catch (AuthenticationException e) {
    // 401 — invalid or expired API key
} catch (NotEntitledException e) {
    // 403 not_entitled / tier_access_denied — not on the required plan
    System.out.println(e.getFeature() + " needs " + e.getRequiredTier() + ": " + e.getUpgradeUrl());
} catch (ForbiddenException e) {
    // any other 403
} catch (PaymentRequiredException e) {
    // 402 — the plan's last payment failed
    System.out.println("Update payment at: " + e.getUpgradeUrl());
} catch (QuotaExceededException e) {
    // 429 gb_exhausted — allocation and top-ups used up (not retried)
    System.out.println(e.getTier() + ": GB used " + e.getGbUsed() + " of " + e.getGbLimit());
} catch (RateLimitException e) {
    // 429 — still limited after the retries, or the playground quota
    System.out.println(e.isQuotaExhausted() ? "Quota used up" : "Retry after: " + e.getRetryAfter() + "s");
} catch (ConcurrencyLimitException e) {
    // 409 concurrent_limit — all the organisation's work slots are in use (not retried)
    System.out.println(e.getTier() + " runs " + e.getConcurrentLimit() + " at once: "
            + e.getActiveCrawls() + " crawl(s)/batch(es), " + e.getActiveRequests() + " other call(s)");
} catch (BillingUnavailableException e) {
    // 503 billing_unavailable — usage could not be checked; retry later
} catch (JobFailedException e) {
    // a request handed off as a job (202) failed or expired
    System.out.println("Job " + e.getJobId() + ": " + e.getMessage());
} catch (JobTimeoutException e) {
    // still running at pollTimeout; resume with client.waitForHandoff(e.getJobId())
} catch (UpstreamException e) {
    // 502 — upstream site fetch failed
} catch (ApiException e) {
    // other 4xx/5xx; the message is the API's (422: "body.url: Field required"),
    // or the response text when it is not JSON. getCode() is the API's error code,
    // for example 409 invalid_batch_transition and 503 dispatch_failed on the batch calls
    System.out.println("Status: " + e.getStatusCode() + " code: " + e.getCode());
} catch (KloakdException e) {
    // base class — network / IO errors
}
```

## Examples

`examples/Quickstart.java` is not part of the published jar. Build the SDK,
then run the example as a single source file:

```bash
mvn -q package -DskipTests -Dgpg.skip
mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp target/classes:$(cat cp.txt) examples/Quickstart.java
```

## Running Tests

```bash
mvn test
mvn --batch-mode clean verify -Dgpg.skip   # tests, the jars, Javadoc, JaCoCo report and check
```

- The code is compiled for Java 21 (`pom.xml` `<release>21</release>`). Maven
  uses the JDK it runs on (`mvn -v` shows it), which need not be the `java`
  on your PATH.
- `verify` without `-Dgpg.skip` also signs the jars with gpg. It needs a
  signing key and a terminal for the passphrase; with neither it stops at
  the signing step and waits. Use `-Dgpg.skip` for anything but a release.
- JaCoCo 0.8.12 runs with the tests and fails the build under 75% line or
  60% branch coverage. On a JDK newer than it knows (JDK 25) it prints
  "Unsupported class file major version" stack traces for the JDK's own
  classes; the SDK's classes are still measured and the check still runs.

## License

MIT
