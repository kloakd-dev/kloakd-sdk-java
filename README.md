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
| `client.nexus()` | AI page analysis, strategy synthesis, execution |
| `client.parlyr()` | Natural language → scrape intent parsing |
| `client.fetchyr()` | Authenticated browser sessions, workflows |
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
`detectMfa` and `nexus().analyze`, `synthesize`, `execute`. Polls start
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

### Nexus: execute, then learn

```java
var exec = client.nexus().execute(strategyId, "https://example.com/products");
var learned = client.nexus().knowledge(exec.executionResultId());
```

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
page by cursor; `startCrawl` returns at once with the crawl ID.

```java
List<PageNode> pages = client.webgrph().crawlAll("https://example.com", 3, 500);
var started = client.webgrph().startCrawl("https://example.com", CrawlOptions.builder()
        .maxDepth(3).maxPages(500)
        .scope(Map.of("same_host", true))
        .captureMode("content")
        .buildAsSet());                  // unset limits are not sent: the tier's defaults apply
var done = client.webgrph().waitForCrawl(started.crawlId(), Duration.ofSeconds(3), Duration.ofMinutes(60));
```

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

```java
var page = client.webgrph().getCrawlPages(crawlId, null, 1000, true);
```

`client.crawl(url, options)` also fetches every page (and extracts it when
`extractSchema` is set). Its `success()` and `error()` are the crawl's. A
page that fails becomes a failed page, but billing and authentication
errors (`PaymentRequiredException`, `QuotaExceededException`,
`BillingUnavailableException`, `NotEntitledException`,
`AuthenticationException`, `RateLimitException`) are thrown.

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
    // or the response text when it is not JSON
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
mvn --batch-mode clean verify -Dgpg.skip   # the release build: tests, Javadoc, JaCoCo check
```

## License

MIT
