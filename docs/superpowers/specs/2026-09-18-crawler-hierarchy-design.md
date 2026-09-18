# Web Crawler Hierarchy Visualisation — Design (2026-09-18)

## Goal
Add true discovery-tree hierarchy visualisation on a separate page, plus APIs for hierarchy and status search by seed URL. Stack: Spring Boot 3.2, Redis 7, Jsoup, vanilla JS. App runs on :8900.

## Current gap
- `UrlResult{url,status,discoveredLinks,error,timestamp}` stores only counts, no parent/children/depth.
- `CrawlStateRepository` keys: `crawl:visited:{job}`, `crawl:job:{job}`, `crawl:result:{job}:{urlHash}`. No seed index, no edge data.
- `CrawlWorker.processUrl()` discovers links but drops parent linkage.
- UI is single `static/index.html`, no hierarchy view.

## Decision (Approach A)
Server-side tree + edge storage. Single cycle/depth logic in Java, dumb JS renderer.

## 1. Data model
- `UrlResult` += `parentUrl:String|null`, `depth:int`, `childUrls:List<String>`.
- Redis HASH `crawl:result:{job}:{urlHash}` new fields: `parentUrl` (empty for seed), `depth`, `childUrls` (newline-joined, empty if none; newline chosen because URLs never contain raw newline, commas can appear encoded). Old hashes without fields load as `parentUrl=null, depth=0, childUrls=[]` — backward compatible.
- `CrawlWorker`: seed depth 0, parent null. On `extractLinks`, for each newly-visited URL record parent=current url, depth=current depth+1. After loop, update current URL's `childUrls` with newly-added children and re-save result. Failed fetch keeps parent/depth, status FAILED.
- No Redis migration; old jobs render via fallback (see §4).

## 2. APIs
- `GET /api/crawl/{jobId}/hierarchy?maxDepth=N` → `200 {jobId, seedUrl, status, tree:{url,status,depth,discoveredLinks,alreadyVisited,children:[...]}}`. Builds from `getUrlResults()` maps: url→result, parent→children. Root = seedUrl (job.seedUrl) or orphan with parent null. BFS expansion with `visitedInTree` set; repeat refs emitted as leaf with `alreadyVisited:true`, no expansion (handles page3→page1 cycles). `maxDepth` prunes deeper levels. Unknown job → `404`.
- `GET /api/crawl/search?seedUrl=q` → `200 [{jobId,seedUrl,maxPages,pagesCrawled,urlsDiscovered,status}]`. `q` required non-blank else `400`. Contains-match (case-insensitive) by scanning `crawl:job:*` keys via `redisTemplate.keys()` then `getJob()` filter. Lab-scale N is small; no secondary index.
- Existing endpoints unchanged.

## 3. Components
- `model/HierarchyNode.java` DTO: url, status, depth, discoveredLinks, alreadyVisited, children.
- `service/HierarchyService.java`: `buildTree(jobId,maxDepth)` + `searchBySeedUrl(query)`.
- `CrawlController`: add 2 GET mappings delegating to service/repository.
- `CrawlStateRepository`: add `getAllJobs()`, `findJobsBySeedContains()`, persist/load new fields, helper `getUrlResult(jobId,url)`.
- `static/hierarchy.html`: separate page, same dark theme as index.html. Controls: jobId input + seedUrl search input + maxDepth + load button; legend (QUEUED/CRAWLING/COMPLETED/FAILED/already-visited); tree `<ul>` collapsible (click toggles), status badge colors, expand-all/collapse-all, node counts. Links back to `/`. Fetches hierarchy + search APIs. No build step.

## 4. Data flow
Start crawl → worker saves parent/depth/children per URL → `GET hierarchy` reads results + job, nests in memory → UI renders. Search → scan jobs → UI lists matches → click job loads its tree.

## 5. Error handling / compat
- Unknown jobId on hierarchy → 404. Missing q → 400. Redis empty → tree with root only or `[]` for search.
- Old jobs (no edge fields): all non-seed nodes attach as direct children of root in timestamp order; alreadyVisited false. Guarantees renderable tree.
- Depth unbounded by default; `maxDepth` guards 50–100 node trees for readability. Commas in URLs: childUrls joined with `\n` separator instead of comma to avoid split bugs (URLs never contain raw newline).

## 6. Testing
- `curl POST /api/crawl {example.com,5}` → `GET hierarchy` asserts nested root + children + depths; `GET search?seedUrl=example` contains-match returns job; 404/400 cases.
- Cycle check with sample-pages (page1→page2→page3→page1) shows alreadyVisited leaf, no infinite recursion.
- UI manual on :8900/hierarchy.html: load by id, search by seed, collapse/expand.
- Existing JMeter plan still passes (endpoints additive only).

## Out of scope
Seed secondary index, depth enforcement in crawl loop, distributed queue, robots.txt, content storage.
