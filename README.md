# kloakd-sdk-java

Official Java SDK for the [KLOAKD API](https://kloakd.dev) — anti-bot bypassing, site crawling, API discovery, authenticated browsing, and intelligent data extraction.

## Requirements

- Java 21+
- Maven 3.8+

## Installation

Add to your `pom.xml`:

```xml
<dependency>
  <groupId>dev.kloakd</groupId>
  <artifactId>kloakd-sdk</artifactId>
  <version>0.4.1</version>
</dependency>
```

## Quickstart

```java
import dev.kloakd.sdk.Kloakd;

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

var result = client.evadr().fetch(url, false, false, null,
        Duration.ofSeconds(3), Duration.ofMinutes(15));
```

### Nexus: execute, then learn

```java
var exec = client.nexus().execute(strategyId, "https://example.com/products");
var learned = client.nexus().knowledge(exec.executionResultId());
```

### Asynchronous (`CompletableFuture`)

```java
CompletableFuture<FetchResult> future = client.evadr().fetchAsync("https://example.com");
future.thenAccept(r -> System.out.println(r.html()));
```

### SSE Streaming (`Stream<T>`)

```java
try (var stream = client.evadr().fetchStream("https://example.com")) {
    stream.forEach(event -> System.out.println(event.type()));
}
```

### Crawls

Crawls run asynchronously. `crawl` starts one, waits for it and reads every
page by cursor; `startCrawl` returns at once with the crawl ID.

```java
List<PageNode> pages = client.webgrph().crawlAll("https://example.com", 3, 500);
var started = client.webgrph().startCrawl("https://example.com", 3, 500, false, null);
```

## Configuration

```java
Kloakd client = Kloakd.builder()
    .apiKey("sk-live-...")
    .organizationId("your-org-uuid")
    .baseUrl("https://api.kloakd.dev")   // optional
    .timeout(Duration.ofSeconds(60))      // optional, default 30s
    .maxRetries(3)                        // optional, default 3
    .pollInterval(Duration.ofSeconds(3))  // optional: first wait on a 202 hand-off
    .pollTimeout(Duration.ofMinutes(15))  // optional: limit on following one
    .build();
```

## Error Handling

```java
import dev.kloakd.sdk.errors.*;

try {
    var result = client.evadr().fetch("https://example.com");
} catch (AuthenticationException e) {
    // 401 — invalid or expired API key
} catch (NotEntitledException e) {
    // 403 — org not on required plan
    System.out.println("Upgrade at: " + e.getUpgradeUrl());
} catch (PaymentRequiredException e) {
    // 402 — the plan's last payment failed
    System.out.println("Update payment at: " + e.getUpgradeUrl());
} catch (QuotaExceededException e) {
    // 429 gb_exhausted — allocation and top-ups used up (not retried)
    System.out.println("GB used " + e.getGbUsed() + " of " + e.getGbLimit());
} catch (RateLimitException e) {
    // 429 — too many requests
    System.out.println("Retry after: " + e.getRetryAfter() + "s");
} catch (BillingUnavailableException e) {
    // 503 billing_unavailable — usage could not be checked; retry later
} catch (JobFailedException e) {
    // a request handed off as a job (202) failed or expired
    System.out.println("Job " + e.getJobId() + ": " + e.getMessage());
} catch (JobTimeoutException e) {
    // still running at pollTimeout; the job may still finish
} catch (UpstreamException e) {
    // 502 — upstream site fetch failed
} catch (ApiException e) {
    // other 4xx/5xx
    System.out.println("Status: " + e.getStatusCode());
} catch (KloakdException e) {
    // base class — network / IO errors
}
```

## Running Tests

```bash
mvn test
mvn --batch-mode clean verify -Dgpg.skip   # the release build: tests, Javadoc, JaCoCo check
```

## License

MIT
