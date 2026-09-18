# Multithreaded Crawling — Design (2026-09-18)

## Goal
Give each crawl job a fixed pool of worker threads (sized by the existing `crawler.thread-count` config, default 4) and fix the three concurrency defects: shared singleton queue + `clear()` corrupting concurrent jobs, hardcoded single-thread executor, and dead `threadCount` setting.

## Current defects (with locations)
1. `service/QueueService.java:15,43-46` — one global `ConcurrentLinkedQueue` for all jobs; `CrawlService.startCrawl` calls `clear()`, wiping a running job's queue and mixing URLs across jobs.
2. `service/CrawlService.java:74` — `Executors.newSingleThreadExecutor()` per job; `worker/CrawlWorker.java:28-30` plain-int counters (`pagesCrawled`, `urlsDiscovered`, `failedCount`) that race under threads.
3. `config/CrawlerConfig.java:15` — `threadCount` never read anywhere.

## Decision (Approach A)
Per-job queue + fixed pool of `threadCount` workers per job. No Redis schema change (`SADD` dedup is already atomic and stays the single claim point for URLs).

## 1. Components
- New `worker/CrawlJobContext.java` — per-job shared state, the only cross-thread object: `LinkedBlockingQueue<CrawlTask> queue`; `AtomicInteger pages, urls, failed, inFlight, liveWorkers`; `AtomicBoolean running(true), finished(false)`; `int maxPages`. Methods: `claimSlot()` (atomic getAndUpdate, exact cap), `pollTask(timeout)`, `addTask(task)`, `shouldExitAfterIdle()` predicate, `markWorkerDone()` returning true for the last worker.
- `service/QueueService.java` — becomes a per-job registry: `ConcurrentHashMap<String,BlockingQueue<CrawlTask>>`; methods `createQueue(jobId)`, `removeQueue(jobId)`, `addTask(jobId,task)`, `pollTask(jobId,timeoutMs)`, `queueSize(jobId)`. The old global-queue methods (`addTask(task)`, `pollTask()`, `addUrl`, `pollUrl`, `size`, `isEmpty`, `clear`) are deleted; callers and `QueueTaskTest` updated.
- `worker/CrawlWorker.java` — ctor takes `(jobId, CrawlJobContext, PageFetcherService, UrlDiscoveryService, CrawlStateRepository)`; `maxPages` read from context. Loop: (a) if `!running` break; (b) if `pages.get() >= maxPages` break; (c) `task = poll(200ms)`; null → idle protocol (§2); else `slot = claimSlot()`; if over limit → requeue task to tail and break (keeps count exact, no livelock since every worker then exits); else `inFlight++`, `processUrl(task)` in try/finally with `inFlight--`.
- `service/CrawlService.java` — keeps `Map<String,JobHandle{context,executor}>` (replaces worker/executor maps); `startCrawl` creates queue via registry, seeks `threadCount = max(1, config.getThreadCount())` workers, submits all; each worker's finally calls `markWorkerDone()` and the last one runs `completeJob` + removes the handle. `stopCrawl` flips `running=false` + `shutdownNow` the job's pool; last-out path still completes normally.

## 2. Data flow & termination
Fetch → extract → `markVisited` (atomic claim) → enqueue children to the job's own queue → persist result with parent/depth/children (shape unchanged from hierarchy work). Progress via `updateJobProgress(jobId, pages.get(), urls.get())` after each URL. Idle protocol on null poll: if `inFlight==0` and queue empty → sleep 300ms → recheck once → exit. This closes the race where a worker sees an empty queue while another is mid-fetch about to enqueue. Completion exactly once via last-worker-wins (`liveWorkers` zero + `finished` CAS).

## 3. Stop & errors
`stopCrawl` interrupts pool threads; workers exit on flag/interrupt; failed fetches keep parent/depth + error exactly as today. The 100ms per-fetch politeness delay stays per worker (4 workers ≈ 4x politeness budget — accepted, unchanged config).

## 4. Testing
Deterministic unit tests only (no timing-flaky tests): slot-claim exactness at the cap single-threaded; idle/termination predicate states; per-job queue isolation (two jobs, interleaved adds/polls never cross); updated `QueueTaskTest` for jobId signatures. Live on :8900: crawl `maxPages=5` finishes with `pagesCrawled == 5` exactly; hierarchy tree intact; two simultaneous jobs both complete with correct separate counts. Existing 17 tests updated where signatures change; JMeter plan still passes (additive-safe: no API changes in this phase).

## Out of scope
Shared global pool, virtual threads (needs Java 21; project targets 17), per-request thread counts, crawl-delay config, Redis-backed queue.
