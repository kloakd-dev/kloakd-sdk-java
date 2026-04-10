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
  <version>0.1.0</version>
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

// 2. Site crawl (reuse artifact)
var crawl = client.webgrph().crawl("https://target.com");
System.out.println("Pages found: " + crawl.totalPages());

// 3. Data extraction (reuse fetch artifact)
var data = client.kolektr().page("https://target.com/products",
    null, page.artifactId(), null, null, 0, 0);
data.records().forEach(System.out::println);
```

## Modules

| Namespace | Description |
|-----------|-------------|
| `client.evadr()` | Anti-bot fetch, analysis, proxy storage |
| `client.webgrph()` | Site crawl, hierarchy, job status |
| `client.skanyr()` | API endpoint discovery |
| `client.nexus()` | AI page analysis, strategy synthesis, execution |
| `client.parlyr()` | Natural language → scrape intent parsing |
| `client.fetchyr()` | Authenticated browser sessions, workflows |
| `client.kolektr()` | Structured data extraction |

## API Patterns

### Synchronous (default)

```java
var result = client.evadr().fetch("https://example.com");
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

### Pagination helpers

All list methods have an `*All` variant that auto-paginates:

```java
List<PageNode> pages = client.webgrph().crawlAll("https://example.com", 3, 500);
List<Map<String, Object>> records = client.kolektr().pageAll("https://example.com", schema);
```

## Configuration

```java
Kloakd client = Kloakd.builder()
    .apiKey("sk-live-...")
    .organizationId("your-org-uuid")
    .baseUrl("https://api.kloakd.dev")   // optional
    .timeout(Duration.ofSeconds(60))      // optional, default 30s
    .maxRetries(3)                        // optional, default 3
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
} catch (RateLimitException e) {
    // 429 — quota exceeded
    System.out.println("Retry after: " + e.getRetryAfter() + "s");
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
```

## License

MIT
