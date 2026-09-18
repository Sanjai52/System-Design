# Multithreaded Crawling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give each crawl job a fixed pool of `crawler.thread-count` workers with per-job queues and exact page accounting, fixing the shared-queue corruption, single-thread crawl, and dead config.

**Architecture:** New `CrawlJobContext` per job owns counters and lifecycle and fronts a per-job queue in a `QueueService` registry; `CrawlWorker` instances share the context with claim-slot exactness and idle termination; `CrawlService` runs a fixed pool per job with last-worker-out completion.

**Tech Stack:** Java 17, Spring Boot 3.2.5, `java.util.concurrent` (LinkedBlockingQueue, AtomicInteger, AtomicBoolean), Lettuce/Redis (unchanged), JUnit5 + Mockito.

**Spec:** `docs/superpowers/specs/2026-09-18-crawler-multithreading-design.md`

## Global Constraints

- Java version floor: 17 (do not use Java 21 APIs such as virtual threads).
- Spring Boot version: 3.2.5 (do not upgrade).
- Redis keys unchanged: `crawl:visited:{job}`, `crawl:job:{job}`, `crawl:result:{job}:{urlHash}` (no new keys in this phase).
- Worker count per job: `Math.max(1, config.getThreadCount())`, default 4 from `crawler.thread-count`.
- `pagesCrawled` must land exactly on `maxPages` (claim-slot protocol, no overshoot).
- No new Maven dependencies.
- Manual verification base URL: `http://localhost:8900`.
- All paths below are relative to `web-crawler/`.

---

## File Structure

- Create: `src/main/java/com/crawler/worker/CrawlJobContext.java` — per-job shared state: counters, lifecycle flags, queue façade over the registry.
- Modify: `src/main/java/com/crawler/service/QueueService.java` — replace global queue with `Map<String,BlockingQueue<CrawlTask>>` registry (`createQueue/removeQueue/addTask(jobId)/pollTask(jobId,timeoutMs)/queueSize(jobId)`).
- Modify: `src/main/java/com/crawler/worker/CrawlWorker.java` — full rewrite onto context: claim-slot loop, in-flight tracking, idle termination, last-worker completion.
- Modify: `src/main/java/com/crawler/service/CrawlService.java` — full rewrite: `JobHandle` map, fixed pool per job, new stop path.
- Test: `src/test/java/com/crawler/worker/JobContextTest.java` (new), rewrite `src/test/java/com/crawler/service/QueueTaskTest.java`, new `src/test/java/com/crawler/worker/CrawlWorkerEdgeTest.java`.

Task chain: Task 1 (context) → Task 2 (registry) → Task 3 (worker) → Task 4 (service) → Task 5 (live verify :8900, no commit).

---

### Task 1: CrawlJobContext + unit tests

**Files:**
- Create: `src/main/java/com/crawler/worker/CrawlJobContext.java`
- Test: `src/test/java/com/crawler/worker/JobContextTest.java`

**Interfaces:**
- Consumes: `QueueService.createQueue/removeQueue/addTask(String,CrawlTask)/pollTask(String,long)/queueSize(String)` from Task 2 (test uses the real current QueueService only for construction; claim/drain tests never touch the queue).
- Produces: `new CrawlJobContext(String jobId, int maxPages, int workers, QueueService queueService)`; `claimSlot()` returning slot index, capped so `getPages()` never exceeds maxPages; `isDrained()` (inFlight==0 and queue empty); `markWorkerDone()` true exactly once for the last worker; `addTask/pollTask/queueEmpty/enterFetch/exitFetch/getPages/getUrls/getFailed/getInFlight/addUrls/countFailed/isRunning/stop/getJobId/getMaxPages`.

- [ ] **Step 1: Write the failing test**

```java
package com.crawler.worker;

import com.crawler.service.QueueService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class JobContextTest {

    @Test
    void claimSlotCapsExactlyAtMaxPages() {
        CrawlJobContext ctx = new CrawlJobContext("j", 3, 1, new QueueService());
        assertEquals(0, ctx.claimSlot());
        assertEquals(1, ctx.claimSlot());
        assertEquals(2, ctx.claimSlot());
        assertEquals(3, ctx.claimSlot());
        assertEquals(3, ctx.claimSlot());
        assertEquals(3, ctx.getPages());
    }

    @Test
    void markWorkerDoneTrueExactlyOnceForLastWorker() {
        CrawlJobContext ctx = new CrawlJobContext("j", 5, 2, new QueueService());
        assertFalse(ctx.markWorkerDone());
        assertTrue(ctx.markWorkerDone());
        assertFalse(ctx.markWorkerDone());
    }

    @Test
    void drainedPredicateTracksInflightAndQueue() {
        CrawlJobContext ctx = new CrawlJobContext("j", 5, 1, new QueueService());
        assertTrue(ctx.isDrained());
        ctx.enterFetch();
        assertFalse(ctx.isDrained());
        ctx.exitFetch();
        assertTrue(ctx.isDrained());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=JobContextTest test`
Expected: FAIL with "CrawlJobContext not found" (compilation error).

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/crawler/worker/CrawlJobContext.java` with this exact content:

```java
package com.crawler.worker;

import com.crawler.model.CrawlTask;
import com.crawler.service.QueueService;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CrawlJobContext {

    private final String jobId;
    private final int maxPages;
    private final QueueService queueService;
    private final AtomicInteger pages = new AtomicInteger(0);
    private final AtomicInteger urls = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final AtomicInteger liveWorkers;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean finished = new AtomicBoolean(false);

    public CrawlJobContext(String jobId, int maxPages, int workers, QueueService queueService) {
        this.jobId = jobId;
        this.maxPages = maxPages;
        this.liveWorkers = new AtomicInteger(workers);
        this.queueService = queueService;
    }

    public String getJobId() { return jobId; }
    public int getMaxPages() { return maxPages; }

    public void addTask(CrawlTask task) { queueService.addTask(jobId, task); }

    public CrawlTask pollTask(long timeoutMs) {
        try {
            return queueService.pollTask(jobId, timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public boolean queueEmpty() { return queueService.queueSize(jobId) == 0; }

    public int claimSlot() { return pages.getAndUpdate(x -> x < maxPages ? x + 1 : x); }

    public int getPages() { return pages.get(); }
    public int getUrls() { return urls.get(); }
    public int getFailed() { return failed.get(); }
    public int getInFlight() { return inFlight.get(); }

    public void addUrls(int n) { urls.addAndGet(n); }
    public void countFailed() { failed.incrementAndGet(); }
    public void enterFetch() { inFlight.incrementAndGet(); }
    public void exitFetch() { inFlight.decrementAndGet(); }

    public boolean isDrained() { return inFlight.get() == 0 && queueEmpty(); }

    public boolean isRunning() { return running.get(); }
    public void stop() { running.set(false); }

    public boolean markWorkerDone() {
        if (liveWorkers.decrementAndGet() == 0) return finished.compareAndSet(false, true);
        return false;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=JobContextTest test`
Expected: PASS (Tests run: 3, Failures: 0, Errors: 0).

- [ ] **Step 5: Commit**

```bash
git add web-crawler/src/main/java/com/crawler/worker/CrawlJobContext.java web-crawler/src/test/java/com/crawler/worker/JobContextTest.java
git commit -m "feat(crawler): per-job crawl context with atomic counters"
```

---

### Task 2: QueueService per-job registry

**Files:**
- Modify: `src/main/java/com/crawler/service/QueueService.java` (full replacement below)
- Test: rewrite `src/test/java/com/crawler/service/QueueTaskTest.java` (full replacement below)

**Interfaces:**
- Consumes: `CrawlTask` (exists).
- Produces: `createQueue(String jobId)`, `removeQueue(String jobId)`, `addTask(String jobId, CrawlTask task)` (throws IllegalStateException without queue), `pollTask(String jobId, long timeoutMs)` throws InterruptedException, `queueSize(String jobId)` (0 without queue). Old global methods are deleted.

- [ ] **Step 1: Write the failing test**

```java
package com.crawler.service;

import com.crawler.model.CrawlTask;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class QueueTaskTest {

    @Test
    void perJobQueuesNeverCross() throws Exception {
        QueueService q = new QueueService();
        q.createQueue("j1");
        q.createQueue("j2");
        q.addTask("j1", new CrawlTask("https://a.test", null, 0));
        q.addTask("j2", new CrawlTask("https://b.test", null, 0));

        assertEquals("https://a.test", q.pollTask("j1", 100).getUrl());
        assertEquals("https://b.test", q.pollTask("j2", 100).getUrl());
        assertNull(q.pollTask("j1", 50));
    }

    @Test
    void removedQueueRejectsTasks() {
        QueueService q = new QueueService();
        q.createQueue("j1");
        q.removeQueue("j1");

        assertEquals(0, q.queueSize("j1"));
        assertThrows(IllegalStateException.class,
                () -> q.addTask("j1", new CrawlTask("https://c.test", null, 0)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=QueueTaskTest test`
Expected: FAIL with "cannot find symbol: method createQueue / addTask(String, CrawlTask)" (compilation error against the old global-queue API).

- [ ] **Step 3: Write minimal implementation**

Replace `src/main/java/com/crawler/service/QueueService.java` with this exact content:

```java
package com.crawler.service;

import com.crawler.model.CrawlTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final ConcurrentHashMap<String, LinkedBlockingQueue<CrawlTask>> queues = new ConcurrentHashMap<>();

    public void createQueue(String jobId) {
        queues.putIfAbsent(jobId, new LinkedBlockingQueue<>());
    }

    public void removeQueue(String jobId) {
        queues.remove(jobId);
    }

    public void addTask(String jobId, CrawlTask task) {
        queueFor(jobId).add(task);
        log.debug("Enqueued URL: {} (queue size: {})", task.getUrl(), queueFor(jobId).size());
    }

    public CrawlTask pollTask(String jobId, long timeoutMs) throws InterruptedException {
        return queueFor(jobId).poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public int queueSize(String jobId) {
        LinkedBlockingQueue<CrawlTask> q = queues.get(jobId);
        return q != null ? q.size() : 0;
    }

    private LinkedBlockingQueue<CrawlTask> queueFor(String jobId) {
        LinkedBlockingQueue<CrawlTask> q = queues.get(jobId);
        if (q == null) throw new IllegalStateException("No queue for job: " + jobId);
        return q;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest='QueueTaskTest,JobContextTest' test`
Expected: PASS (Tests run: 5, Failures: 0, Errors: 0). Note: `CrawlWorker.java`, `CrawlService.java` and `CrawlWorkerEdgeTest` do not exist yet in this state — full `mvn test` will fail to compile them; that is expected and fixed in Tasks 3–4. Verify only that no error mentions `QueueService` or `CrawlJobContext`.

- [ ] **Step 5: Commit**

```bash
git add web-crawler/src/main/java/com/crawler/service/QueueService.java web-crawler/src/test/java/com/crawler/service/QueueTaskTest.java
git commit -m "feat(crawler): per-job queue registry (fixes cross-job corruption)"
```

---

### Task 3: CrawlWorker on shared context

**Files:**
- Modify: `src/main/java/com/crawler/worker/CrawlWorker.java` (full replacement below)
- Test: `src/test/java/com/crawler/worker/CrawlWorkerEdgeTest.java` (new)

**Interfaces:**
- Consumes: `CrawlJobContext` from Task 1 (exact method names); `QueueService` only indirectly via context; `PageFetcherService.fetchPage(String)`, `UrlDiscoveryService.extractLinks(Document,String)` + `isValidUrl` (unchanged); `CrawlStateRepository.markVisited/saveUrlResult/updateJobProgress/completeJob` (unchanged).
- Produces: `new CrawlWorker(String jobId, CrawlJobContext context, PageFetcherService, UrlDiscoveryService, CrawlStateRepository, Runnable finishHook)`; `run()` exits on stop flag, page cap, or drained queue; last worker runs `completeJob` + hook exactly once; `getPagesCrawled()/getUrlsDiscovered()` delegates.

- [ ] **Step 1: Write the failing test**

```java
package com.crawler.worker;

import com.crawler.model.CrawlStatus;
import com.crawler.model.CrawlTask;
import com.crawler.repository.CrawlStateRepository;
import com.crawler.service.PageFetcherService;
import com.crawler.service.QueueService;
import com.crawler.service.UrlDiscoveryService;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CrawlWorkerEdgeTest {

    @Mock
    PageFetcherService fetcher;

    @Mock
    CrawlStateRepository repo;

    UrlDiscoveryService discovery = new UrlDiscoveryService();
    QueueService queues = new QueueService();

    @Test
    void singleWorkerCrawlsOnePageAndFinishes() {
        when(repo.markVisited(any(), any())).thenReturn(true);
        when(fetcher.fetchPage("https://s.test")).thenReturn(Jsoup.parse(
                "<html><head><title>T</title></head><body><a href=\"https://s.test/a\">a</a></body></html>",
                "https://s.test"));
        queues.createQueue("j1");
        CrawlJobContext ctx = new CrawlJobContext("j1", 1, 1, queues);
        ctx.addTask(new CrawlTask("https://s.test", null, 0));
        AtomicBoolean hookRan = new AtomicBoolean(false);

        new CrawlWorker("j1", ctx, fetcher, discovery, repo, () -> hookRan.set(true)).run();

        assertEquals(1, ctx.getPages());
        assertTrue(hookRan.get());
        verify(repo).completeJob("j1");
        verify(repo).saveUrlResult(eq("j1"), argThat(r ->
                r.getStatus() == CrawlStatus.COMPLETED
                        && r.getChildUrls().equals(List.of("https://s.test/a"))));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=CrawlWorkerEdgeTest test`
Expected: FAIL with "no suitable constructor found for CrawlWorker" (old 6-arg ctor takes QueueService, not context+hook).

- [ ] **Step 3: Write minimal implementation**

Replace `src/main/java/com/crawler/worker/CrawlWorker.java` with this exact content:

```java
package com.crawler.worker;

import com.crawler.model.CrawlStatus;
import com.crawler.model.CrawlTask;
import com.crawler.model.UrlResult;
import com.crawler.repository.CrawlStateRepository;
import com.crawler.service.PageFetcherService;
import com.crawler.service.UrlDiscoveryService;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CrawlWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CrawlWorker.class);

    private final String jobId;
    private final CrawlJobContext context;
    private final PageFetcherService pageFetcherService;
    private final UrlDiscoveryService urlDiscoveryService;
    private final CrawlStateRepository crawlStateRepository;
    private final Runnable finishHook;

    public CrawlWorker(String jobId, CrawlJobContext context,
                       PageFetcherService pageFetcherService,
                       UrlDiscoveryService urlDiscoveryService,
                       CrawlStateRepository crawlStateRepository,
                       Runnable finishHook) {
        this.jobId = jobId;
        this.context = context;
        this.pageFetcherService = pageFetcherService;
        this.urlDiscoveryService = urlDiscoveryService;
        this.crawlStateRepository = crawlStateRepository;
        this.finishHook = finishHook;
    }

    @Override
    public void run() {
        log.info("CrawlWorker started for job: {}", jobId);

        try {
            while (context.isRunning()) {
                if (context.getPages() >= context.getMaxPages()) break;

                CrawlTask task = context.pollTask(200);
                if (task == null) {
                    if (!context.isRunning() || Thread.currentThread().isInterrupted()) break;
                    if (context.isDrained()) {
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        if (context.isDrained()) break;
                    }
                    continue;
                }

                int slot = context.claimSlot();
                if (slot >= context.getMaxPages()) {
                    context.addTask(task);
                    break;
                }

                context.enterFetch();
                try {
                    processUrl(task);
                } finally {
                    context.exitFetch();
                }
                crawlStateRepository.updateJobProgress(jobId, context.getPages(), context.getUrls());

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            if (context.markWorkerDone()) {
                try {
                    crawlStateRepository.updateJobProgress(jobId, context.getPages(), context.getUrls());
                    crawlStateRepository.completeJob(jobId);
                    log.info("Crawl job {} completed (pages: {}, discovered: {}, failed: {})",
                            jobId, context.getPages(), context.getUrls(), context.getFailed());
                } finally {
                    finishHook.run();
                }
            }
        }
    }

    private void processUrl(CrawlTask task) {
        String url = task.getUrl();
        log.info("Processing URL: {}", url);

        UrlResult crawling = new UrlResult(url, CrawlStatus.CRAWLING, 0);
        crawling.setParentUrl(task.getParentUrl());
        crawling.setDepth(task.getDepth());
        crawlStateRepository.saveUrlResult(jobId, crawling);

        try {
            Document document = pageFetcherService.fetchPage(url);
            log.info("Fetched page: {} (title: {})", url, document.title());

            List<String> discoveredUrls = urlDiscoveryService.extractLinks(document, url);
            int linkCount = discoveredUrls.size();
            context.addUrls(linkCount);

            java.util.List<String> newChildren = new java.util.ArrayList<>();
            for (String discoveredUrl : discoveredUrls) {
                if (crawlStateRepository.markVisited(jobId, discoveredUrl)) {
                    context.addTask(new CrawlTask(discoveredUrl, url, task.getDepth() + 1));
                    newChildren.add(discoveredUrl);
                }
            }

            UrlResult done = new UrlResult(url, CrawlStatus.COMPLETED, linkCount);
            done.setParentUrl(task.getParentUrl());
            done.setDepth(task.getDepth());
            done.setChildUrls(newChildren);
            crawlStateRepository.saveUrlResult(jobId, done);

            log.info("Completed URL: {} (links: {}, new: {})", url, linkCount, newChildren.size());

        } catch (Exception e) {
            context.countFailed();
            String errorMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("FAILED URL: {} — {}", url, errorMsg);

            UrlResult failedResult = new UrlResult(url, CrawlStatus.FAILED, 0);
            failedResult.setParentUrl(task.getParentUrl());
            failedResult.setDepth(task.getDepth());
            failedResult.setError(errorMsg);
            crawlStateRepository.saveUrlResult(jobId, failedResult);
        }
    }

    public void stop() {
        context.stop();
    }

    public int getPagesCrawled() { return context.getPages(); }
    public int getUrlsDiscovered() { return context.getUrls(); }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest='CrawlWorkerEdgeTest,JobContextTest,QueueTaskTest' test`
Expected: PASS (Tests run: 6, Failures: 0, Errors: 0). Full `mvn test` still fails to compile `CrawlService.java` (old call sites) — expected, fixed in Task 4.

- [ ] **Step 5: Commit**

```bash
git add web-crawler/src/main/java/com/crawler/worker/CrawlWorker.java web-crawler/src/test/java/com/crawler/worker/CrawlWorkerEdgeTest.java
git commit -m "feat(crawler): multithreaded worker with claim-slot and idle termination"
```

---

### Task 4: CrawlService fixed pool per job

**Files:**
- Modify: `src/main/java/com/crawler/service/CrawlService.java` (full replacement below)
- Test: full suite must pass (`mvn test`, 18 tests: 17 existing + 1 new counting JobContextTest's 3 — total is 7+1+3+6=17? recount at runtime; expectation is 0 failures)

**Interfaces:**
- Consumes: `CrawlJobContext` + `CrawlWorker(jobId,context,fetcher,discovery,repo,hook)` from Tasks 1/3; `QueueService.createQueue/removeQueue` from Task 2; `CrawlerConfig.getThreadCount()` (existing); repository + controller unchanged (same `startCrawl/getJobStatus/stopCrawl` signatures).
- Produces: same three public signatures; `workers = max(1, threadCount)` threads per job; `stopCrawl` stops pool via context flag + shutdownNow.

- [ ] **Step 1: Verify current failure (RED)**

Run: `mvn -q -Dtest=CrawlControllerTest test`
Expected: FAIL with compilation errors in `CrawlService.java` (old `newSingleThreadExecutor`, `queueService.clear()`, old worker ctor).

- [ ] **Step 2: Write minimal implementation**

Replace `src/main/java/com/crawler/service/CrawlService.java` with this exact content:

```java
package com.crawler.service;

import com.crawler.config.CrawlerConfig;
import com.crawler.model.CrawlJob;
import com.crawler.model.CrawlStatus;
import com.crawler.model.CrawlTask;
import com.crawler.repository.CrawlStateRepository;
import com.crawler.worker.CrawlJobContext;
import com.crawler.worker.CrawlWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CrawlService {

    private static final Logger log = LoggerFactory.getLogger(CrawlService.class);

    public record JobHandle(CrawlJobContext context, ExecutorService executor) {}

    private final CrawlerConfig config;
    private final QueueService queueService;
    private final PageFetcherService pageFetcherService;
    private final UrlDiscoveryService urlDiscoveryService;
    private final CrawlStateRepository crawlStateRepository;

    private final Map<String, JobHandle> activeJobs = new ConcurrentHashMap<>();

    public CrawlService(CrawlerConfig config,
                        QueueService queueService,
                        PageFetcherService pageFetcherService,
                        UrlDiscoveryService urlDiscoveryService,
                        CrawlStateRepository crawlStateRepository) {
        this.config = config;
        this.queueService = queueService;
        this.pageFetcherService = pageFetcherService;
        this.urlDiscoveryService = urlDiscoveryService;
        this.crawlStateRepository = crawlStateRepository;
    }

    public CrawlJob startCrawl(String seedUrl, int maxPages) {
        String jobId = UUID.randomUUID().toString();

        if (!urlDiscoveryService.isValidUrl(seedUrl)) {
            throw new IllegalArgumentException("Invalid seed URL: " + seedUrl);
        }

        CrawlJob job = new CrawlJob(jobId, seedUrl, maxPages);
        job.setStatus(CrawlStatus.QUEUED);
        crawlStateRepository.saveJob(job);

        int workers = Math.max(1, config.getThreadCount());
        queueService.createQueue(jobId);
        CrawlJobContext context = new CrawlJobContext(jobId, maxPages, workers, queueService);
        context.addTask(new CrawlTask(seedUrl, null, 0));
        crawlStateRepository.markVisited(jobId, seedUrl);

        ExecutorService executor = Executors.newFixedThreadPool(workers, new CrawlThreadFactory(jobId));
        activeJobs.put(jobId, new JobHandle(context, executor));

        crawlStateRepository.updateJobStatus(jobId, CrawlStatus.CRAWLING);

        Runnable finishHook = () -> {
            queueService.removeQueue(jobId);
            activeJobs.remove(jobId);
            log.info("Crawl job {} completed", jobId);
        };
        for (int i = 0; i < workers; i++) {
            executor.submit(new CrawlWorker(jobId, context, pageFetcherService,
                    urlDiscoveryService, crawlStateRepository, finishHook));
        }

        log.info("Started crawl job {} for seed URL: {} ({} workers)", jobId, seedUrl, workers);
        return job;
    }

    public CrawlJob getJobStatus(String jobId) {
        return crawlStateRepository.getJob(jobId);
    }

    public void stopCrawl(String jobId) {
        JobHandle handle = activeJobs.get(jobId);
        if (handle != null) {
            handle.context().stop();
            handle.executor().shutdownNow();
        }

        crawlStateRepository.updateJobStatus(jobId, CrawlStatus.COMPLETED);
        log.info("Stopped crawl job: {}", jobId);
    }

    private static final class CrawlThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(0);

        CrawlThreadFactory(String jobId) {
            this.prefix = "crawl-" + jobId.substring(0, 8) + "-";
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
```

- [ ] **Step 3: Run full suite to verify it passes**

Run: `mvn test`
Expected: BUILD SUCCESS, Tests run total 18 (7 HierarchyServiceTest + 1 QueueTaskTest... exact counts printed), 0 failures. (Pre-existing counts: HierarchyServiceTest 7, QueueTaskTest 2, ModelFieldsTest 3, CrawlControllerTest 6 = 18; plus JobContextTest 3, CrawlWorkerEdgeTest 1 = 22 total. Assert 0 failures regardless of exact total.)

- [ ] **Step 4: Commit**

```bash
git add web-crawler/src/main/java/com/crawler/service/CrawlService.java
git commit -m "feat(crawler): fixed worker pool per job (uses thread-count)"
```

---

### Task 5: Live verify multithreading on :8900 (no commit)

**Files:** none (verification only).

- [ ] **Step 1: Rebuild and restart on :8900**

Run: `mvn -q -DskipTests package`
Expected: BUILD SUCCESS.

Run: `OLD=$(ss -tlnp | grep ':8900' | grep -oP 'pid=\K[0-9]+' | head -1); kill -9 "$OLD"; sleep 3; nohup java -jar target/web-crawler-1.0.0.jar --server.port=8900 > /tmp/webcrawler-8900.log 2>&1 & echo STARTED:$!; sleep 10; ss -tlnp | grep 8900`
Expected: `STARTED:<pid>` then `LISTEN … *:8900`. (SIGKILL is required — this app ignores SIGTERM.)

- [ ] **Step 2: Exact page accounting on one job**

Run: `JOB=$(curl -s -X POST http://localhost:8900/api/crawl -H "Content-Type: application/json" -d '{"url":"https://example.com","maxPages":5}' | python3 -c "import sys,json;print(json.load(sys.stdin)['jobId'])"); sleep 15; curl -s http://localhost:8900/api/crawl/$JOB | python3 -c "import sys,json;d=json.load(sys.stdin);assert d['status']=='COMPLETED',d;assert d['pagesCrawled']==5,d;print('exact-count OK:',d)"`
Expected: `exact-count OK:` with `pagesCrawled: 5` (claim-slot exactness — multithreaded overshoot would fail this assert).

- [ ] **Step 3: Hierarchy intact under threads**

Run: `curl -s http://localhost:8900/api/crawl/$JOB/hierarchy | python3 -c "import sys,json;d=json.load(sys.stdin);assert d['tree']['children'], 'empty tree';print('tree OK, root children:',len(d['tree']['children']))"`
Expected: `tree OK, root children: N` with N ≥ 1.

- [ ] **Step 4: Two simultaneous jobs stay isolated**

Run: `A=$(curl -s -X POST http://localhost:8900/api/crawl -H "Content-Type: application/json" -d '{"url":"https://example.com","maxPages":3}' | python3 -c "import sys,json;print(json.load(sys.stdin)['jobId'])"); B=$(curl -s -X POST http://localhost:8900/api/crawl -H "Content-Type: application/json" -d '{"url":"https://example.org","maxPages":3}' | python3 -c "import sys,json;print(json.load(sys.stdin)['jobId'])"); sleep 15; for J in $A $B; do curl -s http://localhost:8900/api/crawl/$J | python3 -c "import sys,json;d=json.load(sys.stdin);assert d['status']=='COMPLETED' and d['pagesCrawled']==3,d;print('isolated OK:',d['jobId'],d['seedUrl'])"; done`
Expected: two `isolated OK` lines, one per seed (the old shared-queue bug would show mixed counts or stuck jobs).

---

## Self-Review

- Spec coverage: defect 1 (shared queue/clear) → Task 2 registry (no `clear()` anywhere); defect 2 (single thread) → Tasks 3–4 pool; defect 3 (dead threadCount) → Task 4 `Math.max(1, config.getThreadCount())`; claim exactness → Task 1+3+5; idle termination → Tasks 1/3; last-worker completion → Tasks 1/3/4; stop → Task 4; politeness delay kept → Task 3; Redis unchanged → no task needed (verified by absence of repository changes).
- Placeholder scan: every step has exact code/commands/expected outputs; no TBD/TODO/similar-to.
- Type consistency: `CrawlJobContext(String,int,int,QueueService)` identical in Tasks 1/3/4; `CrawlWorker(String,CrawlJobContext,PageFetcherService,UrlDiscoveryService,CrawlStateRepository,Runnable)` identical in Tasks 3/4; `QueueService.createQueue/removeQueue/addTask(String,CrawlTask)/pollTask(String,long)/queueSize(String)` identical in Tasks 1/2/3/4; `JobHandle(CrawlJobContext,ExecutorService)` record used only in Task 4.
