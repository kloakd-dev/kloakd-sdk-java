# Changelog

All notable changes to the KLOAKD Java SDK will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Checked against kobe-scraper main 6bd19e9b; the batch and unified scrape
methods and the route list are of integration/2026-11-12b (228857dc).
**Breaking**: two methods and
two records are removed, and two values the API never sent are no longer
filled in (see Fixed), so the next release is 0.7.0. `ROUTES.md` lists the
routes.

### Fixed
- `webgrph().startCrawl(...)`: `CrawlResult.status()` is now null. `POST
  webgrph/crawl` answers with no status; the SDK read a field that is not
  there.
- `webgrph().waitForCrawl(...)`: `CrawlResult.url()` is no longer read from
  the status answer, which has no URL. It is the URL passed to `crawl(url,
  ...)`, and null on a result waited for by ID.
- The crawl waits (`webgrph().crawl`, `waitForCrawl`, `crawlAll`,
  `client.crawl`) and the crawl-extract waits (`waitForCrawlExtract`,
  `crawlExtract`) stop when the API holds the work for quota (`held_quota`)
  and return it. Before, they polled a held crawl until the timeout and
  threw `JobTimeoutException`.
- `ExportDownload.jobStatus()` is documented as it is: set for a
  crawl-extract export, null for a batch export (which sends no
  `X-Job-Status`).

### Changed
- `CrawlResult` has new components `isPartial`, `partialReason`, `resumable`,
  `pagesDiscovered`, `pagesFetched`, `pagesUnfetched`, `errorCode`, `message`
  and `held`. For a held crawl `held()` is true, `status()` is `held_quota`,
  `success()` is false, `error()` is null, `message()` is the API's text and
  `pages()` is empty. The 0.6.0 ten-argument constructor is kept.
- `SiteCrawlResult` has new components `crawlId`, `status`, `message` and
  `held`; `client.crawl` returns a held crawl with no pages fetched. The
  0.6.0 eight-argument constructor is kept.
- `waitForCrawlExtract` and `crawlExtract` return a copy of the status map
  with a `held` key (true or false) added by the SDK.
- `ExtractionResult` has new components `level`, `levelReason` and
  `loginOptions` (null when the API sends none). The 0.6.0 eleven-argument
  constructor is kept.
- `SavedExport` has a new component `crawl`. The five-argument constructor
  is kept.
- Code that matches these records with record patterns, or calls their
  canonical constructors, must be updated.
- `src/test/resources/routes.tsv` is the route list of kobe-scraper
  integration/2026-11-12b (228857dc): 280 routes. The API removed its 12
  `performance/...` routes; the SDK called none.

### Removed
- `nexus().execute(...)` (both overloads) and `NexusExecuteResult`: the API
  removed `POST nexus/execute`; the call could only fail.
- `nexus().knowledge(...)` and `NexusKnowledgeResult`: the route now takes
  `{strategy_id, url, outcome}` (the old `{execution_result_id}` body is
  refused) and is not part of the public API reference.

### Added
- `webgrph().waitForCrawl(crawlId)`: wait for a crawl by its ID with the
  default interval (3 s) and timeout (60 min).
- `webgrph().continueCrawl(crawlId)` and `CrawlContinueResult`: continue a
  crawl that stopped at its time budget (`POST
  webgrph/crawl/{crawl_id}/continue`). 409 (`ApiException`) when it cannot
  be continued.
- `WebgrphNamespace.HELD_QUOTA` and
  `WebgrphNamespace.isCrawlExtractHeld(status)`.
- `ExportDownload.crawl()` and `SavedExport.crawl()` (`ExportCrawl`): the
  crawl-extract export's `X-Crawl-*` headers (status, partial, partial
  reason, pages discovered, fetched and unfetched, cancelled, root
  unfetched). Null when the API sent none, as for a batch export.
- Login sessions (`fetchyr()`): `startLoginSession(targetUrl)`,
  `startLoginSession(targetUrl, LoginSessionOptions)`, `getSession(artifactId)`
  and `resumeSession(artifactId)`, with `LoginSession`, `LoginSessionOptions`
  and `SessionStatus`.
- `nexus().reason(queryType, parameters, perceptionId, timeoutSecs)`: sends
  `timeout_secs` when set.
- Batch jobs (`Kloakd`): `createBatchJob(urls)`, `createBatchJob(urls,
  BatchJobOptions)`, `getBatchJob`, `listBatchJobs()`, `listBatchJobs(page,
  pageSize)`, `getBatchJobResults(id)`, `getBatchJobResults(id, limit,
  offset)`, `pauseBatchJob`, `resumeBatchJob`, `cancelBatchJob`. A control
  the batch's state does not allow is a 409 `ApiException` with `getCode()`
  `invalid_batch_transition`; repeating a control answers the batch as it
  is; a resume or a create that could not be queued is a 503 `ApiException`
  with `getCode()` `dispatch_failed` and is not retried. Records `BatchJob`,
  `BatchJobsPage`, `BatchJobItem`, `BatchJobResults`, `BatchJobOptions`.
- Unified scrape (`Kloakd`): `unifiedScrape(url)`, `unifiedScrape(url,
  UnifiedScrapeOptions)` (`POST scraping/unified`, a 202 hand-off is
  followed) and `getUnifiedScrape(jobId)`. Records `UnifiedScrapeOptions`,
  `UnifiedScrapeResult`, `UnifiedScrapeStatus`, `UnifiedScrapeProgress`,
  `UnifiedEscalation`.
- `PageNode.fetchStatus()` and `fetchError()` (from the node's metadata).
- Crawl, extract and download in one job (`webgrph()`):
  `startCrawlExtract`, `crawlExtract`, `getCrawlExtract`,
  `waitForCrawlExtract`, `getCrawlExtractResults`, `crawlExtractAllResults`,
  `getCrawlExtractPages`, `exportCrawlExtract` (stream),
  `downloadCrawlExtract` (file), `cancelCrawlExtract`. The JSON answers are
  maps, as for `getCrawlStatus`. New `ExportDownload` (the body as an
  `InputStream`, `contentType()`, `filename()`, `jobStatus()`) and
  `SavedExport`. A `limit` outside 0–1000 or a `format` other than `json`,
  `ndjson` or `csv` throws `IllegalArgumentException` before the request.
- `client.exportBatchJob` and `client.downloadBatchJob`: a batch job's
  results as json, ndjson or csv.
- `NexusVerifyResult.status()`, `riskScore()`, `safetyChecks()`,
  `verificationResultId()`, `durationMs()` (new record components; the 0.6.0
  four-argument constructor is kept).
- `HttpTransport.download(path, params)`: a GET whose body is handed on
  unread. An error status is thrown (and retried) as for any GET; the
  request timeout covers the wait for the headers, not the body.

## [0.6.0] — 2026-11-03

Support for the API changes deployed on 2 November 2026, checked against
kobe-scraper 1c74de2b (deployed code 0bb0c6b5). No breaking changes: every
0.5.0 call compiles and sends the same request. `mvn --batch-mode clean
verify -Dgpg.skip` (JDK 21): 224 tests, line coverage 0.93, branch 0.81.

### Added
- Kolektr item selector (GAP-API-11): `schema_spec.item_selector` makes one
  record per element it matches, each field read inside that element
  (`modules/kolektr/router.py` `SchemaSpec`, `core/extraction/item_records.py`).
  - `ExtractSchema`: a typed schema (`fields` of `ExtractSchema.Field(name,
    selector, attribute, type)`, `itemSelector`, `pagination`, `maxPages`,
    `outputFormat`) with a builder (`field`, `listField`, `itemSelector`) and
    `toMap()` for the `schema(...)` / `extractSchema(...)` options. `build()`
    rejects a blank item selector, an item selector with no fields and
    `maxPages` outside 1–100 (the API's 422s).
  - `ExtractOptions.itemSelector(...)` (new record component; the 0.5.0
    seven-argument constructor is kept): sent as `schema_spec.item_selector`
    with the shorthand or a full spec.
  - `kolektr().extractHtml(html, url, schema, itemSelector)`.
  - The shorthand map accepts an `"item_selector"` entry (string value),
    sent as the spec's `item_selector` instead of a field.
  - `KolektrNamespace.schemaSpec(schema, itemSelector)`.
- Crawl pages (GAP-API-9/10): `webgrph().getCrawlPages(crawlId, cursor,
  limit, includeGrouping)` and `waitForCrawl(crawlId, interval, timeout,
  includeGrouping)` send `include_grouping=true` to include the hierarchy's
  grouping folders; `PageNode.isGrouping()` reads `metadata.is_grouping`.
  `getCrawlPages` checks `limit` (1–1000, `MAX_PAGES_LIMIT`; 0 for the API's
  default of 100) and throws `IllegalArgumentException` outside it (the API
  answers 422).
- Work slots (GAP-BILL-13): `ConcurrencyLimitException.getActiveRequests()`
  (`active_requests`, the other calls holding a slot) and a five-argument
  constructor (the 0.5.0 one is kept).

### Changed
- Documentation: by default the pages route lists only fetched pages, and
  the crawl status's `total_pages` / `max_depth_reached` (`CrawlResult.totalPages`,
  `maxDepthReached`) count fetched pages only; the raw pages of a crawl's
  `pipeline_complete` event carry `is_grouping` and `crawl_parent_url`;
  a 409 `concurrent_limit` means every work slot is in use (Free 2, Starter 5,
  Pro 10, Pro+ 20, Developer 50, Enterprise 100) and is still never retried.
- Version 0.6.0 (`pom.xml`, `HttpTransport.SDK_VERSION`, so `User-Agent:
  kloakd-sdk-java/0.6.0` and `X-Kloakd-SDK: java/0.6.0`).

## [0.5.0] — 2026-10-28

The 0.5.0 fixes shared by the four KLOAKD SDKs, checked against kobe-scraper
e5f15f7d. `mvn --batch-mode clean verify -Dgpg.skip`: 211 tests, line
coverage 0.92, branch 0.80.

### Breaking changes
- `ChatTurn` is rebuilt from the events the API sends (`thinking`,
  `interpretation`, `progress`, `results`, `error`, `warning`,
  `auth_required`, `mfa_required`, `clarification`, `export_ready`, `text`,
  then `end`): `sessionId, intent, response, clarification, interpretation,
  results, warnings, errors, actions, events` and `ok()`. The old
  `confidence`, `tier`, `entities`, `requiresAction` and
  `clarificationNeeded` were never sent. `response` joins the `text` and
  `clarification` contents; `intent` is `interpretation.intent` (the API
  sends none today, so null). `error` events are collected in `errors()`.
- `ChatEvent` is `(type, content, data, actions, timestamp)` (was
  `(event, data)`; `event()` remains, deprecated). `chatStream` stops at `end`.
- `CrawlEvent` is removed: `webgrph().crawlStream` returns
  `Stream<PipelineEvent>` (`eventType, stageId, status, message,
  progressPct, metadata, error, sequence, ...`).
- `FetchEvent` is `(type, jobId, status, tierUsed, artifactId, error,
  pipelineEvent)` (was `type, tier, vendor, metadata`, which the API never sent).
- `FetchResult` and `ExtractionResult` gain `authRequired` and
  `discoveryId` (new record components).
- `evadr().fetchAsync(url, forceBrowser, useProxy, webhookUrl[, interval,
  timeout])` is removed: its 4th argument was the webhook while `fetch`'s is
  the session. Use `fetchAsync(url, FetchOptions)`.
- `CrawlOptions`: `extractSchema` is `Map<String, ?>` (it can hold a
  `fields` list); `outputFormat` is removed (never used); `scope`, `budget`,
  `captureMode` are added.
- `skanyr().listSites()` returns `List<Map<String, Object>>` (was a map
  with `items`).
- A 403 is `NotEntitledException` only for `not_entitled` and
  `tier_access_denied`; any other 403 is the new `ForbiddenException`.
  `NotEntitledException` now extends `ForbiddenException` (and so `ApiException`).
- POST, PUT and PATCH are no longer retried on 5xx (the work may have been
  done). GET, HEAD and DELETE are, and also on network errors.
- `webgrph().crawl` throws `JobTimeoutException` (code still
  `crawl_timeout`, `getJobId()` the crawl id) instead of a plain `ApiException`.
- `examples/Quickstart.java` is no longer in the jar (`dev.kloakd.examples`).

### Fixed
- Default timeout 100 s (was 30 s): above the API's 85 s synchronous wait,
  so a slow request receives its 202 hand-off. It applies per attempt.
- The extraction schema shorthand `{field: "css:selector"}` is sent as
  `{"fields": [{"name", "selector"}]}` (`kolektr().extract`, `extractHtml`,
  `client.crawl`); a full spec with `fields` is sent as given.
- `client.crawl` reports the crawl's own `success` and `error`, and throws
  billing and auth errors (`PaymentRequired`, `QuotaExceeded`,
  `BillingUnavailable`, `NotEntitled`, `Authentication`, `RateLimit`)
  instead of marking every page failed.
- `client.crawlStream` streams: each event is produced when it is read
  (it used to run the whole crawl first).
- `webgrph().listSessions(false)` sends `active_only=false` (the API
  defaults to true, so false was ignored).
- 429 `rate_limited` is retried on any method after `Retry-After`,
  `retry_after` or `reset_at` (waits over 60 s are thrown). The playground
  quota (`retry_after` 0, no reset) is not retried: `RateLimitException`
  has `isQuotaExhausted()`, `getQuotaUsed()`, `getQuotaLimit()`, `getUpgradeUrl()`.
- 503 `billing_unavailable` is retried on any method.
- A non-JSON error body is the exception's message; a 422's `detail[]`
  becomes `loc: msg; ...`.
- Path values are percent-encoded, including the API endpoint URL of
  `kolektr/api-data/{api_endpoint:path}`.
- SSE: several `data:` lines are joined, comments skipped, and a read error
  is thrown instead of ending the stream silently; closing the stream closes
  the connection.

### Added
- `User-Agent: kloakd-sdk-java/0.5.0` on every request.
- `client.getHandoff(jobId)` and `client.waitForHandoff(jobId[, interval,
  timeout])` (`GET handoff/{job_id}`): resume after a `JobTimeoutException`.
  `webgrph().waitForCrawl(crawlId, ...)` does the same for a crawl.
- `ConcurrencyLimitException` for 409 `concurrent_limit`
  (`getConcurrentLimit`, `getActiveCrawls`, `getTier`).
- `NotEntitledException.getFeature()`, `getCurrentTier()`,
  `getRequiredTier()` (`tier_access_denied`); `QuotaExceededException.getTier()`.
- `FetchOptions` (`evadr().fetch/fetchAsync/startFetchJob/fetchStream(url, options)`),
  `ExtractOptions` (`kolektr().extract(url, options)`: `level`, `options`,
  `pipeline_id`), `CrawlOptions.Builder.buildAsSet()` and
  `webgrph().startCrawl/crawl(url, CrawlOptions)`.
- `nexus().reason(queryType, parameters[, perceptionId])`; `reason(Map)` is deprecated.
- Skanyr, as in the JS SDK: `updateSite`, `siteFromCrawl`, `exportSite`
  (ZIP bytes), `getSiteApis`, `getSiteResources`, `searchSite`,
  `getFindings`, `getTechnologies`, `getTechnologyEvidence`,
  `listSnapshots`, `getTimeline`, `getTimelineSnapshot`, `diff`,
  `getGraph`, `getGraphStats`, `getSubgraph`, `listResources`,
  `getResource`, `getExtractionText`, `getExtractionStructure`, and
  `search(..., mimeType)`. `setSitePolicy`, `createSiteFromCrawl`,
  `siteResources` and `siteApis` remain, deprecated.
- `fetchyr().resumeAuthChainCredentials` and `resumeAuthChainMfa`;
  `createMultiSiteWorkflow(name, sites, description, dependencies,
  globalConfig)` and `FetchyrNamespace.siteDependency(...)`.
- Paging: `kolektr().listJobs(page, limit, statusFilter)`,
  `listScrapers(page, pageSize, isTemplate)`, `listContent(page, pageSize,
  search)`, `fetchyr().listWorkflows(page, pageSize)`; api-data
  `endpointHash` and `maxRecords`.
- `webgrph().readerView(url, crawlId, sessionArtifactId)`.
- README: a Gradle snippet, retries, resuming hand-offs, schemas, chat.

## [0.4.1] — 2026-10-28

The 0.4.0 code, published. 0.4.0's upload was refused by Maven Central
("Bundle has content that does NOT have a .pom file: dev/kloakd/kloakd-sdk"):
central-publishing-maven-plugin 0.5.0 left an artifact-level file in the
bundle. The plugin is now 0.12.0. No API changes.

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
