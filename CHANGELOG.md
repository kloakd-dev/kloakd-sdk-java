# Changelog

All notable changes to the KLOAKD Java SDK will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.0] — 2026-10-28

0.3.1 never reached Maven Central either: its release build stopped at the
JaCoCo check (line coverage 0.63 of the 0.75 required). 0.4.0 measures 0.86
line, 0.73 branch under the same `mvn --batch-mode clean verify`.

### Added
- Every route that can hand a request off as a job is followed: past about
  85 s the API answers 202 with `job_id` and `poll_url`
  (`/api/v1/organizations/{id}/handoff/{job_id}`), and the SDK polls it and
  returns the same typed result as a direct answer. Covers
  `kolektr().extract` (and the deprecated `page`), `fetchyr().authenticate`,
  `detectForms`, `fillForm`, `detectMfa`, `nexus().analyze`, `synthesize`,
  `execute`, as well as `evadr().fetch`, which now shares the one follower
  (`HttpTransport.postFollowing` / `followHandOff`) and follows its own
  `poll_url` (`evadr/jobs/{id}`).
- Polling waits `pollInterval` before the first poll, then 1.5 times longer
  each time, at most 15 s; it stops at `pollTimeout`. Defaults 3 s / 15 min,
  set per client with `Kloakd.builder().pollInterval(...)` and
  `.pollTimeout(...)`; `evadr().fetch` keeps its per-call overload.
- `JobFailedException` (a failed job, or one the poll no longer finds: results
  are kept for an hour) and `JobTimeoutException`, with `getJobId()`. Both
  extend `ApiException` and keep the codes `job_failed` / `job_timeout` that
  0.3 threw.
- `NexusExecuteResult.executionResultId()` (`execution_result_id`): the id to
  pass to `nexus().knowledge(...)`.

### Removed (breaking)
- The Nexus recommendation methods, whose routes the API removed (they
  returned invented results): `recommendAnalyze`,
  `listRecommendationApplications`, `getCacheStatistics`, `cleanupCache`,
  `invalidateCache`, `getHooksStatus`, `enableHook`, `disableHook`,
  `createPreference`, `getPreferences`, `updatePreference`,
  `deletePreference`, `getRecommendationStatistics`.
- `kolektr().createJob` (`POST kolektr/jobs`, removed for the same reason).
  `listJobs` and `getJob` remain.
- Kolektr's job-progress, phase and pipeline methods, whose routes the API
  removed (they answered fixed values for any id): `getJobProgress`,
  `getJobProgressSummary`, `getJobProgressEvents`, `getJobProgressLatest`,
  `listProgressPhases`, `getProgressPhase`, `getProgressPhaseSteps`,
  `getProgressSummary`, `getPipelineStream`, `getPipelineEvents`.
- `webgrph().getJobTrends` and `getErrorSummary` (routes removed).
- `evadr().listProfiles` (`GET evadr/profiles`, route removed);
  `listVendors` remains.
- `webgrph().getDashboardSummary`, `getDiscoveryPatterns`,
  `getEfficiencyMetrics`, `getSiteMappingTrends`, `getUserBehaviorInsights`:
  their routes still exist but serve the KLOAKD dashboard only and are not
  part of the public API.

### Changed
- `NexusExecuteResult` gains a seventh component (`executionResultId`); code
  calling its constructor directly must pass it.
- `client.crawl` and `crawlStream` share one per-page fetch-and-extract step
  (behaviour unchanged).

## [0.3.1] — 2026-10-28

The 0.3.0 release build stopped at its Javadoc step (`@param hasMore` on
`ExtractionResult`, a field the record does not have), so 0.3.0 never reached
Maven Central. 0.3.1 is 0.3.0 with that comment corrected.

## [0.3.0] — 2026-10-27

Every call now targets a live API route (`ROUTES.md`; `LiveRoutesTest`
checks each call against `src/test/resources/routes.tsv`). Of 125 calls in
0.2.0, 78 were right, 32 had a wrong path, fields or behaviour, and 15 had
no live route.

### Added
- `evadr().fetch` follows a hand-off: past about 85 s the API answers 202
  with a job; the SDK polls `evadr/jobs/{id}` and returns the job's result
  (overload with poll interval and timeout; defaults 3 s / 15 min).
- `PaymentRequiredException` (402), `QuotaExceededException` (429
  `gb_exhausted`, not retried), `BillingUnavailableException` (503
  `billing_unavailable`, honours `Retry-After`); `KloakdException.getCode()`
  carries the API's error word.
- `evadr().startFetchJob`, `followJob`, `jobEvents` (stream), `profile`;
  `webgrph().startCrawl`, `crawlEvents` (stream), `getCrawlPages(id, cursor,
  limit)`, `cancelCrawl`, and the session, detected-API and reader-view
  methods (moved from Skanyr); `fetchyr().authenticate`, `resolveMfa`,
  `resumeExecution`, `discoverAuthChain`, `getAuthChainStatus`;
  `kolektr().extract`; Skanyr's site API (`listSites`, `createSiteFromCrawl`,
  `getSite`, `setSitePolicy`, `deleteSite`, `siteResources`, `siteApis`,
  `sync`, `search`, `extract`).

### Changed (breaking)
- `webgrph().crawl` waits for the crawl and returns every page, read by
  cursor (no limit/offset); `CrawlResult` gains `status`, loses `hasMore`;
  `PageNode` follows the API's node.
- `evadr().fetchAsync` starts a job and completes with its result (it parsed
  the job acknowledgement as a page); `fetchStream` streams the job's events;
  `scan(url)` takes the URL only.
- `fetchyr().createWorkflow(name, steps[, description, workflowType])`;
  `executeWorkflow(id, targetUrl[, sessionArtifactId, inputData])`;
  `fillForm` loses `submit`; `createMultiSiteWorkflow(name, sites)`;
  `checkDuplicates` → `checkDuplicate(url, contentHash, sessionId)`; result
  records follow the API's fields.
- `kolektr().page` → `extract` (`page(url)` remains, deprecated); the schema is
  sent as `schema_spec`; `pageAll` removed (extraction is not paginated);
  `extractHtml` posts to `kolektr/extract/html`.
- `nexus().analyze(url, html[, constraints])`; `synthesize(url, html, goal)`;
  result records follow the API.
- A JSON array response is returned as `{"items": [...]}` (it was dropped).

### Removed (no live route)
- `fetchyr().login` (use `storeCredentials` + `authenticate`),
  `fetchyr().fetch` (use `evadr().fetch` with a session artifact),
  `fetchyr().submitMfa`; `FetchyrResult`, `MfaResult`.
- `webgrph().getHierarchy`, `getJob`; `evadr().getJobEvents` (now `jobEvents`).
- Skanyr's discovery API (`discover`, `discoverAll`, `discoverStream`,
  `analyzeBundle`, `hierarchy`, `expandNode`, `retry`, `health`, `getApiMap`,
  `getJob`, `getDiscovery*`); `DiscoverResult`, `DiscoverEvent`, `ApiEndpoint`.

## [0.2.0] — 2026-05-13

### Added
- Full API coverage for all kernel module operations across 7 modules
- **Transport**: `put()` and `patch()` methods for full CRUD HTTP support
- **EvadrNamespace**: `scan`, `getJob`, `getJobEvents`, `listVendors`, `listProfiles`, `listProxies`, `deleteProxy`
- **FetchyrNamespace**: `storeCredentials`, `listCredentials`, `deleteCredentials`, `listSessions`, `terminateSession`, `fillForm`, `listMfaChallenges`, `getMfaChallenge`, `getMfaStatistics`, `listWorkflows`, `getWorkflow`, `updateWorkflow`, `deleteWorkflow`, `getWorkflowStatistics`, `createMultiSiteWorkflow`, `createDedupSession`, `listDedupSessions`, `getDedupSession`, `getDedupSessionStatistics`, `getDedupDomainStatistics`
- **KolektrNamespace**: `getApiData`, `getApiDataPaginated`, `extractAllApiData`, `listContent`, `getContent`, `deleteContent`, `listJobs`, `createJob`, `getJob`, `getJobStatus`, `getJobProgress`, `getJobProgressEvents`, `getJobProgressLatest`, `getJobProgressSummary`, `getPipelineEvents`, `getPipelineStream`, `listProgressPhases`, `getProgressPhase`, `getProgressPhaseSteps`, `getProgressSummary`, `listScrapers`, `createScraper`, `getScraper`, `updateScraper`, `deleteScraper`
- **NexusNamespace**: `reason`, `recommendAnalyze`, `listRecommendationApplications`, `getCacheStatistics`, `cleanupCache`, `invalidateCache`, `getHooksStatus`, `enableHook`, `disableHook`, `createPreference`, `getPreferences`, `updatePreference`, `deletePreference`, `getRecommendationStatistics`
- **SkanyrNamespace**: `getDiscovery`, `getDiscoveryEvents`, `analyzeBundle`, `discoverPageLive`, `detectedApis`, `hierarchy`, `expandNode`, `readerView`, `retry`, `health`, `listSessions`, `saveSession`, `getSession`, `deleteSession`, `endSession`, `updateSessionJob`
- **WebgrphNamespace**: `getCrawlStatus`, `getCrawlEvents`, `getCrawlPages`, `getDashboardSummary`, `getErrorSummary`, `getJobTrends`, `getDiscoveryPatterns`, `getEfficiencyMetrics`, `getSiteMappingTrends`, `getUserBehaviorInsights`

### Changed
- `checkDuplicates` endpoint corrected to `fetchyr/deduplication/check`

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
