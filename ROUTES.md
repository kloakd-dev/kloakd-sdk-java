# Routes called by the Java SDK

## 0.6.0 → unreleased

Checked against kobe-scraper integration/2026-11-12 (e5e358aa). The route
list in `src/test/resources/routes.tsv` was edited to match (one row
removed, seven added), not regenerated. `LiveRoutesTest` now also scans
`download(...)` calls.

| Call | Java method | After 0.6.0 |
|---|---|---|
| `POST nexus/execute` | — | removed from the API (`modules/nexus/router.py`) and the SDK (`nexus().execute`) |
| `POST nexus/knowledge` | — | no longer called (`nexus().knowledge` removed): the body is now `{strategy_id, url, outcome}` and the route is left out of the public API reference (`scripts/generate_public_openapi.py` EXCLUDED). Still in the route list |
| `POST nexus/verify` | `nexus().verify` | response read for `status`, `risk_score`, `safety_checks`, `verification_result_id`, `duration_ms` as well (VerifyResponse) |
| `POST webgrph/crawl-extract` | `webgrph().startCrawlExtract`, `crawlExtract` | new (`modules/webgrph/crawl_extract_router.py`) |
| `GET webgrph/crawl-extract/{job_id}` | `getCrawlExtract`, `waitForCrawlExtract` | new |
| `GET webgrph/crawl-extract/{job_id}/results` | `getCrawlExtractResults`, `crawlExtractAllResults` | new: `limit` (1–1000), `cursor`, `shape`, `page_type`, `kind` |
| `GET webgrph/crawl-extract/{job_id}/pages` | `getCrawlExtractPages` | new: `limit`, `offset`, `status` |
| `GET webgrph/crawl-extract/{job_id}/export` | `exportCrawlExtract`, `downloadCrawlExtract` | new: `format` (json, ndjson, csv), `shape`, `page_type`, `kind`; streamed |
| `POST webgrph/crawl-extract/{job_id}/cancel` | `cancelCrawlExtract` | new |
| `GET batch-jobs/{batch_job_id}/export` | `client.exportBatchJob`, `downloadBatchJob` | new: `format`; streamed (`kernel/batch_export_router.py`) |

## 0.5.0 → 0.6.0

Checked against kobe-scraper 1c74de2b. No routes added or removed (`src/test/resources/routes.tsv` unchanged); request fields added:

| Call | Java method | 0.6.0 |
|---|---|---|
| `POST kolektr/extract`, `extract/html` | `kolektr().extract(url, ExtractOptions)`, `extractHtml(html, url, schema, itemSelector)` | `schema_spec.item_selector`; fields' `attribute` and `type` (`ExtractSchema`) (`modules/kolektr/router.py` `SchemaSpec`) |
| `GET webgrph/crawl/{id}/pages` | `webgrph().getCrawlPages(crawlId, cursor, limit, includeGrouping)`, `waitForCrawl(..., includeGrouping)` | `include_grouping`; `limit` checked 1–1000 (`modules/webgrph/router.py` `get_crawl_pages`) |
| any metered call | — | 409 `concurrent_limit` adds `active_requests` (`kernel/gateway/dependencies.py` `hold_work_slot`): `ConcurrencyLimitException.getActiveRequests()` |

## 0.4.1 → 0.5.0

Checked against kobe-scraper e5f15f7d (`src/kobe_scraper/modules/*/router.py`, `kernel/sync_handoff.py`). Every route below is in `src/test/resources/routes.tsv`; `LiveRoutesTest` (which now also scans `requestList`, `requestBytes` and `requestText`) and `RouteMappingTest` check the SDK's calls. Path values are percent-encoded (`HttpTransport.seg`).

| Call | Java method | 0.5.0 |
|---|---|---|
| `GET handoff/{job_id}` | `client.getHandoff`, `client.waitForHandoff` | new: public (`kernel/sync_handoff.py:158-174`) |
| `GET skanyr/resources` | `skanyr().listResources` | new; list |
| `GET skanyr/resources/{id}` | `skanyr().getResource` | new |
| `GET skanyr/extractions/{hash}/text` | `skanyr().getExtractionText` | new; plain text |
| `GET skanyr/extractions/{hash}/structure` | `skanyr().getExtractionStructure` | new |
| `POST skanyr/sites/{id}/export` | `skanyr().exportSite` | new; ZIP bytes |
| `GET skanyr/sites/{id}/diff`, `/findings`, `/graph`, `/graph/stats`, `/graph/subgraph/{rid}`, `/search`, `/snapshots`, `/technologies`, `/technology-evidence`, `/timeline`, `/timeline/{snap}` | `diff`, `getFindings`, `getGraph`, `getGraphStats`, `getSubgraph`, `searchSite`, `listSnapshots`, `getTechnologies`, `getTechnologyEvidence`, `getTimeline`, `getTimelineSnapshot` | new |
| `GET skanyr/sites`, `/sites/{id}/resources`, `/sites/{id}/apis` | `listSites`, `getSiteResources`, `getSiteApis` | return lists (`siteResources`, `siteApis` deprecated) |
| `PATCH skanyr/sites/{id}` | `skanyr().updateSite` | renamed from `setSitePolicy` (kept, deprecated) |
| `POST skanyr/sites/from-crawl/{id}` | `skanyr().siteFromCrawl` | renamed from `createSiteFromCrawl` (kept, deprecated) |
| `POST skanyr/search` | `skanyr().search(..., mimeType)` | `mime_type` |
| `POST fetchyr/auth-chain/{id}/resume-credentials`, `/resume-mfa` | `resumeAuthChainCredentials`, `resumeAuthChainMfa` | new |
| `POST fetchyr/multi-site-workflows` | `createMultiSiteWorkflow` | `description`, `dependencies`, `global_config` |
| `GET fetchyr/workflows` | `listWorkflows(page, pageSize)` | `page`, `page_size` |
| `GET kolektr/jobs` | `listJobs(page, limit, statusFilter)` | `page`, `limit`, `status_filter` |
| `GET kolektr/scrapers` | `listScrapers(page, pageSize, isTemplate)` | `page`, `page_size`, `is_template` |
| `GET kolektr/content` | `listContent(page, pageSize, search)` | `page`, `page_size`, `search` |
| `GET/POST kolektr/api-data/{api_endpoint:path}[/paginated, /extract-all]` | `getApiData`, `getApiDataPaginated`, `extractAllApiData` | endpoint percent-encoded; `endpoint_hash`, `max_records` |
| `POST kolektr/extract`, `extract/html` | `kolektr().extract(url, ExtractOptions)`, `extractHtml` | shorthand schema converted to `{fields: [...]}`; `level`, `options`, `pipeline_id` |
| `POST nexus/reason` | `nexus().reason(queryType, parameters)` | `{query_type, parameters: [str]}` |
| `POST webgrph/crawl` | `webgrph().startCrawl/crawl(url, CrawlOptions)` | `scope`, `budget`, `capture_mode` |
| `POST webgrph/reader-view` | `readerView(url, crawlId, sessionArtifactId)` | `session_artifact_id` |
| `GET webgrph/sessions` | `listSessions(activeOnly)` | always sends `active_only` (the API defaults to true) |
| `GET webgrph/crawl/{id}/events`, `GET evadr/jobs/{id}/events` | `crawlStream`, `fetchStream` | pipeline-event fields (`core/pipeline/models.py`); Evadr's polled events `{type, job_id, status, tier_used, artifact_id, error}` |
| `POST parlyr/chat` | `parlyr().chat`, `chatStream` | events `{type, content, data, actions, timestamp}` until `end` |

## 0.3.0 → 0.4.0

Checked against kobe-scraper's routers (`src/kobe_scraper/modules/*/router.py`, `kernel/sync_handoff.py`); `src/test/resources/routes.tsv` updated to match and `LiveRoutesTest` keeps the SDK to it.

| Call | 0.4.0 |
|---|---|
| `POST kolektr/extract` | follows a 202 hand-off (`@hands_off_after_limit("kolektr.extract")`) |
| `POST fetchyr/authenticate` | follows a 202 hand-off |
| `POST fetchyr/form/detect` | follows a 202 hand-off |
| `POST fetchyr/form/fill` | follows a 202 hand-off |
| `POST fetchyr/mfa/detect` | follows a 202 hand-off |
| `POST nexus/analyze` | follows a 202 hand-off |
| `POST nexus/synthesize` | follows a 202 hand-off |
| `POST nexus/execute` | follows a 202 hand-off; reads `execution_result_id` |
| `POST evadr/fetch` | follows its 202 via the same follower (`poll_url` = `evadr/jobs/{id}`) |
| `GET handoff/{job_id}` | new: the poll route of a hand-off (`poll_url`); not called directly |
| `POST kolektr/jobs` | removed (route removed) |
| `nexus/recommendations/*` (13 calls) | removed (routes removed) |
| `GET kolektr/jobs/{id}/progress`, `…/progress/summary`, `…/progress/events`, `…/progress/latest` | removed (routes removed, kobe-scraper d7d25134) |
| `GET kolektr/progress/phases`, `…/phases/{name}`, `…/phases/{name}/steps`, `GET kolektr/progress/summary` | removed (routes removed) |
| `GET kolektr/pipeline/{id}/stream`, `…/events` | removed (routes removed) |
| `GET webgrph/analytics/job-trends`, `…/error-summary` | removed (routes removed) |
| `GET evadr/profiles` | removed (route removed); `GET evadr/vendors` stays |
| `GET webgrph/analytics/dashboard/summary`, `…/content/discovery-patterns`, `…/scraping/efficiency-metrics`, `…/site-maps/trends`, `…/users/behavior-insights` | removed from the SDK: dashboard-only (`modules/webgrph/analytics_router.py`; `scripts/generate_public_openapi.py` EXCLUDED); routes kept in routes.tsv |

The fixture also drops the standalone `recommendations/*` routes, which `api/app.py` no longer mounts.

The 0.2.0 → 0.3.0 table below no longer lists the 18 calls removed in 0.4.0 (its counts are as of 0.3.0).

## 0.2.0 → 0.3.0

Checked against the backend's route list at kobe-scraper 4fb33ea4 (module routers mounted as `kernel/bootstrap.py` does), copied to `src/test/resources/routes.tsv`; `LiveRoutesTest.everySdkCallIsALiveRoute` keeps it so. Paths are relative to `/api/v1/organizations/{organization_id}/`.

**Counts:** fixed (fields or behaviour) 17, already right 78, fixed (path) 15, removed 15 (of 125 calls in 0.2.0).

| 0.2.0 call | Class | 0.3.0 |
|---|---|---|
| `POST evadr/fetch` | matches; behaviour fixed | 202 hand-off followed to the job result |
| `POST evadr/fetch/async` | matches a live route | — |
| `POST evadr/fetch/stream` | route removed | `evadr().fetchStream`: POST evadr/fetch/async, then GET evadr/jobs/{id}/events |
| `POST evadr/analyze` | matches a live route | — |
| `POST evadr/scan` | wrong fields | wrong fields: takes url only |
| `GET evadr/jobs/%s` | matches a live route | — |
| `GET evadr/jobs/%s/events` | matches; behaviour fixed | SSE: now a stream (`evadr().jobEvents`) |
| `GET evadr/vendors` | matches a live route | — |
| `GET evadr/proxies` | matches a live route | — |
| `POST evadr/proxies` | matches a live route | — |
| `DELETE evadr/proxies/%s` | matches a live route | — |
| `POST fetchyr/login` | route removed | `fetchyr().authenticate`: POST fetchyr/authenticate {credential_name, target_url} |
| `POST fetchyr/fetch` | route removed | removed; use Evadr fetch with session_artifact_id |
| `POST fetchyr/workflows` | wrong fields | wrong fields: url removed; description, workflow_type; response id |
| `POST fetchyr/workflows/%s/execute` | wrong fields | wrong fields: target_url required; response result |
| `GET fetchyr/workflows/%s/executions/%s` | matches a live route | — |
| `POST fetchyr/forms/detect` | wrong path | POST fetchyr/form/detect |
| `POST fetchyr/mfa/detect` | matches a live route | — |
| `POST fetchyr/mfa/submit` | route removed | `fetchyr().resolveMfa`: POST fetchyr/mfa-queue/{id}/resolve {resolution} |
| `POST fetchyr/account/credentials` | matches a live route | — |
| `GET fetchyr/account/credentials` | matches a live route | — |
| `DELETE fetchyr/account/credentials/%s` | matches a live route | — |
| `GET fetchyr/sessions` | matches a live route | — |
| `DELETE fetchyr/sessions/%s` | matches a live route | — |
| `POST fetchyr/form/fill` | wrong fields | wrong fields: submit removed |
| `GET fetchyr/mfa-queue` | matches; behaviour fixed | array response (was dropped): under items |
| `GET fetchyr/mfa/challenges/%s` | matches a live route | — |
| `GET fetchyr/mfa/statistics/%s` | matches a live route | — |
| `GET fetchyr/workflows` | matches a live route | — |
| `GET fetchyr/workflows/%s` | matches a live route | — |
| `PATCH fetchyr/workflows/%s` | matches a live route | — |
| `DELETE fetchyr/workflows/%s` | matches a live route | — |
| `GET fetchyr/workflows/%s/statistics` | matches a live route | — |
| `POST fetchyr/multi-site-workflows` | wrong fields | wrong fields: name required |
| `POST fetchyr/deduplication/check` | wrong fields | wrong fields: url (+content_hash, session_id); response is_duplicate |
| `POST fetchyr/deduplication/sessions` | matches a live route | — |
| `GET fetchyr/deduplication/sessions/active` | matches a live route | — |
| `GET fetchyr/deduplication/sessions/%s` | matches a live route | — |
| `GET fetchyr/deduplication/sessions/%s/statistics` | matches a live route | — |
| `GET fetchyr/deduplication/statistics/%s` | matches a live route | — |
| `POST kolektr/page` | wrong path | `kolektr().extract`: POST kolektr/extract (schema_spec) |
| `POST kolektr/extract-html` | wrong path | POST kolektr/extract/html (schema_spec) |
| `GET kolektr/api-data/%s` | matches a live route | — |
| `GET kolektr/api-data/%s/paginated` | matches a live route | — |
| `POST kolektr/api-data/%s/extract-all` | matches a live route | — |
| `GET kolektr/content` | matches a live route | — |
| `GET kolektr/content/%s` | matches a live route | — |
| `DELETE kolektr/content/%s` | matches a live route | — |
| `GET kolektr/jobs` | matches a live route | — |
| `POST kolektr/jobs` | matches a live route | — |
| `GET kolektr/jobs/%s` | matches a live route | — |
| `GET kolektr/extraction-jobs/%s/status` | matches a live route | — |
| `GET kolektr/scrapers` | matches a live route | — |
| `POST kolektr/scrapers` | matches a live route | — |
| `GET kolektr/scrapers/%s` | matches a live route | — |
| `PATCH kolektr/scrapers/%s` | matches a live route | — |
| `DELETE kolektr/scrapers/%s` | matches a live route | — |
| `POST nexus/analyze` | wrong fields | wrong fields: html required; response fields |
| `POST nexus/synthesize` | wrong fields | wrong fields: url, html, goal (not perception_id) |
| `POST nexus/verify` | matches; behaviour fixed | response fields: verified, safety_score |
| `POST nexus/execute` | matches; behaviour fixed | response fields; options |
| `POST nexus/knowledge` | matches; behaviour fixed | response fields: patterns, total_patterns |
| `POST nexus/reason` | matches a live route | — |
| `POST nexus/recommendations/analyze` | matches a live route | — |
| `GET nexus/recommendations/applications` | matches a live route | — |
| `GET nexus/recommendations/cache/statistics` | matches a live route | — |
| `POST nexus/recommendations/cache/cleanup` | matches a live route | — |
| `POST nexus/recommendations/cache/invalidate` | matches a live route | — |
| `GET nexus/recommendations/hooks/status` | matches a live route | — |
| `POST nexus/recommendations/hooks/%s/enable` | matches a live route | — |
| `POST nexus/recommendations/hooks/%s/disable` | matches a live route | — |
| `POST nexus/recommendations/preferences` | matches a live route | — |
| `GET nexus/recommendations/preferences/%s` | matches a live route | — |
| `PUT nexus/recommendations/preferences/%s` | matches a live route | — |
| `DELETE nexus/recommendations/preferences/%s` | matches a live route | — |
| `GET nexus/recommendations/statistics` | matches a live route | — |
| `POST parlyr/parse` | matches a live route | — |
| `POST parlyr/chat` | matches a live route | — |
| `POST parlyr/chat` | matches a live route | — |
| `DELETE parlyr/chat/%s` | matches a live route | — |
| `POST skanyr/discover` | route removed | removed (no live route) |
| `POST skanyr/discover/stream` | route removed | removed (no live route) |
| `GET skanyr/discover/%s` | route removed | removed (no live route) |
| `GET skanyr/discover/%s/events` | route removed | removed (no live route) |
| `POST skanyr/analyze-bundle` | route removed | removed (no live route) |
| `POST skanyr/discover-page/live` | route removed | removed (no live route) |
| `GET skanyr/detected-apis` | wrong path | moved to Webgrph: GET webgrph/detected-apis |
| `POST skanyr/hierarchy` | route removed | removed (no live route) |
| `POST skanyr/expand-node` | route removed | removed (no live route) |
| `POST skanyr/reader-view` | wrong path | moved to Webgrph: POST webgrph/reader-view |
| `POST skanyr/retry` | route removed | removed (no live route) |
| `GET skanyr/health` | route removed | removed (no live route) |
| `GET skanyr/sessions` | wrong path | moved to Webgrph: GET webgrph/sessions |
| `POST skanyr/sessions` | wrong path | moved to Webgrph: POST webgrph/sessions |
| `GET skanyr/sessions/%s` | wrong path | moved to Webgrph: GET webgrph/sessions/{id} |
| `DELETE skanyr/sessions/%s` | wrong path | moved to Webgrph: DELETE webgrph/sessions/{id} |
| `POST skanyr/sessions/%s/end` | wrong path | moved to Webgrph: POST webgrph/sessions/{id}/end |
| `PATCH skanyr/sessions/%s/job` | wrong path | moved to Webgrph: PATCH webgrph/sessions/{id}/job?job_id= |
| `GET skanyr/api-map/%s` | route removed | removed (no live route) |
| `GET skanyr/jobs/%s` | route removed | removed (no live route) |
| `POST webgrph/crawl` | matches; behaviour fixed | answers 202; `webgrph().crawl` now waits and reads pages by cursor; limit/offset removed |
| `POST webgrph/crawl/stream` | route removed | `webgrph().crawlStream`: POST webgrph/crawl, then GET webgrph/crawl/{id}/events |
| `GET webgrph/crawl/%s` | matches a live route | — |
| `GET webgrph/crawl/%s/events` | matches; behaviour fixed | SSE: now a stream (`webgrph().crawlEvents`) |
| `GET webgrph/crawl/%s/pages` | matches; behaviour fixed | cursor/limit params |
| `GET webgrph/hierarchy/%s` | route removed | removed (pages: GET webgrph/crawl/{id}/pages) |
| `GET webgrph/jobs/%s` | route removed | removed (status: GET webgrph/crawl/{id}) |
