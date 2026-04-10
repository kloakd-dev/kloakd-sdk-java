# Changelog

All notable changes to the KLOAKD Java SDK will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] — 2026-04-09

### Added

- **`Kloakd` client** — builder pattern with `apiKey`, `organizationId`, `baseUrl`, `timeout`, `maxRetries` configuration
- **`EvadrNamespace`** — `fetch`, `fetchAsync`, `fetchStream`, `analyze`, `storeProxy`
- **`WebgrphNamespace`** — `crawl`, `crawlAll`, `crawlStream`, `getHierarchy`, `getJob`
- **`SkanyrNamespace`** — `discover`, `discoverAll`, `discoverStream`, `getApiMap`, `getJob`
- **`NexusNamespace`** — `analyze`, `synthesize`, `verify`, `execute`, `knowledge`
- **`ParlyrNamespace`** — `parse`, `chat`, `chatStream`, `deleteSession`
- **`FetchyrNamespace`** — `login`, `fetch`, `createWorkflow`, `executeWorkflow`, `getExecution`, `detectForms`, `detectMfa`, `submitMfa`, `checkDuplicates`
- **`KolektrNamespace`** — `page`, `pageAll`, `extractHtml`
- **`HttpTransport`** — synchronous `HttpClient.send()` with exponential backoff retry, `CompletableFuture` async variant, SSE `Stream<T>` via line-iterator
- **Per-request timeout** — every `HttpRequest` carries the configured timeout, preventing indefinite blocking
- **Error hierarchy** — `KloakdException` → `AuthenticationException` (401), `NotEntitledException` (403), `RateLimitException` (429), `UpstreamException` (502), `ApiException` (other 4xx/5xx)
- **Model records** — all API responses as Java records: `FetchResult`, `FetchEvent`, `AnalyzeResult`, `CrawlResult`, `CrawlEvent`, `PageNode`, `DiscoverResult`, `DiscoverEvent`, `ApiEndpoint`, `NexusAnalyzeResult`, `NexusSynthesisResult`, `NexusVerifyResult`, `NexusExecuteResult`, `NexusKnowledgeResult`, `ParseResult`, `ChatTurn`, `ChatEvent`, `SessionResult`, `FetchyrResult`, `WorkflowResult`, `ExecutionResult`, `FormDetectionResult`, `MfaDetectionResult`, `MfaResult`, `DeduplicationResult`, `ExtractionResult`
- **Quickstart example** — `src/main/java/dev/kloakd/examples/Quickstart.java`
- **67 JUnit 5 tests** — covering all namespaces, transport retry/backoff, error mapping, SSE streaming, pagination
- **Coverage** — 88.7% line · 70.7% branch · 96.2% method (JaCoCo 0.8.12, thresholds 75% line / 60% branch enforced in `mvn verify`)
