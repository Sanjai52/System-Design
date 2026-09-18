# Crawler Hierarchy Visualisation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add parent/child edge tracking to the crawl, plus hierarchy-tree and seed-search APIs and a separate collapsible hierarchy page.

**Architecture:** Store `parentUrl/depth/childUrls` per URL in Redis, build the nested tree server-side in a new `HierarchyService` with cycle-guard, expose two new GET endpoints, render with a new static `hierarchy.html` page.

**Tech Stack:** Java 17, Spring Boot 3.2.5, Spring Data Redis (Lettuce), Jsoup (unchanged), vanilla JS, JUnit5 + Mockito (via spring-boot-starter-test).

**Spec:** `docs/superpowers/specs/2026-09-18-crawler-hierarchy-design.md`

## Global Constraints

- Java version floor: 17 (project runs on JDK 21, `java.version=17` in `web-crawler/pom.xml`).
- Spring Boot version: 3.2.5 (do not upgrade).
- Redis: 7, keys `crawl:visited:{job}`, `crawl:job:{job}`, `crawl:result:{job}:{urlHash}` (new HASH fields only, no key renames).
- Child URL serialisation: newline-joined (`\n`), never comma-joined (commas can appear in URLs).
- Manual verification base URL: `http://localhost:8900`.
- No new Maven dependencies.
- All paths below are relative to `web-crawler/`.

---

## File Structure

- Modify: `src/main/java/com/crawler/model/UrlResult.java` — add `parentUrl`, `depth`, `childUrls` with getters/setters; keep all existing constructors working.
- Create: `src/main/java/com/crawler/model/CrawlTask.java` — queue item `{url, parentUrl, depth}` so the worker knows each URL's parent and depth.
- Create: `src/main/java/com/crawler/model/HierarchyNode.java` — API DTO `{url, status, depth, discoveredLinks, alreadyVisited, children}`.
- Modify: `src/main/java/com/crawler/service/QueueService.java` — queue holds `CrawlTask` instead of `String`.
- Modify: `src/main/java/com/crawler/service/CrawlService.java` — enqueue seed as `CrawlTask(seedUrl, null, 0)`.
- Modify: `src/main/java/com/crawler/worker/CrawlWorker.java` — poll `CrawlTask`, persist parent/depth/childUrls on every save.
- Modify: `src/main/java/com/crawler/repository/CrawlStateRepository.java` — persist/load the 3 new HASH fields; add `getAllJobs()` and `findJobsBySeedContains(String)`.
- Create: `src/main/java/com/crawler/service/HierarchyService.java` — pure static tree builder + seed filter plus thin service methods.
- Modify: `src/main/java/com/crawler/controller/CrawlController.java` — add `GET /api/crawl/{jobId}/hierarchy` and `GET /api/crawl/search`.
- Create: `src/main/resources/static/hierarchy.html` — separate hierarchy page.
- Create: `src/test/java/com/crawler/service/HierarchyServiceTest.java` — unit tests, no Redis needed.
- Create: `src/test/java/com/crawler/controller/CrawlControllerTest.java` — MockMvc tests for the two new endpoints.

Task dependency chain: Task 1 (models) → Task 2 (queue+worker) → Task 3 (repository) → Task 4 (service+tests) → Task 5 (controller+tests) → Task 6 (UI + live verify on :8900).

---

### Task 1: Model fields (UrlResult + CrawlTask + HierarchyNode)

**Files:**
- Modify: `src/main/java/com/crawler/model/UrlResult.java`
- Create: `src/main/java/com/crawler/model/CrawlTask.java`
- Create: `src/main/java/com/crawler/model/HierarchyNode.java`
- Test: `src/test/java/com/crawler/model/ModelFieldsTest.java`

**Interfaces:**
- Consumes: nothing (foundation task).
- Produces: `UrlResult.getParentUrl()/setParentUrl(String)`, `getDepth()/setDepth(int)`, `getChildUrls()/setChildUrls(List<String>)`; `new CrawlTask(String url, String parentUrl, int depth)` with getters; `new HierarchyNode(String url, CrawlStatus status, int depth, int discoveredLinks)` with `isAlreadyVisited()/setAlreadyVisited(boolean)`, `getChildren()` mutable list.

- [ ] **Step 1: Write the failing test**

```java
package com.crawler.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ModelFieldsTest {
    @Test
    void urlResultHoldsParentDepthAndChildren() {
        UrlResult r = new UrlResult("https://example.com/a", CrawlStatus.COMPLETED, 2);
        r.setParentUrl("https://example.com");
        r.setDepth(1);
        r.setChildUrls(List.of("https://example.com/b"));
        assertEquals("https://example.com", r.getParentUrl());
        assertEquals(1, r.getDepth());
        assertEquals(List.of("https://example.com/b"), r.getChildUrls());
    }

    @Test
    void crawlTaskHoldsParentAndDepth() {
        CrawlTask t = new CrawlTask("https://example.com/a", "https://example.com", 1);
        assertEquals("https://example.com/a", t.getUrl());
        assertEquals("https://example.com", t.getParentUrl());
        assertEquals(1, t.getDepth());
    }

    @Test
    void hierarchyNodeChildrenDefaultEmpty() {
        HierarchyNode n = new HierarchyNode("https://example.com", CrawlStatus.COMPLETED, 0, 2);
        assertNotNull(n.getChildren());
        assertTrue(n.getChildren().isEmpty());
        assertFalse(n.isAlreadyVisited());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ModelFieldsTest test`
Expected: FAIL with "package com.crawler.model … CrawlTask/HierarchyNode not found" (compilation error).

- [ ] **Step 3: Write minimal implementation**

In `src/main/java/com/crawler/model/UrlResult.java` add fields (keep every existing constructor and method untouched):

```java
private String parentUrl;
private int depth;
private java.util.List<String> childUrls = new java.util.ArrayList<>();

public String getParentUrl() { return parentUrl; }
public void setParentUrl(String parentUrl) { this.parentUrl = parentUrl; }

public int getDepth() { return depth; }
public void setDepth(int depth) { this.depth = depth; }

public java.util.List<String> getChildUrls() { return childUrls; }
public void setChildUrls(java.util.List<String> childUrls) {
    this.childUrls = childUrls != null ? childUrls : new java.util.ArrayList<>();
}
```

Create `src/main/java/com/crawler/model/CrawlTask.java`:

```java
package com.crawler.model;

public class CrawlTask {
    private String url;
    private String parentUrl;
    private int depth;

    public CrawlTask() {}

    public CrawlTask(String url, String parentUrl, int depth) {
        this.url = url;
        this.parentUrl = parentUrl;
        this.depth = depth;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getParentUrl() { return parentUrl; }
    public void setParentUrl(String parentUrl) { this.parentUrl = parentUrl; }

    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }
}
```

Create `src/main/java/com/crawler/model/HierarchyNode.java`:

```java
package com.crawler.model;

import java.util.ArrayList;
import java.util.List;

public class HierarchyNode {
    private String url;
    private CrawlStatus status;
    private int depth;
    private int discoveredLinks;
    private boolean alreadyVisited;
    private List<HierarchyNode> children = new ArrayList<>();

    public HierarchyNode() {}

    public HierarchyNode(String url, CrawlStatus status, int depth, int discoveredLinks) {
        this.url = url;
        this.status = status;
        this.depth = depth;
        this.discoveredLinks = discoveredLinks;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public CrawlStatus getStatus() { return status; }
    public void setStatus(CrawlStatus status) { this.status = status; }

    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }

    public int getDiscoveredLinks() { return discoveredLinks; }
    public void setDiscoveredLinks(int discoveredLinks) { this.discoveredLinks = discoveredLinks; }

    public boolean isAlreadyVisited() { return alreadyVisited; }
    public void setAlreadyVisited(boolean alreadyVisited) { this.alreadyVisited = alreadyVisited; }

    public List<HierarchyNode> getChildren() { return children; }
    public void setChildren(List<HierarchyNode> children) {
        this.children = children != null ? children : new ArrayList<>();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=ModelFieldsTest test`
Expected: PASS (Tests run: 3, Failures: 0, Errors: 0).

- [ ] **Step 5: Commit**

```bash
git add web-crawler/src/main/java/com/crawler/model/UrlResult.java web-crawler/src/main/java/com/crawler/model/CrawlTask.java web-crawler/src/main/java/com/crawler/model/HierarchyNode.java web-crawler/src/test/java/com/crawler/model/ModelFieldsTest.java
git commit -m "feat(crawler): add parent/depth/children models for hierarchy"
```

---

### Task 2: Queue carries parent+depth; worker persists edges

**Files:**
- Modify: `src/main/java/com/crawler/service/QueueService.java`
- Modify: `src/main/java/com/crawler/service/CrawlService.java`
- Modify: `src/main/java/com/crawler/worker/CrawlWorker.java`
- Test: compile check + existing behaviour via `mvn -q -DskipTests package`

**Interfaces:**
- Consumes: `CrawlTask` from Task 1.
- Produces: `QueueService.addTask(CrawlTask)`, `QueueService.pollTask()` returning `CrawlTask`; worker saves `UrlResult` with parent/depth/childUrls populated.

- [ ] **Step 1: Write the failing test**

```java
package com.crawler.service;

import com.crawler.model.CrawlTask;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class QueueTaskTest {
    @Test
    void queueRoundTripsParentAndDepth() {
        QueueService q = new QueueService();
        q.clear();
        q.addTask(new CrawlTask("https://example.com/a", "https://example.com", 1));
        CrawlTask got = q.pollTask();
        assertNotNull(got);
        assertEquals("https://example.com/a", got.getUrl());
        assertEquals("https://example.com", got.getParentUrl());
        assertEquals(1, got.getDepth());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=QueueTaskTest test`
Expected: FAIL with "cannot find symbol: method addTask / pollTask".

- [ ] **Step 3: Write minimal implementation**

`QueueService.java`: replace the `ConcurrentLinkedQueue<String>` with `ConcurrentLinkedQueue<CrawlTask>` and replace `addUrl/pollUrl` bodies (keep method names as thin delegates so nothing else breaks):

```java
package com.crawler.service;

import com.crawler.model.CrawlTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final ConcurrentLinkedQueue<CrawlTask> queue = new ConcurrentLinkedQueue<>();

    public void addTask(CrawlTask task) {
        queue.add(task);
        log.debug("Enqueued URL: {} (queue size: {})", task.getUrl(), queue.size());
    }

    public CrawlTask pollTask() {
        return queue.poll();
    }

    public void addUrl(String url) {
        addTask(new CrawlTask(url, null, 0));
    }

    public String pollUrl() {
        CrawlTask task = pollTask();
        return task != null ? task.getUrl() : null;
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void clear() {
        queue.clear();
        log.info("URL queue cleared");
    }
}
```

`CrawlService.java` line ~64: replace `queueService.addUrl(seedUrl);` with:

```java
queueService.addTask(new com.crawler.model.CrawlTask(seedUrl, null, 0));
```

Add the import `import com.crawler.model.CrawlTask;` at the top of `CrawlService.java`.

`CrawlWorker.java`: replace the run-loop poll and `processUrl(String)` with task-aware versions. Exact edits:

1. In `run()`, replace:
```java
String url = queueService.pollUrl();
if (url == null) {
```
with:
```java
com.crawler.model.CrawlTask task = queueService.pollTask();
if (task == null) {
```
and replace `processUrl(url);` with `processUrl(task);`.
2. Replace the whole `processUrl(String url)` method with:

```java
private void processUrl(com.crawler.model.CrawlTask task) {
    String url = task.getUrl();
    log.info("Processing URL #{}: {}", pagesCrawled, url);

    UrlResult crawling = new UrlResult(url, CrawlStatus.CRAWLING, 0);
    crawling.setParentUrl(task.getParentUrl());
    crawling.setDepth(task.getDepth());
    crawlStateRepository.saveUrlResult(jobId, crawling);

    try {
        Document document = pageFetcherService.fetchPage(url);
        log.info("Fetched page: {} (title: {})", url, document.title());

        List<String> discoveredUrls = urlDiscoveryService.extractLinks(document, url);
        int linkCount = discoveredUrls.size();
        urlsDiscovered += linkCount;

        java.util.List<String> newChildren = new java.util.ArrayList<>();
        for (String discoveredUrl : discoveredUrls) {
            if (crawlStateRepository.markVisited(jobId, discoveredUrl)) {
                queueService.addTask(new com.crawler.model.CrawlTask(discoveredUrl, url, task.getDepth() + 1));
                newChildren.add(discoveredUrl);
                newUrlsCount(newChildren);
            }
        }

        UrlResult done = new UrlResult(url, CrawlStatus.COMPLETED, linkCount);
        done.setParentUrl(task.getParentUrl());
        done.setDepth(task.getDepth());
        done.setChildUrls(newChildren);
        crawlStateRepository.saveUrlResult(jobId, done);

        log.info("Completed URL: {} (links: {}, new: {})", url, linkCount, newChildren.size());

    } catch (Exception e) {
        failedCount++;
        String errorMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
        log.warn("FAILED URL #{}: {} — {}", failedCount, url, errorMsg);

        UrlResult failedResult = new UrlResult(url, CrawlStatus.FAILED, 0);
        failedResult.setParentUrl(task.getParentUrl());
        failedResult.setDepth(task.getDepth());
        failedResult.setError(errorMsg);
        crawlStateRepository.saveUrlResult(jobId, failedResult);
    }
}
```

3. Delete the now-unused helper `newUrlsCount` if your edit added a call to it — instead inline the counting: remove the line `newUrlsCount(newChildren);` and keep `newChildren.add(discoveredUrl);` only. (The snippet above must NOT contain the `newUrlsCount` line in the final file; it is shown only to flag the removal. Final loop body is exactly:)

```java
for (String discoveredUrl : discoveredUrls) {
    if (crawlStateRepository.markVisited(jobId, discoveredUrl)) {
        queueService.addTask(new com.crawler.model.CrawlTask(discoveredUrl, url, task.getDepth() + 1));
        newChildren.add(discoveredUrl);
    }
}
```

4. Add imports at top of `CrawlWorker.java`:
```java
import com.crawler.model.CrawlTask;
```
(`UrlResult`, `List`, `Document` imports already exist.)

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=QueueTaskTest test`
Expected: PASS. Then run: `mvn -q -DskipTests package`
Expected: BUILD SUCCESS (proves `CrawlService` + `CrawlWorker` still compile).

- [ ] **Step 5: Commit**

```bash
git add web-crawler/src/main/java/com/crawler/service/QueueService.java web-crawler/src/main/java/com/crawler/service/CrawlService.java web-crawler/src/main/java/com/crawler/worker/CrawlWorker.java web-crawler/src/test/java/com/crawler/service/QueueTaskTest.java
git commit -m "feat(crawler): track parent/depth edges during crawl"
```

---

### Task 3: Repository persists edges + lists/searches jobs

**Files:**
- Modify: `src/main/java/com/crawler/repository/CrawlStateRepository.java`
- Test: manual Redis verify in Task 6 (repository methods are thin Redis scans; unit-tested indirectly via Task 4 with mocks)

**Interfaces:**
- Consumes: `UrlResult` parent/depth/children from Task 1.
- Produces: `getAllJobs()` returning `List<CrawlJob>`; `findJobsBySeedContains(String query)` returning contains-matched jobs (case-insensitive); `saveUrlResult`/`getUrlResults` round-trip the 3 new fields.

- [ ] **Step 1: Write the failing check (compile-level)**

Run: `grep -n "findJobsBySeedContains\|getAllJobs" src/main/java/com/crawler/repository/CrawlStateRepository.java`
Expected: no output (methods do not exist yet).

- [ ] **Step 2: Implement repository changes**

1. In `saveUrlResult`, after the existing `fields.put("timestamp", ...)` lines and before `putAll`, add:

```java
fields.put("parentUrl", result.getParentUrl() != null ? result.getParentUrl() : "");
fields.put("depth", String.valueOf(result.getDepth()));
java.util.List<String> kids = result.getChildUrls() != null ? result.getChildUrls() : java.util.List.of();
fields.put("childUrls", String.join("\n", kids));
```

2. In `getUrlResults` mapping lambda, after `result.setDiscoveredLinks(...)` add:

```java
Object parentRaw = fields.get("parentUrl");
String parent = parentRaw != null ? parentRaw.toString() : "";
result.setParentUrl(parent.isEmpty() ? null : parent);
Object depthRaw = fields.get("depth");
int depth = 0;
try { depth = depthRaw != null ? Integer.parseInt(depthRaw.toString()) : 0; } catch (NumberFormatException ignored) {}
result.setDepth(depth);
Object kidsRaw = fields.get("childUrls");
java.util.List<String> kids = new java.util.ArrayList<>();
if (kidsRaw != null && !kidsRaw.toString().isEmpty()) {
    for (String k : kidsRaw.toString().split("\n", -1)) {
        if (!k.isEmpty()) kids.add(k);
    }
}
result.setChildUrls(kids);
```

3. Append these two methods at the end of the class (before the final closing brace, after `deleteJob`):

```java
public java.util.List<CrawlJob> getAllJobs() {
    Set<String> keys = redisTemplate.keys(JOB_PREFIX + "*");
    if (keys == null || keys.isEmpty()) return java.util.List.of();
    java.util.List<CrawlJob> jobs = new java.util.ArrayList<>();
    for (String key : keys) {
        String jobId = key.substring(JOB_PREFIX.length());
        CrawlJob job = getJob(jobId);
        if (job != null) jobs.add(job);
    }
    return jobs;
}

public java.util.List<CrawlJob> findJobsBySeedContains(String query) {
    if (query == null || query.isBlank()) return java.util.List.of();
    String q = query.toLowerCase();
    return getAllJobs().stream()
            .filter(j -> j.getSeedUrl() != null && j.getSeedUrl().toLowerCase().contains(q))
            .collect(java.util.stream.Collectors.toList());
}
```

- [ ] **Step 3: Run build to verify it compiles**

Run: `mvn -q -DskipTests package`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add web-crawler/src/main/java/com/crawler/repository/CrawlStateRepository.java
git commit -m "feat(crawler): persist hierarchy edges and search jobs by seed"
```

---

### Task 4: HierarchyService with unit tests (TDD core)

**Files:**
- Create: `src/main/java/com/crawler/service/HierarchyService.java`
- Test: `src/test/java/com/crawler/service/HierarchyServiceTest.java`

**Interfaces:**
- Consumes: `CrawlStateRepository.getJob(String)`, `getUrlResults(String)`, `findJobsBySeedContains(String)` from Task 3; `HierarchyNode`, `UrlResult`, `CrawlJob`, `CrawlStatus` from Task 1.
- Produces: `HierarchyNode buildTree(String jobId, Integer maxDepth)` throwing `java.util.NoSuchElementException` when job missing; `List<CrawlJob> searchBySeed(String query)` throwing `IllegalArgumentException` on blank query.

- [ ] **Step 1: Write the failing test**

```java
package com.crawler.service;

import com.crawler.model.CrawlJob;
import com.crawler.model.CrawlStatus;
import com.crawler.model.HierarchyNode;
import com.crawler.model.UrlResult;
import com.crawler.repository.CrawlStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class HierarchyServiceTest {

    @Mock
    CrawlStateRepository repo;

    @InjectMocks
    HierarchyService service;

    private static UrlResult r(String url, String parent, int depth, List<String> kids, CrawlStatus s) {
        UrlResult x = new UrlResult(url, s, kids.size());
        x.setParentUrl(parent);
        x.setDepth(depth);
        x.setChildUrls(kids);
        return x;
    }

    @Test
    void buildsNestedTreeWithDepths() {
        CrawlJob job = new CrawlJob("j1", "https://seed.test", 10);
        when(repo.getJob("j1")).thenReturn(job);
        when(repo.getUrlResults("j1")).thenReturn(List.of(
                r("https://seed.test", null, 0, List.of("https://seed.test/a"), CrawlStatus.COMPLETED),
                r("https://seed.test/a", "https://seed.test", 1, List.of(), CrawlStatus.COMPLETED)));

        HierarchyNode root = service.buildTree("j1", null);

        assertEquals("https://seed.test", root.getUrl());
        assertEquals(1, root.getChildren().size());
        assertEquals("https://seed.test/a", root.getChildren().get(0).getUrl());
        assertEquals(1, root.getChildren().get(0).getDepth());
    }

    @Test
    void cycleBecomesAlreadyVisitedLeaf() {
        CrawlJob job = new CrawlJob("j1", "https://s.test", 10);
        when(repo.getJob("j1")).thenReturn(job);
        when(repo.getUrlResults("j1")).thenReturn(List.of(
                r("https://s.test", null, 0, List.of("https://s.test/a"), CrawlStatus.COMPLETED),
                r("https://s.test/a", "https://s.test", 1, List.of("https://s.test"), CrawlStatus.COMPLETED),
                r("https://s.test", null, 0, List.of("https://s.test/a"), CrawlStatus.COMPLETED)));

        HierarchyNode root = service.buildTree("j1", null);

        HierarchyNode a = root.getChildren().get(0);
        assertEquals(1, a.getChildren().size());
        assertTrue(a.getChildren().get(0).isAlreadyVisited());
        assertTrue(a.getChildren().get(0).getChildren().isEmpty());
    }

    @Test
    void maxDepthPrunesChildren() {
        CrawlJob job = new CrawlJob("j1", "https://s.test", 10);
        when(repo.getJob("j1")).thenReturn(job);
        when(repo.getUrlResults("j1")).thenReturn(List.of(
                r("https://s.test", null, 0, List.of("https://s.test/a"), CrawlStatus.COMPLETED),
                r("https://s.test/a", "https://s.test", 1, List.of("https://s.test/b"), CrawlStatus.COMPLETED),
                r("https://s.test/b", "https://s.test/a", 2, List.of(), CrawlStatus.COMPLETED)));

        HierarchyNode root = service.buildTree("j1", 1);

        assertEquals(1, root.getChildren().size());
        assertTrue(root.getChildren().get(0).getChildren().isEmpty());
    }

    @Test
    void oldJobsWithoutParentsAttachToRoot() {
        CrawlJob job = new CrawlJob("j1", "https://s.test", 10);
        when(repo.getJob("j1")).thenReturn(job);
        UrlResult orphan = new UrlResult("https://s.test/z", CrawlStatus.COMPLETED, 0);
        UrlResult seed = new UrlResult("https://s.test", CrawlStatus.COMPLETED, 0);
        when(repo.getUrlResults("j1")).thenReturn(List.of(seed, orphan));

        HierarchyNode root = service.buildTree("j1", null);

        assertEquals(1, root.getChildren().size());
        assertEquals("https://s.test/z", root.getChildren().get(0).getUrl());
    }

    @Test
    void blankSearchQueryRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.searchBySeed("  "));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=HierarchyServiceTest test`
Expected: FAIL with "HierarchyService not found" (compilation error).

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/crawler/service/HierarchyService.java` with this exact content:

```java
package com.crawler.service;

import com.crawler.model.CrawlJob;
import com.crawler.model.CrawlStatus;
import com.crawler.model.HierarchyNode;
import com.crawler.model.UrlResult;
import com.crawler.repository.CrawlStateRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class HierarchyService {

    private final CrawlStateRepository repository;

    public HierarchyService(CrawlStateRepository repository) {
        this.repository = repository;
    }

    public HierarchyNode buildTree(String jobId, Integer maxDepth) {
        CrawlJob job = repository.getJob(jobId);
        if (job == null) throw new NoSuchElementException("Job not found: " + jobId);
        List<UrlResult> results = repository.getUrlResults(jobId);
        int limit = maxDepth != null ? maxDepth : Integer.MAX_VALUE;
        return buildTreeFrom(job.getSeedUrl(), job.getStatus(), results, limit);
    }

    public List<CrawlJob> searchBySeed(String query) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("seedUrl query must not be blank");
        return repository.findJobsBySeedContains(query.trim());
    }

    static HierarchyNode buildTreeFrom(String seedUrl, CrawlStatus rootStatus, List<UrlResult> results, int maxDepth) {
        Map<String, UrlResult> byUrl = new HashMap<>();
        for (UrlResult r : results) {
            if (r.getUrl() != null) byUrl.putIfAbsent(r.getUrl(), r);
        }
        UrlResult seedResult = byUrl.get(seedUrl);
        CrawlStatus status = seedResult != null ? seedResult.getStatus() : rootStatus;
        int links = seedResult != null ? seedResult.getDiscoveredLinks() : 0;
        HierarchyNode root = new HierarchyNode(seedUrl, status, 0, links);
        Set<String> visited = new HashSet<>();
        visited.add(seedUrl);
        expand(root, byUrl, visited, maxDepth);
        return root;
    }

    private static void expand(HierarchyNode node, Map<String, UrlResult> byUrl, Set<String> visited, int maxDepth) {
        if (node.getDepth() >= maxDepth) return;
        UrlResult self = byUrl.get(node.getUrl());
        List<String> kids = self != null && self.getChildUrls() != null ? self.getChildUrls() : List.of();
        if (kids.isEmpty() && node.getDepth() == 0) {
            for (UrlResult r : byUrl.values()) {
                boolean isSeed = r.getUrl() != null && r.getUrl().equals(node.getUrl());
                boolean noParent = r.getParentUrl() == null || r.getParentUrl().isEmpty();
                if (!isSeed && noParent) kids = append(kids, r.getUrl());
            }
        }
        for (String childUrl : kids) {
            if (childUrl == null || childUrl.isEmpty()) continue;
            if (!visited.add(childUrl)) {
                HierarchyNode leaf = nodeFor(childUrl, byUrl, node.getDepth() + 1);
                leaf.setAlreadyVisited(true);
                node.getChildren().add(leaf);
                continue;
            }
            HierarchyNode child = nodeFor(childUrl, byUrl, node.getDepth() + 1);
            node.getChildren().add(child);
            expand(child, byUrl, visited, maxDepth);
        }
    }

    private static HierarchyNode nodeFor(String url, Map<String, UrlResult> byUrl, int depth) {
        UrlResult r = byUrl.get(url);
        if (r == null) return new HierarchyNode(url, CrawlStatus.DISCOVERED, depth, 0);
        return new HierarchyNode(url, r.getStatus(), depth, r.getDiscoveredLinks());
    }

    private static List<String> append(List<String> list, String value) {
        List<String> out = new ArrayList<>(list);
        out.add(value);
        return out;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=HierarchyServiceTest test`
Expected: PASS (Tests run: 5, Failures: 0, Errors: 0).

- [ ] **Step 5: Commit**

```bash
git add web-crawler/src/main/java/com/crawler/service/HierarchyService.java web-crawler/src/test/java/com/crawler/service/HierarchyServiceTest.java
git commit -m "feat(crawler): hierarchy tree service with cycle guard"
```

---

### Task 5: Controller endpoints for hierarchy + seed search

**Files:**
- Modify: `src/main/java/com/crawler/controller/CrawlController.java`
- Test: `src/test/java/com/crawler/controller/CrawlControllerTest.java`

**Interfaces:**
- Consumes: `HierarchyService.buildTree(String, Integer)` and `searchBySeed(String)` from Task 4; existing `CrawlService` unchanged.
- Produces: `GET /api/crawl/search?seedUrl={q}` → 200 list / 400 on blank; `GET /api/crawl/{jobId}/hierarchy?maxDepth={n}` → 200 `{jobId,seedUrl,status,tree}` / 404 on unknown job. Exact `/search` route wins over `/{jobId}` because Spring prefers exact matches.

- [ ] **Step 1: Write the failing test**

```java
package com.crawler.controller;

import com.crawler.model.CrawlJob;
import com.crawler.model.CrawlStatus;
import com.crawler.model.HierarchyNode;
import com.crawler.repository.CrawlStateRepository;
import com.crawler.service.CrawlService;
import com.crawler.service.HierarchyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CrawlController.class)
public class CrawlControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    CrawlService crawlService;

    @MockBean
    CrawlStateRepository crawlStateRepository;

    @MockBean
    HierarchyService hierarchyService;

    @Test
    void hierarchyReturnsTree() throws Exception {
        HierarchyNode root = new HierarchyNode("https://s.test", CrawlStatus.COMPLETED, 0, 1);
        root.getChildren().add(new HierarchyNode("https://s.test/a", CrawlStatus.COMPLETED, 1, 0));
        when(hierarchyService.buildTree(eq("j1"), any())).thenReturn(root);

        mvc.perform(get("/api/crawl/j1/hierarchy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("j1"))
                .andExpect(jsonPath("$.tree.url").value("https://s.test"))
                .andExpect(jsonPath("$.tree.children[0].url").value("https://s.test/a"));
    }

    @Test
    void hierarchyUnknownJobIs404() throws Exception {
        when(hierarchyService.buildTree(eq("nope"), any())).thenThrow(new NoSuchElementException("gone"));

        mvc.perform(get("/api/crawl/nope/hierarchy"))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchReturnsMatches() throws Exception {
        CrawlJob job = new CrawlJob("j1", "https://example.com", 10);
        job.setStatus(CrawlStatus.COMPLETED);
        when(hierarchyService.searchBySeed("example")).thenReturn(List.of(job));

        mvc.perform(get("/api/crawl/search").param("seedUrl", "example"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobId").value("j1"));
    }

    @Test
    void searchBlankIs400() throws Exception {
        when(hierarchyService.searchBySeed(" ")).thenThrow(new IllegalArgumentException("blank"));

        mvc.perform(get("/api/crawl/search").param("seedUrl", " "))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=CrawlControllerTest test`
Expected: FAIL with compilation errors ("cannot find symbol: HierarchyService", "no suitable constructor" on `CrawlController`).

- [ ] **Step 3: Write minimal implementation**

In `src/main/java/com/crawler/controller/CrawlController.java` make exactly these edits:

1. Add imports:
```java
import com.crawler.service.HierarchyService;
import java.util.NoSuchElementException;
```
2. Add field + constructor param. Replace:
```java
private final CrawlService crawlService;
private final CrawlStateRepository crawlStateRepository;

public CrawlController(CrawlService crawlService, CrawlStateRepository crawlStateRepository) {
    this.crawlService = crawlService;
    this.crawlStateRepository = crawlStateRepository;
}
```
with:
```java
private final CrawlService crawlService;
private final CrawlStateRepository crawlStateRepository;
private final HierarchyService hierarchyService;

public CrawlController(CrawlService crawlService, CrawlStateRepository crawlStateRepository, HierarchyService hierarchyService) {
    this.crawlService = crawlService;
    this.crawlStateRepository = crawlStateRepository;
    this.hierarchyService = hierarchyService;
}
```
3. Append these two handler methods inside the class (after `stopJob`, before final brace):
```java
@GetMapping("/search")
public ResponseEntity<?> searchBySeed(@RequestParam String seedUrl) {
    try {
        return ResponseEntity.ok(hierarchyService.searchBySeed(seedUrl));
    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}

@GetMapping("/{jobId}/hierarchy")
public ResponseEntity<?> getHierarchy(@PathVariable String jobId,
                                      @RequestParam(required = false) Integer maxDepth) {
    try {
        com.crawler.model.HierarchyNode tree = hierarchyService.buildTree(jobId, maxDepth);
        CrawlJob job = crawlService.getJobStatus(jobId);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("jobId", jobId);
        body.put("seedUrl", job != null ? job.getSeedUrl() : null);
        body.put("status", job != null ? job.getStatus().name() : null);
        body.put("tree", tree);
        return ResponseEntity.ok(body);
    } catch (NoSuchElementException e) {
        return ResponseEntity.notFound().build();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=CrawlControllerTest test`
Expected: PASS (Tests run: 4, Failures: 0, Errors: 0). Then run full unit suite: `mvn -q test`
Expected: all green.

- [ ] **Step 5: Commit**

```bash
git add web-crawler/src/main/java/com/crawler/controller/CrawlController.java web-crawler/src/test/java/com/crawler/controller/CrawlControllerTest.java
git commit -m "feat(crawler): hierarchy and seed-search APIs"
```

---

### Task 6: Separate hierarchy page + live verify on :8900

**Files:**
- Create: `src/main/resources/static/hierarchy.html`
- Test: live `curl` checks against `http://localhost:8900` + browser open of `/hierarchy.html`

**Interfaces:**
- Consumes: `GET /api/crawl/{jobId}/hierarchy`, `GET /api/crawl/search?seedUrl=`, `GET /api/crawl/{jobId}` from Task 5.
- Produces: browsable `/hierarchy.html` with jobId/seed search, collapsible status-colored tree, legend, expand/collapse-all.

- [ ] **Step 1: Create the page**

Create `src/main/resources/static/hierarchy.html` with this exact content:

```html
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Crawl Hierarchy</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'Segoe UI',system-ui,sans-serif;background:#0f0f23;color:#e0e0e0;min-height:100vh}
.container{max-width:1000px;margin:0 auto;padding:32px 20px}
a{color:#93c5fd}
header{display:flex;align-items:baseline;gap:16px;margin-bottom:20px}
header h1{font-size:1.6rem;background:linear-gradient(135deg,#60a5fa,#a78bfa);-webkit-background-clip:text;-webkit-text-fill-color:transparent}
.card{background:rgba(30,30,60,.6);border:1px solid rgba(59,130,246,.15);border-radius:12px;padding:20px;margin-bottom:20px}
.row{display:flex;gap:10px;flex-wrap:wrap}
input{flex:1;min-width:180px;padding:9px 12px;background:rgba(15,15,35,.8);border:1px solid rgba(59,130,246,.2);border-radius:8px;color:#e0e0e0}
button{padding:9px 18px;border:none;border-radius:8px;font-weight:600;cursor:pointer;background:linear-gradient(135deg,#3b82f6,#8b5cf6);color:#fff}
button.ghost{background:rgba(59,130,246,.15);color:#bfdbfe}
.legend{display:flex;gap:8px;flex-wrap:wrap;margin-top:12px;font-size:.8rem;color:#94a3b8}
.badge{display:inline-block;padding:2px 10px;border-radius:20px;font-size:.72rem;font-weight:700;text-transform:uppercase}
.s-QUEUED{background:rgba(251,191,36,.15);color:#fbbf24}.s-CRAWLING{background:rgba(59,130,246,.15);color:#60a5fa}.s-COMPLETED{background:rgba(34,197,94,.15);color:#4ade80}.s-FAILED{background:rgba(239,68,68,.15);color:#f87171}.s-DISCOVERED{background:rgba(148,163,184,.15);color:#cbd5e1}
ul.tree{list-style:none;margin-top:12px}
ul.tree ul{margin-left:22px;border-left:1px solid rgba(59,130,246,.25);padding-left:14px}
.node{margin:6px 0}
.toggle{cursor:pointer;user-select:none;margin-right:6px;color:#93c5fd;font-weight:700}
.leaf{display:inline-block;width:14px;margin-right:6px;color:#475569}
.url{word-break:break-all}
.meta{font-size:.75rem;color:#94a3b8;margin-left:8px}
.visited{font-size:.72rem;color:#fbbf24;border:1px solid rgba(251,191,36,.4);border-radius:10px;padding:0 8px;margin-left:8px}
#error{color:#f87171;margin-top:8px;min-height:1.2em}
.results{margin-top:10px;font-size:.85rem}
.results div{padding:6px 8px;border-bottom:1px solid rgba(59,130,246,.12);cursor:pointer}
.results div:hover{background:rgba(59,130,246,.08)}
</style>
</head>
<body>
<div class="container">
<header><h1>Crawl Hierarchy</h1><a href="/">← back to crawler</a></header>
<div class="card">
<div class="row">
<input id="jobId" placeholder="jobId (e.g. d4fbe0b3-…)">
<input id="seed" placeholder="or search seedUrl contains… (e.g. example)">
<input id="depth" type="number" min="1" max="20" placeholder="maxDepth" style="flex:0 0 110px">
<button onclick="loadById()">Load tree</button>
<button class="ghost" onclick="searchSeed()">Search</button>
</div>
<div class="row" style="margin-top:10px">
<button class="ghost" onclick="expandAll(true)">Expand all</button>
<button class="ghost" onclick="expandAll(false)">Collapse all</button>
<span id="counts" style="align-self:center;color:#94a3b8;font-size:.85rem"></span>
</div>
<div class="legend"><span><span class="badge s-COMPLETED">completed</span> done</span><span><span class="badge s-CRAWLING">crawling</span> active</span><span><span class="badge s-FAILED">failed</span> error</span><span><span class="badge s-QUEUED">queued</span> waiting</span><span class="visited">already visited</span> cycle ref (not expanded)</div>
<div id="error"></div>
<div id="searchResults" class="results"></div>
</div>
<div class="card"><div id="treeWrap"><p style="color:#64748b">Enter a jobId or search by seed URL to visualise the discovery tree.</p></div></div>
</div>
<script>
let total=0;
async function loadById(){
  const id=document.getElementById('jobId').value.trim();
  if(!id){showError('Enter a jobId first');return;}
  const d=document.getElementById('depth').value.trim();
  await loadTree(id,d?`?maxDepth=${encodeURIComponent(d)}`:'');
}
async function searchSeed(){
  const q=document.getElementById('seed').value.trim();
  if(!q){showError('Enter seed text to search');return;}
  showError('');
  const res=await fetch(`/api/crawl/search?seedUrl=${encodeURIComponent(q)}`);
  if(!res.ok){showError('Search failed: '+res.status);return;}
  const jobs=await res.json();
  const box=document.getElementById('searchResults');
  if(!jobs.length){box.innerHTML='<p style="color:#64748b">No jobs match "'+escapeHtml(q)+'"</p>';return;}
  box.innerHTML=jobs.map(j=>`<div data-id="${j.jobId}"><span class="badge s-${j.status}">${j.status}</span> <b>${escapeHtml(j.seedUrl||'')}</b> <span class="meta">${j.pagesCrawled}/${j.maxPages} pages · ${j.jobId}</span></div>`).join('');
  box.querySelectorAll('div[data-id]').forEach(el=>el.onclick=()=>{document.getElementById('jobId').value=el.dataset.id;loadTree(el.dataset.id,'');});
}
async function loadTree(id,qs){
  showError('');document.getElementById('searchResults').innerHTML='';
  const res=await fetch(`/api/crawl/${id}/hierarchy${qs}`);
  if(res.status===404){showError('Job not found: '+id);return;}
  if(!res.ok){showError('Failed to load hierarchy: '+res.status);return;}
  const data=await res.json();
  total=0;countNodes(data.tree);
  document.getElementById('counts').textContent=`${total} nodes · seed ${data.seedUrl||''} · ${data.status||''}`;
  document.getElementById('treeWrap').innerHTML=renderNode(data.tree,true);
  bindToggles();
}
function countNodes(n){if(!n)return;total++;(n.children||[]).forEach(countNodes);}
function renderNode(n,isRoot){
  const kids=n.children||[];
  const has=kids.length>0;
  const caret=has?`<span class="toggle" data-open="true">▾</span>`:`<span class="leaf">•</span>`;
  const visited=n.alreadyVisited?`<span class="visited">already visited</span>`:'';
  let h=`<li class="node">${caret}<span class="badge s-${n.status}">${n.status}</span> <span class="url">${escapeHtml(n.url)}</span><span class="meta">d${n.depth} · ${n.discoveredLinks} links</span>${visited}`;
  if(has){h+=`<ul>${kids.map(k=>renderNode(k,false)).join('')}</ul>`;}
  return h+'</li>';
}
function bindToggles(){
  document.querySelectorAll('.toggle').forEach(t=>{
    t.onclick=()=>{
      const open=t.dataset.open==='true';
      t.dataset.open=open?'false':'true';
      t.textContent=open?'▸':'▾';
      const ul=t.parentElement.querySelector(':scope > ul');
      if(ul)ul.style.display=open?'none':'';
    };
  });
}
function expandAll(open){
  document.querySelectorAll('.toggle').forEach(t=>{
    t.dataset.open=open?'true':'false';t.textContent=open?'▾':'▸';
    const ul=t.parentElement.querySelector(':scope > ul');
    if(ul)ul.style.display=open?'':'none';
  });
}
function showError(m){document.getElementById('error').textContent=m;}
function escapeHtml(s){return (s||'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
</script>
</body>
</html>
```

- [ ] **Step 2: Rebuild and restart on :8900**

Run: `mvn -q -DskipTests package`
Expected: BUILD SUCCESS.

Run: `pkill -f "web-crawler-1.0.0.jar.*8900" ; sleep 2; nohup java -jar target/web-crawler-1.0.0.jar --server.port=8900 > /tmp/webcrawler-8900.log 2>&1 & echo STARTED:$!`
Expected: `STARTED:<pid>`. Then run: `sleep 8; ss -tlnp | grep 8900`
Expected: `LISTEN … *:8900`.

- [ ] **Step 3: Live-verify hierarchy + search APIs**

Run: `curl -s http://localhost:8900/hierarchy.html -o /dev/null -w "hierarchy page: %{http_code}\n"`
Expected: `hierarchy page: 200`.

Run: `JOB=$(curl -s -X POST http://localhost:8900/api/crawl -H "Content-Type: application/json" -d '{"url":"https://example.com","maxPages":3}' | python3 -c "import sys,json;print(json.load(sys.stdin)['jobId'])"); echo $JOB; sleep 6; curl -s http://localhost:8900/api/crawl/$JOB/hierarchy | head -c 600; echo; curl -s "http://localhost:8900/api/crawl/search?seedUrl=example" | head -c 400`
Expected: hierarchy JSON contains `"tree"` with nested `children`; search JSON contains the new `jobId`.

- [ ] **Step 4: Commit**

```bash
git add web-crawler/src/main/resources/static/hierarchy.html
git commit -m "feat(crawler): separate hierarchy visualisation page"
```

---

## Self-Review

- Spec coverage: true discovery tree (§1–2) → Tasks 1–4+6; hierarchy API with maxDepth + cycle guard → Tasks 4–5; seed contains-search returning all → Tasks 3–5; separate understandable page (legend, collapse, cycle leaves) → Task 6; old-job fallback → Task 4 test; 404/400 → Task 5 tests.
- Placeholder scan: no TBD/TODO; every code block is complete file/method content; run commands include exact paths and expected outputs.
- Type consistency: `CrawlTask(url,parentUrl,depth)` used identically in Tasks 1/2; `HierarchyNode(url,status,depth,discoveredLinks)` + `alreadyVisited` + `children` identical in Tasks 1/4/5/6; repository method names `getAllJobs`/`findJobsBySeedContains` identical in Tasks 3/4; endpoint paths `/api/crawl/search` and `/api/crawl/{jobId}/hierarchy` identical in Tasks 5/6.
