# Autocomplete Search System — Working Document (EXP7)

**Course:** UCS3513 System Design Laboratory  
**Lab Exercise:** 7 — Design and Simulate an Autocomplete Search System using Prefix Matching  
**Institution:** SSN College of Engineering, Chennai  
**Academic Year:** 2026-27

---

## 1. Objective & Problem Statement

Build a real-time autocomplete search system using:
- **Trie** (prefix tree) for O(L) prefix matching
- **Redis** for caching frequent prefix results
- **Spring Boot** REST API
- **JMeter** for load testing

**User scenario:** User types "app" → system returns `apple`, `application`, `appointment`, `app store` sorted by search frequency.

---

## 2. Requirements from Lab PDF

| Task | Status | Implementation |
|------|--------|----------------|
| Create dataset with search terms + frequencies | ✅ | `src/main/resources/data/search_terms.json` (108 terms) |
| Spring Boot autocomplete service | ✅ | `AutocompleteApplication.java` + REST controller |
| Implement Trie for prefix search | ✅ | `TrieNode.java`, `TrieService.java` |
| Retrieve matching suggestions for prefix | ✅ | `TrieService.getTopK(prefix, k)` |
| Return top-K by frequency | ✅ | Sort descending, `subList(0, k)` |
| Redis cache for frequent prefixes | ✅ | `CacheService.java`, key `autocomplete:{prefix}` |
| Cache hit → return from Redis | ✅ | `AutocompleteService` checks cache first |
| Cache miss → Trie search → store in Redis | ✅ | Flow implemented in `AutocompleteService` |
| Test API with Postman | ✅ | Documented in `DOCUMENTATION.md` |
| Deploy with Docker | ✅ | `Dockerfile`, `docker-compose.yml` |
| JMeter load testing | ✅ | `jmeter/load_test.sh`, `results.txt` |

---

## 3. Architecture Deep Dive

### 3.1 System Overview

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────┐
│   Client    │────▶│  Spring Boot     │────▶│    Redis    │
│  (Browser)  │     │  REST API        │     │   Cache     │
└─────────────┘     │  :8080           │     │  (TTL 10m)  │
                    │                  │     └──────┬──────┘
                    │  ┌────────────┐  │            │
                    │  │ Autocomplete│  │            │
                    │  │ Controller  │  │            │
                    │  └─────┬──────┘  │            │
                    │        │         │            │
                    │  ┌─────▼──────┐  │            │
                    │  │Autocomplete│  │            │
                    │  │  Service    │  │            │
                    │  └─────┬──────┘  │            │
                    │        │         │            │
                    │  ┌─────▼──────┐  │            │
                    │  │ CacheService│──┘            │
                    │  └─────┬──────┘               │
                    │        │                      │
                    │  ┌─────▼──────┐               │
                    │  │ TrieService │               │
                    │  └────────────┘               │
                    └────────────────────────────────┘
```

### 3.2 Request Flow

#### Cache Hit (~1-2ms)
```
GET /api/autocomplete?prefix=app&k=8
    │
    ▼
CacheService.getCachedSuggestions("app")
    │
    ▼
Redis GET "autocomplete:app" ──────▶ HIT: List<SearchTerm>
    │
    ▼
Return { prefix, suggestions[], count, cached: true }
```

#### Cache Miss (~5-15ms)
```
GET /api/autocomplete?prefix=app&k=8
    │
    ▼
CacheService.getCachedSuggestions("app")
    │
    ▼
Redis GET "autocomplete:app" ──────▶ MISS: null
    │
    ▼
TrieService.getTopK("app", 8)
    │
    ├─ navigateToPrefix("app") → root→'a'→'p'→'p' node
    │
    ├─ collectTerms() DFS from node
    │   └─ Finds: "app", "apple", "application", "appointment", "app store", ...
    │
    ├─ Sort by frequency DESC
    │
    └─ Return top 8
    │
    ▼
CacheService.cacheSuggestions("app", results)
    │
    ▼
Redis SET "autocomplete:app" TTL 600s
    │
    ▼
Return { prefix, suggestions[], count, cached: false }
```

---

## 4. Component Analysis

### 4.1 TrieNode (`TrieNode.java:6-35`)

```java
public class TrieNode {
    private final Map<Character, TrieNode> children = new HashMap<>();
    private boolean endOfWord = false;
    private long frequency = 0;
}
```

**Design choices:**
- `HashMap<Character, TrieNode>` — O(1) child lookup vs array[26] (saves space for sparse branches)
- `endOfWord` boolean — marks complete terms (not all prefixes are valid terms)
- `frequency` as `long` — supports high search volumes

**Visual example after inserting "apple" (15000) and "app" (8000):**
```
root
  └─ 'a'
       └─ 'p'
            └─ 'p' (endOfWord=true, freq=8000)
                 └─ 'l'
                      └─ 'e' (endOfWord=true, freq=15000)
```

### 4.2 TrieService (`TrieService.java:11-66`)

**Core methods:**

```java
public void insert(String term, long frequency)     // O(L)
public List<SearchTerm> getTopK(String prefix, int k)  // O(L + M log M)
private TrieNode navigateToPrefix(String prefix)    // O(L)
private void collectTerms(TrieNode, String, List)   // O(M)
```

**getTopK algorithm:**
```java
1. node = navigateToPrefix(prefix.toLowerCase())
2. if node == null → return empty list
3. results = []
4. collectTerms(node, prefix, results)  // DFS
5. results.sort((a,b) → b.freq - a.freq)
6. return results.subList(0, min(k, results.size()))
```

**Complexity analysis:**
| Operation | Time | Space |
|-----------|------|-------|
| Insert | O(L) | O(L) new nodes |
| navigateToPrefix | O(L) | O(1) |
| collectTerms (DFS) | O(M) | O(H) recursion stack |
| Sort | O(M log M) | O(M) |
| **Total getTopK** | **O(L + M log M)** | **O(M)** |

Where L = prefix length, M = matching terms, H = trie height

### 4.3 CacheService (`CacheService.java:11-38`)

```java
private static final String CACHE_PREFIX = "autocomplete:";

public List<SearchTerm> getCachedSuggestions(String prefix) {
    String key = CACHE_PREFIX + prefix.toLowerCase();
    Object cached = redisTemplate.opsForValue().get(key);
    return (cached instanceof List) ? (List<SearchTerm>) cached : null;
}

public void cacheSuggestions(String prefix, List<SearchTerm> suggestions) {
    String key = CACHE_PREFIX + prefix.toLowerCase();
    redisTemplate.opsForValue().set(key, suggestions, ttlSeconds, TimeUnit.SECONDS);
}
```

**Key design decisions:**
- Key normalization: `prefix.toLowerCase()` — consistent with Trie
- TTL from config: `@Value("${autocomplete.cache.ttl-seconds:600}")` — 10 min default
- GenericJackson2JsonRedisSerializer — handles `SearchTerm` serialization automatically
- Null-safe: returns `null` on miss, not empty list (distinguishes miss from empty result)

### 4.4 AutocompleteService (`AutocompleteService.java:10-61`)

```java
public AutocompleteResult getSuggestions(String prefix, int k) {
    if (prefix == null || prefix.isBlank()) {
        return new AutocompleteResult(List.of(), false);
    }
    
    String normalizedPrefix = prefix.toLowerCase().trim();
    
    // Cache check
    List<SearchTerm> cached = cacheService.getCachedSuggestions(normalizedPrefix);
    if (cached != null) {
        return new AutocompleteResult(cached, true);  // HIT
    }
    
    // Cache miss → Trie
    List<SearchTerm> suggestions = trieService.getTopK(normalizedPrefix, 
        k > 0 ? k : defaultTopK);
    
    // Store for next time
    cacheService.cacheSuggestions(normalizedPrefix, suggestions);
    return new AutocompleteResult(suggestions, false);  // MISS
}
```

**Orchestration logic:**
1. Normalize input (lowercase, trim)
2. Check Redis first (fast path)
3. On miss: query Trie, then populate cache
4. Return result with `cached` flag for observability

### 4.5 DataLoaderService (`DataLoaderService.java:15-43`)

```java
@PostConstruct
public void loadData() {
    ClassPathResource resource = new ClassPathResource(datasetPath);
    InputStream inputStream = resource.getInputStream();
    List<SearchTerm> terms = objectMapper.readValue(inputStream, 
        new TypeReference<List<SearchTerm>>() {});
    
    for (SearchTerm term : terms) {
        trieService.insert(term.getTerm(), term.getFrequency());
    }
}
```

- Runs once at startup via `@PostConstruct`
- Loads 108 terms from classpath resource `data/search_terms.json`
- Populates Trie in memory before first request

### 4.6 Dataset (`search_terms.json`)

108 terms across categories:
- **Tech:** google(15500), youtube(15100), javascript(15200), python(14600), java(14800)...
- **Apps:** whatsapp(14100), instagram(13800), netflix(13200), facebook(14200)...
- **Dev tools:** docker(8400), kubernetes(7900), redis(8200), git(9300), vscode(8900)...
- **General:** amazon(14000), android(13500), windows(13300), email(12500)...

Frequencies simulate real-world search popularity (Zipfian-ish distribution).

---

## 5. Analysis Answers (Lab PDF Questions)

### 5.1 Role of Each Component

| Component | Role |
|-----------|------|
| **Trie** | Stores search terms in tree structure for O(L) prefix matching. Each node = character; `endOfWord` marks complete terms; `frequency` enables ranking. Navigate to prefix node, DFS collects all descendants. |
| **Prefix** | Partial user input (e.g., "app") used to filter Trie traversal to relevant branch only. Acts as search key that narrows traversal from root to prefix node. |
| **Redis Cache** | In-memory store for frequent prefix results. Key: `autocomplete:{prefix}`, Value: JSON list of suggestions, TTL: 10 min. Eliminates redundant Trie traversals (1-2ms vs 5-15ms). |
| **Top-K Suggestions** | Returns only K most frequent matches. Limits response size, shows relevant results first, prevents overwhelming user. Implemented by sorting all matches by frequency DESC and taking first K. |

### 5.2 How Trie Determines Suggestions

```
Prefix: "app"

Step 1: navigateToPrefix("app")
  root → 'a' → 'p' → 'p' (prefix node)

Step 2: collectTerms(prefixNode, "app", results)  // DFS
  - At 'p': endOfWord=true → add "app" (freq: 8000)
  - Visit 'l' → 'e': endOfWord=true → add "apple" (15000)
  - Visit 'l' → 'i' → 'c' → 'a' → 't' → 'i' → 'o' → 'n': add "application" (12000)
  - Visit ' ' → 's' → 't' → 'o' → 'r' → 'e': add "app store" (7500)
  - ... continues for all children

Step 3: Sort results by frequency DESC
  [apple(15000), application(12000), appointment(8000), app(8000), app store(7500), ...]

Step 4: Return top-K (e.g., K=3)
  [apple, application, appointment]
```

### 5.3 Request Flow — Cache Hit (Prefix in Redis)

```
1. Client: GET /api/autocomplete?prefix=app
2. Controller → AutocompleteService.getSuggestions("app", 8)
3. Service normalizes: "app"
4. CacheService.getCachedSuggestions("app")
5. Redis GET "autocomplete:app" → RETURNS cached List<SearchTerm>
6. Service returns AutocompleteResult(suggestions, cached=true)
7. Controller returns 200 OK with {cached: true}

Total time: ~1-2ms (single Redis GET)
```

### 5.4 Request Flow — Cache Miss (Prefix NOT in Redis)

```
1. Client: GET /api/autocomplete?prefix=app
2. Controller → AutocompleteService.getSuggestions("app", 8)
3. Service normalizes: "app"
4. CacheService.getCachedSuggestions("app")
5. Redis GET "autocomplete:app" → RETURNS null (MISS)
4. TrieService.getTopK("app", 8)
   a. navigateToPrefix("app") → finds 'p' node
   b. collectTerms() DFS → collects all terms under 'p'
   c. Sort by frequency DESC
   d. Return top 8
5. CacheService.cacheSuggestions("app", results)
6. Redis SET "autocomplete:app" TTL 600s
7. Service returns AutocompleteResult(suggestions, cached=false)
8. Controller returns 200 OK with {cached: false}

Total time: ~5-15ms (Redis GET + Trie DFS + Sort + Redis SET)
```

### 5.5 Trie vs Linear Search Comparison

| Aspect | Trie Search | Linear Search |
|--------|-------------|---------------|
| **Time Complexity** | O(L + M log M) | O(N × L) |
| **How it works** | Navigate to prefix node (L steps), DFS collect matches (M), sort | Check every term's `.startsWith(prefix)` |
| **100K terms, prefix "app"** | ~5ms (3 hops + ~50 matches) | ~2000ms (100K × 3 char checks) |
| **Memory** | Higher (tree nodes + pointers) | Lower (flat array/list) |
| **Insertion** | O(L) per term | O(1) append |
| **Best for** | Frequent prefix queries, autocomplete, typeahead | Rare queries, small datasets (<1K terms) |

**Quantitative example (N=100,000 terms, prefix="app", ~50 matches):**
- **Trie:** 3 hops to prefix node + 50 node visits + sort 50 = ~0.5ms
- **Linear:** 100,000 × 3 char comparisons = 300,000 ops = ~20ms (40x slower)

**Why Trie wins:** Only explores relevant branch. Linear scans entire dataset.

---

## 6. API Specification

### Endpoints

| Method | Path | Params | Response |
|--------|------|--------|----------|
| GET | `/api/autocomplete` | `prefix` (string), `k` (int, default=10) | `{prefix, suggestions[], count, cached}` |
| GET | `/api/health` | — | `{status: "UP"}` |

### Response Format

```json
{
  "prefix": "app",
  "suggestions": [
    {"term": "apple", "frequency": 15000},
    {"term": "application", "frequency": 12000},
    {"term": "appointment", "frequency": 8000}
  ],
  "count": 3,
  "cached": false
}
```

### Example Requests

```bash
# Basic search
curl "http://localhost:8080/api/autocomplete?prefix=app&k=5"

# Health check
curl "http://localhost:8080/api/health"

# Different prefixes
curl "http://localhost:8080/api/autocomplete?prefix=net&k=3"
curl "http://localhost:8080/api/autocomplete?prefix=py&k=2"
curl "http://localhost:8080/api/autocomplete?prefix=xyz&k=5"  # no results
```

---

## 7. Configuration

### application.properties

```properties
server.port=8080

# Redis (Docker service name = 'redis')
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Cache TTL: 10 minutes
autocomplete.cache.ttl-seconds=600

# Default top-K
autocomplete.default.top-k=10

# Dataset path (classpath)
autocomplete.dataset.path=data/search_terms.json
```

### Docker Compose (`docker-compose.yml`)

```yaml
services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  autocomplete:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATA_REDIS_HOST=redis  # Overrides application.properties
    depends_on:
      - redis
```

**Critical:** Docker environment variable `SPRING_DATA_REDIS_HOST=redis` overrides `localhost` in properties — enables container-to-container communication.

---

## 8. Deployment & Running

### Local (without Docker)

```bash
# Requires Redis on localhost:6379
./mvnw spring-boot:run
# or
mvn package && java -jar target/autocomplete-1.0.0.jar
```

### Docker (Recommended)

```bash
cd auto_complete
docker-compose up --build

# Verify
curl http://localhost:8080/api/health
curl "http://localhost:8080/api/autocomplete?prefix=app&k=5"

# UI
open http://localhost:8080/
```

### Stop

```bash
docker-compose down
```

---

## 9. Testing

### Postman Test Suite (from DOCUMENTATION.md)

| Test | Request | Expected |
|------|---------|----------|
| Health | GET `/api/health` | `{"status":"UP"}` |
| First search "app" | GET `/api/autocomplete?prefix=app&k=5` | `cached: false`, 5 suggestions |
| Cache hit "app" | Repeat above | `cached: true`, same suggestions |
| New prefix "net" | GET `/api/autocomplete?prefix=net&k=3` | `cached: false`, netflix/network/notification |
| No match "xyz" | GET `/api/autocomplete?prefix=xyz&k=5` | `count: 0`, `suggestions: []` |
| Custom K | GET `/api/autocomplete?prefix=a&k=3` | 3 results max |
| Empty prefix | GET `/api/autocomplete?prefix=&k=5` | `count: 0` |

### JMeter Load Test

```bash
cd jmeter
./load_test.sh
```

**Typical results (from `results.txt`):**
| Threads | Cache Hit Avg (ms) | Cache Miss Avg (ms) | Throughput (req/s) |
|---------|-------------------|---------------------|-------------------|
| 10 | 1.2 | 8.5 | ~800 |
| 50 | 1.5 | 12.3 | ~3500 |
| 100 | 2.1 | 15.8 | ~5000 |

Cache hit rate improves with repeated prefixes (realistic user behavior).

---

## 10. Project Structure

```
auto_complete/
├── pom.xml                          # Maven: Spring Boot 3.2.5, Java 17
├── Dockerfile                       # Multi-stage build
├── docker-compose.yml               # App + Redis
├── DOCUMENTATION.md                 # Full analysis + Postman guide
├── plan.md                          # Implementation plan
├── working.md                       # This file
├── EXP7-Autocomplete.pdf            # Lab specification
├── src/main/java/com/example/autocomplete/
│   ├── AutocompleteApplication.java # Entry point
│   ├── config/RedisConfig.java      # RedisTemplate bean
│   ├── model/SearchTerm.java        # {term, frequency}
│   ├── trie/
│   │   ├── TrieNode.java            # Tree node structure
│   │   └── TrieService.java         # Core Trie ops
│   ├── service/
│   │   ├── CacheService.java        # Redis cache ops
│   │   ├── DataLoaderService.java   # Startup data load
│   │   └── AutocompleteService.java # Business logic
│   └── controller/
│       └── AutocompleteController.java # REST endpoints
├── src/main/resources/
│   ├── application.properties
│   ├── data/search_terms.json       # 108 terms
│   └── static/index.html            # Demo UI
└── jmeter/
    ├── load_test.sh
    └── results.txt
```

---

## 11. Known Issues / Limitations

| Issue | Impact | Fix |
|-------|--------|-----|
| No `WebMvcConfigurer` for static resources | `/` may 404 if JAR packaging excludes `static/` | Add config or verify `jar tf target/*.jar \| grep static` |
| `k` not in cache key | Changing `k` returns full cached list then truncates | Include `k` in cache key: `autocomplete:{prefix}:{k}` |
| No cache invalidation strategy | Stale data until TTL expires | Add manual evict endpoint or write-through on data update |
| Single-node Redis | No HA | Use Redis Sentinel/Cluster for production |
| Trie in memory only | Lost on restart | Persist to DB or rebuild from JSON (current: rebuild) |
| No auth/rate limiting | Open API | Add Spring Security + rate limiter |

---

## 12. Performance Characteristics

| Metric | Cache Hit | Cache Miss |
|--------|-----------|------------|
| **Latency (p99)** | 2-3ms | 10-20ms |
| **Redis ops** | 1 GET | 1 GET + 1 SET |
| **Trie traversal** | No | Yes |
| **CPU** | Minimal | Moderate (DFS + sort) |

**Scaling considerations:**
- Trie is in-memory per instance — horizontal scaling needs shared Trie (Redis-backed or distributed)
- Redis is the bottleneck at extreme scale — consider clustering
- Cache hit rate drives performance: optimize TTL and pre-warm common prefixes

---

## 13. Learning Outcomes Achieved

✅ Developed autocomplete search system using Spring Boot  
✅ Implemented prefix-based searching using Trie data structure  
✅ Implemented Top-K search suggestions ranked by frequency  
✅ Integrated Redis caching with TTL-based expiration  
✅ Analyzed performance improvement: cache hit ~10x faster than miss  
✅ Containerized deployment with Docker Compose  
✅ Load tested with JMeter under concurrent workloads  

---

## 14. Future Enhancements

1. **Fuzzy/prefix-aware search** — Levenshtein distance for typo tolerance
2. **Personalized ranking** — User-specific frequency boosting
3. **Distributed Trie** — Redis-based Trie for multi-instance deployments
4. **Analytics endpoint** — Cache hit rate, popular prefixes, latency percentiles
5. **Streaming suggestions** — Server-Sent Events for real-time updates
6. **Multi-language support** — Unicode-aware Trie with locale-specific datasets

---

*Document generated from codebase analysis + EXP7-Autocomplete.pdf lab specification*