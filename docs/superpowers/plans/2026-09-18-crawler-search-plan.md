# Page Indexing, Ranking & Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Index crawled page text into a Redis inverted index during crawling, rank hits with TF-IDF, and expose search via API + page.

**Architecture:** Pure-static `TextTokenizer` feeds term frequencies; `CrawlStateRepository.indexPage` persists ZSET postings + doc HASH + counters with atomic Redis ops (lock-free under Phase 1 workers); `RankService` combines postings into TF-IDF scores at query time; new `SearchController` + `search.html` serve results.

**Tech Stack:** Java 17, Spring Boot 3.2.5, Spring Data Redis ZSET/HSET/SET ops, Jsoup body text (already fetched), vanilla JS, JUnit5 + Mockito.

**Spec:** `docs/superpowers/specs/2026-09-18-crawler-search-design.md`

## Global Constraints

- Java version floor: 17 (no Java 21 APIs).
- Spring Boot version: 3.2.5 (do not upgrade).
- Existing Redis keys untouched; new keys only: `index:term:{token}` (ZSET url→tf), `index:doc:{urlHash}` (HASH), `index:docs` (SET), `index:meta` (HASH totalDocs).
- Tokenizer rules: lowercase, split `[^a-z0-9]+`, min length 3, spec stop-word list, cap 10,000 tokens/page.
- Ranking: TF-IDF `score(d) = Σ tf(t,d)/len(d) × ln(N/df(t))`, desc, URL tie-break; all-zero-idf falls back to tf ordering.
- Search API: `GET /api/search?q=...&limit=10`, limit default 10 max 50, 400 on blank q.
- Requires Phase 1 multithreading plan executed first (worker success path is the indexing hook point).
- No new Maven dependencies.
- Manual verification base URL: `http://localhost:8900`.
- All paths below are relative to `web-crawler/`.

---

## File Structure

- Create: `src/main/java/com/crawler/service/TextTokenizer.java` — pure static tokenizer → `Map<String,Integer>`.
- Modify: `src/main/java/com/crawler/repository/CrawlStateRepository.java` — add `indexPage` + `getTermScores/getTermDocCount/getTotalDocs/getDoc` (append methods only, touch nothing existing).
- Create: `src/main/java/com/crawler/service/RankService.java` — `search(query,limit)` orchestration + static `combineScores` pure function.
- Create: `src/main/java/com/crawler/model/SearchHit.java` — DTO `{url,title,snippet,score}`.
- Create: `src/main/java/com/crawler/controller/SearchController.java` — `GET /api/search`.
- Modify: `src/main/java/com/crawler/worker/CrawlWorker.java` — indexing hook in success path (never breaks crawling: wrapped in try/catch).
- Create: `src/main/resources/static/search.html` — search page.
- Modify: `src/main/resources/static/index.html` — add "Search Pages" button next to "View Hierarchy".
- Tests: `src/test/java/com/crawler/service/TokenizerTest.java`, `src/test/java/com/crawler/service/RankServiceTest.java`, `src/test/java/com/crawler/controller/SearchControllerTest.java`, extend worker test in Task 5 step (new `WorkerIndexTest`).

Task chain: Task 1 (tokenizer) → Task 2 (repository) → Task 3 (rank) → Task 4 (API) → Task 5 (hook + UI + live verify).

---

### Task 1: TextTokenizer + unit tests

**Files:**
- Create: `src/main/java/com/crawler/service/TextTokenizer.java`
- Test: `src/test/java/com/crawler/service/TokenizerTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `TextTokenizer.tokenize(String)` returning `Map<String,Integer>`; constants `MIN_TOKEN_LENGTH=3`, `MAX_TOKENS_PER_PAGE=10000`, `STOP_WORDS` set.

- [ ] **Step 1: Write the failing test**

```java
package com.crawler.service;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class TokenizerTest {

    @Test
    void lowercasesAndCountsFrequencies() {
        Map<String, Integer> tf = TextTokenizer.tokenize("Hello, World! Hello");
        assertEquals(Map.of("hello", 2, "world", 1), tf);
    }

    @Test
    void dropsStopWordsAndShortTokens() {
        Map<String, Integer> tf = TextTokenizer.tokenize("The cat is on the mat a");
        assertEquals(Map.of("cat", 1, "mat", 1), tf);
    }

    @Test
    void nullAndBlankYieldEmpty() {
        assertTrue(TextTokenizer.tokenize(null).isEmpty());
        assertTrue(TextTokenizer.tokenize("   ").isEmpty());
    }

    @Test
    void capsTokensPerPage() {
        String big = "wordx ".repeat(12005);
        Map<String, Integer> tf = TextTokenizer.tokenize(big);
        int total = tf.values().stream().mapToInt(Integer::intValue).sum();
        assertTrue(total <= 10000, "total=" + total);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=TokenizerTest test`
Expected: FAIL with "TextTokenizer not found" (compilation error).

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/crawler/service/TextTokenizer.java` with this exact content:

```java
package com.crawler.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TextTokenizer {

    public static final int MIN_TOKEN_LENGTH = 3;
    public static final int MAX_TOKENS_PER_PAGE = 10000;

    public static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "of", "to", "in", "on", "for",
            "with", "is", "are", "was", "were", "be", "been", "by", "as", "at",
            "from", "that", "this", "it", "its", "into", "over", "after", "before",
            "between", "through", "during", "such", "other", "than", "then", "there",
            "their", "what", "which", "when", "where", "while", "about", "also",
            "just", "like", "more", "most", "only", "own", "same", "still", "even");

    public static Map<String, Integer> tokenize(String text) {
        Map<String, Integer> freq = new HashMap<>();
        if (text == null || text.isBlank()) return freq;
        String[] parts = text.toLowerCase().split("[^a-z0-9]+");
        int total = 0;
        for (String p : parts) {
            if (total >= MAX_TOKENS_PER_PAGE) break;
            if (p.length() < MIN_TOKEN_LENGTH) continue;
            if (STOP_WORDS.contains(p)) continue;
            freq.merge(p, 1, Integer::sum);
            total++;
        }
        return freq;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=TokenizerTest test`
Expected: PASS (Tests run: 4, Failures: 0, Errors: 0).

- [ ] **Step 5: Commit**

```bash
git add web-crawler/src/main/java/com/crawler/service/TextTokenizer.java web-crawler/src/test/java/com/crawler/service/TokenizerTest.java
git commit -m "feat(search): text tokenizer with stop words and cap"
```

---

### Task 2: Repository inverted-index writes + reads

**Files:**
- Modify: `src/main/java/com/crawler/repository/CrawlStateRepository.java` (append 5 methods + 4 key constants; touch nothing existing)
- Test: build check `mvn -q -DskipTests package` (thin Redis code; live-covered in Task 5, same pattern as hierarchy plan Task 3)

**Interfaces:**
- Consumes: `generateUrlKey(String)` private helper (exists — reuse, do not duplicate).
- Produces: `indexPage(String url, String jobId, String title, String snippet, int length, Map<String,Integer> tf)` (no-op on null/blank url or empty tf); `getTermScores(String token)` returning `Map<String,Double>` url→tf; `getTermDocCount(String token)` long; `getTotalDocs()` long (0 default, parse-safe); `getDoc(String url)` returning `Map<String,String>` (empty when missing).

- [ ] **Step 1: Write the failing check (compile-level)**

Run: `grep -n "indexPage\|getTermScores" src/main/java/com/crawler/repository/CrawlStateRepository.java`
Expected: no output (methods do not exist yet).

- [ ] **Step 2: Implement repository changes**

1. Add these constants next to the existing `VISITED_PREFIX/JOB_PREFIX/RESULT_PREFIX` declarations:

```java
private static final String TERM_PREFIX = "index:term:";
private static final String DOC_PREFIX = "index:doc:";
private static final String DOC_SET = "index:docs";
private static final String META_KEY = "index:meta";
```

2. Append these five methods at the end of the class (after `findJobsBySeedContains`, before `deleteJob` or after it — anywhere inside the class body, keeping `generateUrlKey` last):

```java
public void indexPage(String url, String jobId, String title, String snippet, int length,
                      java.util.Map<String, Integer> tf) {
    if (url == null || url.isBlank() || tf == null || tf.isEmpty()) return;
    for (java.util.Map.Entry<String, Integer> e : tf.entrySet()) {
        redisTemplate.opsForZSet().add(TERM_PREFIX + e.getKey(), url, e.getValue().doubleValue());
    }
    String docKey = DOC_PREFIX + generateUrlKey(url);
    java.util.Map<String, String> doc = new java.util.HashMap<>();
    doc.put("url", url);
    doc.put("title", title != null ? title : "");
    doc.put("snippet", snippet != null ? snippet : "");
    doc.put("length", String.valueOf(length));
    doc.put("jobId", jobId != null ? jobId : "");
    redisTemplate.opsForHash().putAll(docKey, doc);
    Long added = redisTemplate.opsForSet().add(DOC_SET, url);
    if (added != null && added > 0) {
        redisTemplate.opsForHash().increment(META_KEY, "totalDocs", 1);
    }
}

public java.util.Map<String, Double> getTermScores(String token) {
    java.util.Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<Object>> tuples =
            redisTemplate.opsForZSet().rangeWithScores(TERM_PREFIX + token, 0, -1);
    java.util.Map<String, Double> out = new java.util.HashMap<>();
    if (tuples == null) return out;
    for (org.springframework.data.redis.core.ZSetOperations.TypedTuple<Object> t : tuples) {
        if (t.getValue() != null && t.getScore() != null) out.put(t.getValue().toString(), t.getScore());
    }
    return out;
}

public long getTermDocCount(String token) {
    Long n = redisTemplate.opsForZSet().zCard(TERM_PREFIX + token);
    return n != null ? n : 0;
}

public long getTotalDocs() {
    Object v = redisTemplate.opsForHash().get(META_KEY, "totalDocs");
    if (v == null) return 0;
    try {
        return Long.parseLong(v.toString());
    } catch (NumberFormatException e) {
        return 0;
    }
}

public java.util.Map<String, String> getDoc(String url) {
    java.util.Map<Object, Object> raw = redisTemplate.opsForHash().entries(DOC_PREFIX + generateUrlKey(url));
    java.util.Map<String, String> out = new java.util.HashMap<>();
    for (java.util.Map.Entry<Object, Object> e : raw.entrySet()) {
        out.put(e.getKey().toString(), e.getValue() != null ? e.getValue().toString() : "");
    }
    return out;
}
```

- [ ] **Step 3: Run build to verify it compiles**

Run: `mvn -q -DskipTests package`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add web-crawler/src/main/java/com/crawler/repository/CrawlStateRepository.java
git commit -m "feat(search): redis inverted index writes and reads"
```

---

### Task 3: RankService TF-IDF + unit tests

**Files:**
- Create: `src/main/java/com/crawler/service/RankService.java`
- Test: `src/test/java/com/crawler/service/RankServiceTest.java`

**Interfaces:**
- Consumes: `TextTokenizer.tokenize` from Task 1; repository `getTermScores/getTermDocCount/getTotalDocs/getDoc` from Task 2; `SearchHit` DTO defined in this task's Step 3 (model file below — create it in this task before RankService compiles).
- Produces: `search(String query, Integer limit)` returning `List<SearchHit>` (throws IllegalArgumentException on blank); static `combineScores(postings, docLen, totalDocs, df, limit, repository)` pure ranking core.

- [ ] **Step 1: Write the failing test**

```java
package com.crawler.service;

import com.crawler.model.SearchHit;
import com.crawler.repository.CrawlStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RankServiceTest {

    @Mock
    CrawlStateRepository repo;

    @InjectMocks
    RankService service;

    @Test
    void higherTfRanksFirstWhenIdfZero() {
        when(repo.getTotalDocs()).thenReturn(2L);
        when(repo.getTermScores("example")).thenReturn(Map.of(
                "https://a.test", 3.0, "https://b.test", 1.0));
        when(repo.getTermDocCount("example")).thenReturn(2L);
        when(repo.getDoc("https://a.test")).thenReturn(Map.of("title", "A", "snippet", "s", "length", "10"));
        when(repo.getDoc("https://b.test")).thenReturn(Map.of("title", "B", "snippet", "s", "length", "10"));

        List<SearchHit> hits = service.search("example", 10);

        assertEquals(2, hits.size());
        assertEquals("https://a.test", hits.get(0).getUrl());
    }

    @Test
    void rareTermOutranksCommonTerm() {
        when(repo.getTotalDocs()).thenReturn(10L);
        when(repo.getTermScores("common")).thenReturn(Map.of("https://a.test", 5.0));
        when(repo.getTermScores("rare")).thenReturn(Map.of("https://b.test", 1.0));
        when(repo.getTermDocCount("common")).thenReturn(9L);
        when(repo.getTermDocCount("rare")).thenReturn(1L);
        when(repo.getDoc("https://a.test")).thenReturn(Map.of("title", "A", "snippet", "s", "length", "10"));
        when(repo.getDoc("https://b.test")).thenReturn(Map.of("title", "B", "snippet", "s", "length", "10"));

        List<SearchHit> hits = service.search("common rare", 10);

        assertEquals("https://b.test", hits.get(0).getUrl());
    }

    @Test
    void unknownTermYieldsEmpty() {
        when(repo.getTotalDocs()).thenReturn(5L);
        when(repo.getTermScores("xyzzy")).thenReturn(Map.of());

        List<SearchHit> hits = service.search("xyzzy", 10);

        assertTrue(hits.isEmpty());
    }

    @Test
    void blankQueryRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.search("  ", 10));
    }
}
```

Note on `rareTermOutranksCommonTerm` math: query tokens after tokenizing "common rare" are both kept (len ≥ 3, not stop words). scoreA = (5/10)×ln(10/9) = 0.5×0.105 = 0.053; scoreB = (1/10)×ln(10/1) = 0.1×2.303 = 0.230 → B first. `higherTfRanksFirstWhenIdfZero`: N=2, df=2 → idf=0 for the only term → tf fallback: A=0.3, B=0.1 → A first.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=RankServiceTest test`
Expected: FAIL with "RankService/SearchHit not found" (compilation error).

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/crawler/model/SearchHit.java` with this exact content:

```java
package com.crawler.model;

public class SearchHit {

    private String url;
    private String title;
    private String snippet;
    private double score;

    public SearchHit() {}

    public SearchHit(String url, String title, String snippet, double score) {
        this.url = url;
        this.title = title;
        this.snippet = snippet;
        this.score = score;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
}
```

Create `src/main/java/com/crawler/service/RankService.java` with this exact content:

```java
package com.crawler.service;

import com.crawler.model.SearchHit;
import com.crawler.repository.CrawlStateRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RankService {

    public static final int DEFAULT_LIMIT = 10;
    public static final int MAX_LIMIT = 50;

    private final CrawlStateRepository repository;

    public RankService(CrawlStateRepository repository) {
        this.repository = repository;
    }

    public List<SearchHit> search(String query, Integer limit) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("Search query must not be blank");
        int n = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        Map<String, Integer> qtokens = TextTokenizer.tokenize(query);
        if (qtokens.isEmpty()) return List.of();
        long totalDocs = repository.getTotalDocs();
        Map<String, Map<String, Double>> postings = new HashMap<>();
        Map<String, Long> df = new HashMap<>();
        Map<String, Integer> docLen = new HashMap<>();
        for (String t : qtokens.keySet()) {
            Map<String, Double> scores = repository.getTermScores(t);
            if (scores.isEmpty()) continue;
            postings.put(t, scores);
            df.put(t, repository.getTermDocCount(t));
            for (String url : scores.keySet()) {
                docLen.computeIfAbsent(url, u -> {
                    String l = repository.getDoc(u).getOrDefault("length", "0");
                    try {
                        return Integer.parseInt(l);
                    } catch (NumberFormatException e) {
                        return 1;
                    }
                });
            }
        }
        return combineScores(postings, docLen, totalDocs, df, n, repository);
    }

    static List<SearchHit> combineScores(Map<String, Map<String, Double>> postings,
                                         Map<String, Integer> docLen,
                                         long totalDocs,
                                         Map<String, Long> df,
                                         int limit,
                                         CrawlStateRepository repository) {
        Map<String, Double> idf = new HashMap<>();
        boolean anyIdf = false;
        for (String t : postings.keySet()) {
            long d = df.getOrDefault(t, 0L);
            double v = (d > 0 && totalDocs > d) ? Math.log((double) totalDocs / d) : 0.0;
            idf.put(t, v);
            if (v > 0) anyIdf = true;
        }
        Map<String, Double> agg = new HashMap<>();
        for (String t : postings.keySet()) {
            for (Map.Entry<String, Double> e : postings.get(t).entrySet()) {
                String url = e.getKey();
                double tf = e.getValue() / Math.max(1, docLen.getOrDefault(url, 1));
                double w = anyIdf ? idf.get(t) : 1.0;
                agg.merge(url, tf * w, Double::sum);
            }
        }
        List<String> ranked = new ArrayList<>(agg.keySet());
        ranked.sort(Comparator.comparingDouble((String u) -> agg.get(u)).reversed()
                .thenComparing(Comparator.naturalOrder()));
        List<SearchHit> out = new ArrayList<>();
        for (String url : ranked.subList(0, Math.min(limit, ranked.size()))) {
            Map<String, String> doc = repository.getDoc(url);
            out.add(new SearchHit(url, doc.getOrDefault("title", ""),
                    doc.getOrDefault("snippet", ""), agg.get(url)));
        }
        return out;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=RankServiceTest test`
Expected: PASS (Tests run: 4, Failures: 0, Errors: 0).

- [ ] **Step 5: Commit**

```bash
git add web-crawler/src/main/java/com/crawler/service/RankService.java web-crawler/src/main/java/com/crawler/model/SearchHit.java web-crawler/src/test/java/com/crawler/service/RankServiceTest.java
git commit -m "feat(search): TF-IDF rank service with unit tests"
```

---

### Task 4: Search API endpoint + MockMvc tests

**Files:**
- Create: `src/main/java/com/crawler/controller/SearchController.java`
- Test: `src/test/java/com/crawler/controller/SearchControllerTest.java`

**Interfaces:**
- Consumes: `RankService.search(String,Integer)` from Task 3 (Mockito-stubbed here).
- Produces: `GET /api/search?q={q}&limit={n}` → 200 JSON list / 400 `{error}` on blank q. No collision: `/api/search` lives under a different base than `/api/crawl/*`.

- [ ] **Step 1: Write the failing test**

```java
package com.crawler.controller;

import com.crawler.model.SearchHit;
import com.crawler.service.RankService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
public class SearchControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    RankService rankService;

    @Test
    void searchReturnsRankedHits() throws Exception {
        when(rankService.search(eq("java"), eq(10))).thenReturn(List.of(
                new SearchHit("https://d.test", "Doc", "snippet here", 1.5)));

        mvc.perform(get("/api/search").param("q", "java").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].url").value("https://d.test"))
                .andExpect(jsonPath("$[0].score").value(1.5));
    }

    @Test
    void searchBlankIs400() throws Exception {
        when(rankService.search(eq(" "), eq(10))).thenThrow(new IllegalArgumentException("blank"));

        mvc.perform(get("/api/search").param("q", " ").param("limit", "10"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=SearchControllerTest test`
Expected: FAIL with "SearchController not found" (compilation error on `@WebMvcTest(SearchController.class)`).

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/crawler/controller/SearchController.java` with this exact content:

```java
package com.crawler.controller;

import com.crawler.service.RankService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final RankService rankService;

    public SearchController(RankService rankService) {
        this.rankService = rankService;
    }

    @GetMapping
    public ResponseEntity<?> search(@RequestParam String q,
                                    @RequestParam(required = false) Integer limit) {
        try {
            return ResponseEntity.ok(rankService.search(q, limit));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=SearchControllerTest test`
Expected: PASS (Tests run: 2, Failures: 0, Errors: 0). Then run the full suite: `mvn test`
Expected: BUILD SUCCESS, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add web-crawler/src/main/java/com/crawler/controller/SearchController.java web-crawler/src/test/java/com/crawler/controller/SearchControllerTest.java
git commit -m "feat(search): ranked search API endpoint"
```

---

### Task 5: Worker indexing hook + search page + live verify

**Files:**
- Modify: `src/main/java/com/crawler/worker/CrawlWorker.java` (2 edits: import + hook block in success path)
- Test: `src/test/java/com/crawler/worker/WorkerIndexTest.java` (new)
- Create: `src/main/resources/static/search.html` (exact content below)
- Modify: `src/main/resources/static/index.html` (add Search Pages button)
- Test: live curl checks on :8900 (rebuild + restart + crawl + search asserts)

**Interfaces:**
- Consumes: `TextTokenizer.tokenize` (Task 1), `CrawlStateRepository.indexPage` (Task 2), Phase 1 worker `processUrl` success path (imports `Document`; Phase 1 file has `log`, `jobId`, `crawlStateRepository` in scope).
- Produces: every successful fetch indexed with title/snippet/length/tf; `/search.html` serving ranked UI; home links to it.

- [ ] **Step 1: Write the failing test**

```java
package com.crawler.worker;

import com.crawler.model.CrawlTask;
import com.crawler.repository.CrawlStateRepository;
import com.crawler.service.PageFetcherService;
import com.crawler.service.QueueService;
import com.crawler.service.UrlDiscoveryService;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class WorkerIndexTest {

    @Mock
    PageFetcherService fetcher;

    @Mock
    CrawlStateRepository repo;

    UrlDiscoveryService discovery = new UrlDiscoveryService();
    QueueService queues = new QueueService();

    @Test
    @SuppressWarnings("unchecked")
    void successfulFetchIndexesPageText() {
        when(repo.markVisited(any(), any())).thenReturn(true);
        when(fetcher.fetchPage("https://s.test")).thenReturn(Jsoup.parse(
                "<html><head><title>Test Page</title></head><body>Test crawler test page crawler</body></html>",
                "https://s.test"));
        queues.createQueue("j1");
        CrawlJobContext ctx = new CrawlJobContext("j1", 1, 1, queues);
        ctx.addTask(new CrawlTask("https://s.test", null, 0));

        new CrawlWorker("j1", ctx, fetcher, discovery, repo, () -> {}).run();

        ArgumentCaptor<Map<String, Integer>> tfCaptor = ArgumentCaptor.forClass(Map.class);
        verify(repo).indexPage(eq("https://s.test"), eq("j1"), eq("Test Page"),
                anyString(), anyInt(), tfCaptor.capture());
        assertEquals(2, tfCaptor.getValue().get("crawler"));
        assertEquals(2, tfCaptor.getValue().get("test"));
    }
}
```

Body text is `Test crawler test page crawler` → tokens test:2, crawler:2, page:1 (all len ≥ 3, none stop words). Title `Test Page` asserted as passed through.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=WorkerIndexTest test`
Expected: FAIL with "Wanted but not invoked: indexPage" (Mockito verification error — hook does not exist yet).

- [ ] **Step 3: Write minimal implementation**

Edit 1 — add import to `CrawlWorker.java` (after the `import com.crawler.service.UrlDiscoveryService;` line):

```java
import com.crawler.service.TextTokenizer;
```

Edit 2 — in `processUrl`, insert the indexing hook immediately after the `context.addUrls(linkCount);` line:

```java
            try {
                String pageText = document.body() != null ? document.body().text() : "";
                String pageTitle = document.title() != null ? document.title() : "";
                java.util.Map<String, Integer> termFreq = TextTokenizer.tokenize(pageText);
                int totalTokens = termFreq.values().stream().mapToInt(Integer::intValue).sum();
                String snippet = pageText.length() > 200 ? pageText.substring(0, 200) : pageText;
                crawlStateRepository.indexPage(url, jobId, pageTitle, snippet,
                        Math.max(1, totalTokens), termFreq);
            } catch (Exception ie) {
                log.warn("Indexing failed for {}: {}", url, ie.getMessage());
            }
```

The hook never breaks crawling: any indexing exception is caught and logged.

- [ ] **Step 4: Create search.html + home button, run tests**

Create `src/main/resources/static/search.html` with this exact content:

```html
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Search Pages</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'Segoe UI',system-ui,sans-serif;background:#0f0f23;color:#e0e0e0;min-height:100vh}
.container{max-width:900px;margin:0 auto;padding:32px 20px}
a{color:#93c5fd}
header{display:flex;align-items:baseline;gap:16px;margin-bottom:20px}
header h1{font-size:1.6rem;background:linear-gradient(135deg,#60a5fa,#a78bfa);-webkit-background-clip:text;-webkit-text-fill-color:transparent}
.card{background:rgba(30,30,60,.6);border:1px solid rgba(59,130,246,.15);border-radius:12px;padding:20px;margin-bottom:20px}
.row{display:flex;gap:10px;flex-wrap:wrap}
input{flex:1;min-width:200px;padding:9px 12px;background:rgba(15,15,35,.8);border:1px solid rgba(59,130,246,.2);border-radius:8px;color:#e0e0e0}
button{padding:9px 18px;border:none;border-radius:8px;font-weight:600;cursor:pointer;background:linear-gradient(135deg,#3b82f6,#8b5cf6);color:#fff}
.hit{padding:12px 4px;border-bottom:1px solid rgba(59,130,246,.12)}
.hit .title{font-size:1rem;font-weight:600}
.hit .url{font-size:.78rem;color:#64748b;word-break:break-all}
.hit .snippet{font-size:.85rem;color:#cbd5e1;margin-top:4px}
.badge{display:inline-block;padding:2px 10px;border-radius:20px;font-size:.72rem;font-weight:700;background:rgba(59,130,246,.15);color:#60a5fa;margin-left:8px}
#error{color:#f87171;margin-top:8px;min-height:1.2em}
.meta{font-size:.85rem;color:#94a3b8;margin-top:8px}
</style>
</head>
<body>
<div class="container">
<header><h1>Search Pages</h1><a href="/">← back to crawler</a><a href="/hierarchy.html">hierarchy →</a></header>
<div class="card">
<div class="row">
<input id="q" placeholder="search indexed pages… (e.g. example)" onkeydown="if(event.key==='Enter')doSearch()">
<button onclick="doSearch()">Search</button>
</div>
<div id="error"></div>
<div id="meta" class="meta"></div>
<div id="results"></div>
</div>
</div>
<script>
async function doSearch(){
  const q=document.getElementById('q').value.trim();
  const err=document.getElementById('error');
  const box=document.getElementById('results');
  const meta=document.getElementById('meta');
  err.textContent='';box.innerHTML='';meta.textContent='';
  if(!q){err.textContent='Type something to search';return;}
  const res=await fetch(`/api/search?q=${encodeURIComponent(q)}&limit=10`);
  if(!res.ok){err.textContent='Search failed: '+res.status;return;}
  const hits=await res.json();
  if(!hits.length){meta.textContent=`No pages match "${q}" yet — run a crawl first.`;return;}
  meta.textContent=`${hits.length} result(s) for "${q}" (ranked by TF-IDF)`;
  box.innerHTML=hits.map(h=>`<div class="hit"><span class="title"><a href="${escapeAttr(h.url)}" target="_blank" rel="noopener">${escapeHtml(h.title||h.url)}</a></span><span class="badge">${Number(h.score).toFixed(4)}</span><div class="url">${escapeHtml(h.url)}</div><div class="snippet">${escapeHtml(h.snippet||'')}</div></div>`).join('');
}
function escapeHtml(s){return (s||'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
function escapeAttr(s){return escapeHtml(s).replace(/`/g,'&#96;');}
</script>
</body>
</html>
```

In `src/main/resources/static/index.html`, replace:

```html
                <a class="btn btn-primary" href="/hierarchy.html" style="text-decoration:none;display:inline-block">
                    View Hierarchy
                </a>
```

with:

```html
                <a class="btn btn-primary" href="/hierarchy.html" style="text-decoration:none;display:inline-block">
                    View Hierarchy
                </a>
                <a class="btn btn-primary" href="/search.html" style="text-decoration:none;display:inline-block;margin-left:8px">
                    Search Pages
                </a>
```

Run: `mvn test`
Expected: BUILD SUCCESS, 0 failures (prior 22 + TokenizerTest 4 + RankServiceTest 4 + SearchControllerTest 2 + WorkerIndexTest 1).

- [ ] **Step 5: Rebuild, restart :8900, live verify search**

Run: `mvn -q -DskipTests package`
Expected: BUILD SUCCESS.

Run: `OLD=$(ss -tlnp | grep ':8900' | grep -oP 'pid=\K[0-9]+' | head -1); kill -9 "$OLD"; sleep 3; nohup java -jar target/web-crawler-1.0.0.jar --server.port=8900 > /tmp/webcrawler-8900.log 2>&1 & echo STARTED:$!; sleep 10; ss -tlnp | grep 8900`
Expected: `STARTED:<pid>` then `LISTEN … *:8900`. (SIGKILL required — this app ignores SIGTERM.)

Run: `curl -s -o /dev/null -w "search page: %{http_code}\n" http://localhost:8900/search.html`
Expected: `search page: 200`.

Run: `JOB=$(curl -s -X POST http://localhost:8900/api/crawl -H "Content-Type: application/json" -d '{"url":"https://example.com","maxPages":3}' | python3 -c "import sys,json;print(json.load(sys.stdin)['jobId'])"); sleep 12; curl -s "http://localhost:8900/api/search?q=example&limit=10" | python3 -c "import sys,json;h=json.load(sys.stdin);assert h,'no hits';assert all('url' in x and 'snippet' in x for x in h),h;print('search OK, hits:',len(h),'top:',h[0]['url'],'score:',round(h[0]['score'],4))"`
Expected: `search OK, hits: N …` with N ≥ 1.

Run: `curl -s "http://localhost:8900/api/search?q=xyzzyqqq&limit=10"`
Expected: `[]`.

- [ ] **Step 6: Commit**

```bash
git add web-crawler/src/main/java/com/crawler/worker/CrawlWorker.java web-crawler/src/test/java/com/crawler/worker/WorkerIndexTest.java web-crawler/src/main/resources/static/search.html web-crawler/src/main/resources/static/index.html
git commit -m "feat(search): index at crawl time, search page, home button"
```

---

## Self-Review

- Spec coverage (search spec): tokenizer §1 → Task 1; index model §2 → Task 2 (keys, ZSET/HASH/SET/meta-increment, re-crawl overwrite); indexing flow §3 → Task 5 hook (success path only, try/catch, 200-char snippet, 1MB bound inherited); ranking §4 → Task 3 (formula, desc+URL tie-break, tf fallback, pure combineScores); API §5 → Task 4 (route, shape, 400/[] cases); UI §5 → Task 5 (search.html + home button); testing §6 → Tasks 1/3/4 unit + Task 5 live.
- Placeholder scan: every step has exact code/commands/expected outputs; no TBD/TODO/similar-to.
- Type consistency: `TextTokenizer.tokenize(String)→Map<String,Integer>` identical in Tasks 1/3/5; `indexPage(String,String,String,String,int,Map<String,Integer>)` identical in Tasks 2/5; `RankService.search(String,Integer)→List<SearchHit>` identical in Tasks 3/4; `SearchHit(url,title,snippet,score)` ctor identical in Tasks 3/4; `GET /api/search?q&limit` identical in Tasks 4/5.
