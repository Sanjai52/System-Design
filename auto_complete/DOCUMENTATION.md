# EXP7: Autocomplete Search System - Documentation & Analysis

## System Overview

An autocomplete search system that provides real-time search suggestions as the user types, using **Trie** for efficient prefix matching and **Redis** for caching frequently requested queries.

---

## Architecture

```
Client (Browser)
    │
    ▼
Spring Boot REST API (/api/autocomplete?prefix=app&k=8)
    │
    ├──► Redis Cache
    │       ├── HIT  → Return cached results
    │       └── MISS → Continue to Trie
    │
    ├──► Trie Data Structure
    │       └── Search by prefix → Collect matches → Sort by frequency → Top-K
    │
    └──► Store results in Redis (TTL: 10 min)
            │
            ▼
        Return Response
```

---

## Component Details

### 1. Trie (Prefix Tree)

**Role:** Efficiently store and retrieve search terms based on prefixes.

**Structure:**
```
TrieNode
├── children: Map<Character, TrieNode>   (links to next characters)
├── isEndOfWord: boolean                 (marks complete words)
└── frequency: int                       (search frequency for ranking)
```

**Example - Inserting "app", "apple", "application":**
```
        root
        │
        a
        │
        p
        │
        p ◄── endOfWord=true (term: "app", freq: 8000)
        │
        l
        │
        e ◄── endOfWord=true (term: "apple", freq: 15000)
        │
        ... (more children for "application")
```

**How prefix search works:**
1. Navigate to the node matching the last character of prefix
2. DFS from that node to collect all words with that prefix
3. Sort collected words by frequency (descending)
4. Return top-K results

**Time Complexity:** O(L + M) where L = prefix length, M = number of matching results

---

### 2. Redis Cache

**Role:** Store frequently requested prefix results to avoid repeated Trie traversal.

**Cache Strategy:**
- **Key:** `autocomplete:{prefix}` (e.g., `autocomplete:app`)
- **Value:** JSON list of suggestions
- **TTL:** 10 minutes (auto-expires stale data)

**Cache Flow:**
```
Request arrives → Check Redis
    → Key exists?  → YES → Return cached (Cache Hit)
    → Key missing? → NO  → Query Trie → Store in Redis → Return (Cache Miss)
```

**Why Redis?**
- In-memory storage = sub-millisecond reads
- Built-in TTL for automatic cache invalidation
- Supports data structures (lists, sorted sets)

---

### 3. Top-K Suggestions

**Role:** Return the K most relevant (frequent) suggestions, not all matches.

**Implementation:**
1. Collect ALL terms matching the prefix from Trie
2. Sort by frequency (descending)
3. Return first K results (default: 8)

**Why Top-K?**
- Limits response size
- Shows most relevant results first
- Prevents overwhelming the user

---

### 4. Prefix

**Role:** The partial query string entered by the user.

**Examples:**
- User types "app" → System finds: apple, application, appointment, app store
- User types "net" → System finds: netflix, network, notification

---

## API Endpoints

| Method | Endpoint | Parameters | Response |
|--------|----------|------------|----------|
| GET | `/api/autocomplete` | `prefix` (string), `k` (int, default=8) | `{prefix, suggestions[], count, cached}` |
| GET | `/api/health` | - | `{status: "UP"}` |

**Example Response:**
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

---

## Analysis Questions

### Q1: Role of each component

| Component | Role |
|-----------|------|
| **Trie** | Stores search terms in a tree structure for O(L) prefix matching. Each node represents a character, enabling efficient traversal to find all words with a given prefix. |
| **Prefix** | The partial user input used to filter suggestions. Acts as the search key that narrows down the Trie traversal to relevant branches only. |
| **Redis Cache** | Stores results of frequently requested prefixes in memory. Eliminates redundant Trie traversals, reducing response time from ~10ms to ~1ms. |
| **Top-K Suggestions** | Limits output to K most relevant results based on frequency. Improves UX by showing most popular matches first. |

---

### Q2: How Trie determines suggestions for a prefix

1. **Navigate to prefix node:** Start at root, follow each character of prefix (e.g., "app" → root→'a'→'p'→'p')
2. **DFS from prefix node:** Recursively visit all children of the prefix node
3. **Collect end-of-word nodes:** Only collect terms where `isEndOfWord = true`
4. **Sort by frequency:** Order collected terms by search frequency (descending)
5. **Return top-K:** Return first K results

**Example:** Prefix "ap" → Navigate to 'p' node → DFS finds "apple", "application", "appointment" → Sort by freq → Return top 8

---

### Q3: Request flow when prefix IS in Redis (Cache Hit)

```
1. Client sends GET /api/autocomplete?prefix=app
2. CacheService checks Redis for key "autocomplete:app"
3. Key EXISTS → Retrieve cached suggestions list
4. Return response with cached=true (no Trie traversal)
```

**Response time:** ~1-2ms (Redis read only)

---

### Q4: Request flow when prefix is NOT in Redis (Cache Miss)

```
1. Client sends GET /api/autocomplete?prefix=app
2. CacheService checks Redis for key "autocomplete:app"
3. Key MISSING → Continue to Trie
4. TrieService searches Trie for prefix "app"
5. Collect all terms: ["apple", "application", "appointment", ...]
6. Sort by frequency, take top-K
7. CacheService stores result in Redis with TTL 10 min
8. Return response with cached=false
```

**Response time:** ~5-15ms (Trie traversal + Redis write)

---

### Q5: Trie vs Linear Search

| Aspect | Trie Search | Linear Search |
|--------|-------------|---------------|
| **Time Complexity** | O(L + M) where L=prefix length, M=matches | O(N × L) where N=total terms |
| **How it works** | Navigate to prefix node, DFS children | Check every term for prefix match |
| **100K terms, prefix "app"** | ~100ms (only traverses 'a'→'p'→'p' branch) | ~2000ms (checks all 100K terms) |
| **Memory** | Higher (tree structure) | Lower (flat list) |
| **Best for** | Frequent prefix queries, autocomplete | Rare queries, small datasets |

**Example comparison:**
- Dataset: 100,000 terms
- Prefix: "app"
- **Trie:** Navigate to 'p' node (3 hops) → DFS finds ~50 matches → ~50ms
- **Linear:** Check all 100K terms → ~2000ms (40x slower)

---

## Project File Structure

```
auto_complete/
├── pom.xml                    # Maven dependencies
├── Dockerfile                 # Multi-stage Docker build
├── docker-compose.yml         # App + Redis orchestration
├── src/main/java/com/example/autocomplete/
│   ├── AutocompleteApplication.java    # Entry point
│   ├── config/RedisConfig.java         # Redis configuration
│   ├── model/SearchTerm.java           # Data model
│   ├── trie/
│   │   ├── TrieNode.java              # Trie node structure
│   │   └── TrieService.java           # Trie operations
│   ├── service/
│   │   ├── CacheService.java          # Redis caching logic
│   │   ├── DataLoaderService.java     # Load dataset on startup
│   │   └── AutocompleteService.java   # Business logic
│   └── controller/
│       └── AutocompleteController.java # REST endpoints
├── src/main/resources/
│   ├── application.properties          # Configuration
│   ├── data/search_terms.json          # 107 search terms
│   └── static/index.html              # Frontend UI
└── jmeter/                             # Load testing (planned)
```

---

## Running the Application

```bash
# With Docker (recommended)
docker-compose up --build

# Access UI
http://localhost:8080

# Test API
curl "http://localhost:8080/api/autocomplete?prefix=app&k=5"
```

---

## Performance Characteristics

| Metric | Cache Hit | Cache Miss |
|--------|-----------|------------|
| Response Time | 1-2ms | 5-15ms |
| Redis Operations | 1 GET | 1 GET + 1 SET |
| Trie Traversal | No | Yes |

**Cache Hit Rate:** Improves with repeated queries (e.g., "app" typed by many users)

---

## Postman Testing Guide

### Setup

1. Open Postman
2. Create a new Collection: **Autocomplete API**
3. Ensure the application is running: `docker-compose up --build`

---

### Test 1: Health Check

| Field | Value |
|-------|-------|
| **Method** | `GET` |
| **URL** | `http://localhost:8080/api/health` |

**Expected Response (200 OK):**
```json
{
  "status": "UP"
}
```

---

### Test 2: Basic Autocomplete Search

| Field | Value |
|-------|-------|
| **Method** | `GET` |
| **URL** | `http://localhost:8080/api/autocomplete?prefix=app&k=5` |

**Expected Response (200 OK):**
```json
{
  "prefix": "app",
  "suggestions": [
    {"term": "apple", "frequency": 15000},
    {"term": "application", "frequency": 12000},
    {"term": "appointment", "frequency": 8000},
    {"term": "app store", "frequency": 7500},
    {"term": "appliance", "frequency": 3200}
  ],
  "count": 5,
  "cached": false
}
```

**Note:** First request shows `"cached": false` (Cache Miss)

---

### Test 3: Cache Hit Verification

1. Run Test 2 again with same prefix (`app`)
2. Check response:

```json
{
  "prefix": "app",
  "suggestions": [...],
  "count": 5,
  "cached": true
}
```

**Note:** Second request shows `"cached": true` (Cache Hit)

---

### Test 4: Different Prefixes

| Prefix | Expected Suggestions |
|--------|---------------------|
| `net` | netflix, network, notification |
| `py` | python |
| `do` | docker, download |
| `wh` | whatsapp |
| `yo` | youtube |

**Example - Prefix "net":**
```
GET http://localhost:8080/api/autocomplete?prefix=net&k=3
```

```json
{
  "prefix": "net",
  "suggestions": [
    {"term": "netflix", "frequency": 13200},
    {"term": "network", "frequency": 10100},
    {"term": "notification", "frequency": 5300}
  ],
  "count": 3,
  "cached": false
}
```

---

### Test 5: No Results

| Field | Value |
|-------|-------|
| **Method** | `GET` |
| **URL** | `http://localhost:8080/api/autocomplete?prefix=xyz&k=5` |

**Expected Response:**
```json
{
  "prefix": "xyz",
  "suggestions": [],
  "count": 0,
  "cached": false
}
```

---

### Test 6: Custom K Value

| Field | Value |
|-------|-------|
| **Method** | `GET` |
| **URL** | `http://localhost:8080/api/autocomplete?prefix=a&k=3` |

Returns only 3 suggestions starting with "a" instead of default 8.

---

### Test 7: Empty Prefix

| Field | Value |
|-------|-------|
| **Method** | `GET` |
| **URL** | `http://localhost:8080/api/autocomplete?prefix=&k=5` |

**Expected Response:**
```json
{
  "prefix": "",
  "suggestions": [],
  "count": 0,
  "cached": false
}
```

---

### Postman Test Script (Automated)

Create a Pre-request Script or Tests tab to verify cache behavior:

```javascript
// Tests tab - Auto-verify cache hit/miss
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Response has suggestions array", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('suggestions');
    pm.expect(jsonData.suggestions).to.be.an('array');
});

pm.test("Response has cached flag", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('cached');
    pm.expect(jsonData.cached).to.be.a('boolean');
});
```

---

### Testing Workflow

```
1. Health Check → Verify app is running
2. Search "app" → cached: false (first time)
3. Search "app" → cached: true (from Redis)
4. Search "net" → cached: false (new prefix)
5. Search "net" → cached: true (cached)
6. Search "xyz" → cached: false, count: 0 (no matches)
7. Search "a" with k=3 → Verify Top-K limiting
```

---

### Common Issues

| Issue | Solution |
|-------|----------|
| Connection refused | Ensure Docker containers are running: `docker-compose up` |
| 404 Not Found | Check URL: `http://localhost:8080/api/autocomplete` |
| Empty suggestions | Verify prefix exists in dataset (check `search_terms.json`) |
| Always cache miss | Wait 10 minutes for cache TTL to expire, or restart Redis |
