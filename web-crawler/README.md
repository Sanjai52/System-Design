# Web Crawler - UCS3513 System Design Laboratory

## Lab Exercise 8: Design and Develop a Web Crawler for Automated Web Content Discovery

---

## 1. Project Objective

To implement a web crawler using Spring Boot for automated discovery and retrieval of web content, manage the set of URLs to be crawled efficiently, avoid repeated crawling of the same pages, and analyze the performance of the crawler under different workloads.

---

## 2. Architecture

```
                    POST /api/crawl
                         │
                         ▼
               ┌──────────────────┐
               │  CrawlController │  REST API layer
               └────────┬─────────┘
                        │
                        ▼
               ┌──────────────────┐
               │   CrawlService   │  Orchestrator
               └──┬─────┬─────┬──┘
                  │     │     │
     ┌────────────┘     │     └────────────┐
     ▼                  ▼                  ▼
┌─────────────┐  ┌──────────────┐  ┌──────────────────┐
│QueueService │  │PageFetcher   │  │UrlDiscoveryService│
│(FIFO Queue) │  │Service       │  │(Extract+Validate) │
└──────┬──────┘  └──────┬───────┘  └────────┬─────────┘
       │                │                   │
       │                ▼                   │
       │        ┌──────────────┐            │
       │        │  Jsoup HTML  │            │
       │        │   Parser     │            │
       │        └──────────────┘            │
       │                                    │
       ▼                                    ▼
┌─────────────────────────────────────────────────────┐
│              CrawlStateRepository                   │
│         Redis abstraction layer                     │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────┐  │
│  │ Visited Set │  │  Crawl Hash  │  │Job Status │  │
│  │ SADD (O(1)) │  │ per-URL data │  │  Hash     │  │
│  └─────────────┘  └──────────────┘  └───────────┘  │
└──────────────────────┬──────────────────────────────┘
                       ▼
                ┌────────────┐
                │   Redis    │
                │  (Docker)  │
                └────────────┘
```

---

## 3. Technologies

| Technology | Purpose |
|---|---|
| Java 17 | Runtime (LTS) |
| Spring Boot 3.2 | Application framework |
| Spring Data Redis | Redis integration via Lettuce |
| Jsoup 1.17 | HTML parsing and link extraction |
| Redis 7 | In-memory store for visited set + crawl state |
| Maven | Build tool |
| Docker + Docker Compose | Containerized deployment |
| JMeter | Performance/load testing |
| Postman | API testing |

---

## 4. Project Structure

```
web-crawler/
├── src/main/java/com/crawler/
│   ├── CrawlerApplication.java          # Entry point
│   ├── controller/
│   │   └── CrawlController.java         # REST endpoints
│   ├── service/
│   │   ├── CrawlService.java            # Orchestration
│   │   ├── QueueService.java            # FIFO URL queue
│   │   ├── PageFetcherService.java      # HTTP fetch via Jsoup
│   │   └── UrlDiscoveryService.java     # Link extraction + validation
│   ├── worker/
│   │   └── CrawlWorker.java             # Crawl loop logic
│   ├── repository/
│   │   └── CrawlStateRepository.java    # Redis abstraction
│   ├── model/
│   │   ├── CrawlRequest.java            # API request DTO
│   │   ├── CrawlResponse.java           # API response DTO
│   │   ├── CrawlJob.java                # Job metadata
│   │   ├── CrawlStatus.java             # Status enum
│   │   └── UrlResult.java               # Per-URL result
│   ├── config/
│   │   ├── RedisConfig.java             # Redis connection
│   │   └── CrawlerConfig.java           # Crawler parameters
│   └── exception/
│       ├── InvalidUrlException.java
│       └── CrawlFailedException.java
├── src/main/resources/
│   ├── application.yml
│   └── static/
│       └── index.html                   # Web UI
├── sample-pages/                        # Test dataset
│   ├── page1.html → page2, page3
│   ├── page2.html → page3, page4
│   ├── page3.html → page1 (cycle)
│   └── page4.html (leaf)
├── jmeter/
│   └── crawler_test_plan.jmx
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── README.md
```

---

## 5. How to Run Locally

### Prerequisites
- Java 17+
- Maven 3.8+
- Redis running on localhost:6379

### Steps
```bash
# Start Redis (if not using Docker)
redis-server

# Build and run
cd web-crawler
mvn spring-boot:run

# Access UI
open http://localhost:8080
```

---

## 6. How to Run with Docker

```bash
cd web-crawler

# Build and start both app + Redis
docker compose up --build

# Access UI
open http://localhost:8080

# View logs
docker compose logs -f

# Stop
docker compose down
```

---

## 7. Redis Configuration

| Key | Type | Purpose |
|---|---|---|
| `crawl:visited:{jobId}` | SET | All visited URLs (atomic SADD for duplicate detection) |
| `crawl:job:{jobId}` | HASH | Job metadata (seedUrl, status, pagesCrawled, etc.) |
| `crawl:result:{jobId}:{urlHash}` | HASH | Per-URL crawl result (status, discoveredLinks, error) |

**Why Redis Set for visited URLs?**
- `SADD` is atomic — returns 1 if new, 0 if exists
- O(1) membership check
- Thread-safe for concurrent workers
- Shared across multiple instances if scaled

---

## 8. API Endpoints

### POST /api/crawl — Start a crawl job

**Request:**
```json
{
  "url": "https://example.com",
  "maxPages": 50
}
```

**Response (202 Accepted):**
```json
{
  "jobId": "abc-123-def",
  "status": "QUEUED",
  "message": "Crawl job started successfully"
}
```

### GET /api/crawl/{jobId} — Get job status

**Response (200 OK):**
```json
{
  "jobId": "abc-123-def",
  "seedUrl": "https://example.com",
  "maxPages": 50,
  "pagesCrawled": 25,
  "urlsDiscovered": 120,
  "status": "CRAWLING"
}
```

### GET /api/crawl/{jobId}/urls — Get crawled URL results

**Response (200 OK):**
```json
[
  {
    "url": "https://example.com",
    "status": "COMPLETED",
    "discoveredLinks": 15,
    "timestamp": "2026-09-11T10:30:00"
  }
]
```

### DELETE /api/crawl/{jobId} — Stop a crawl job

**Response (200 OK):**
```json
{
  "message": "Job stopped",
  "jobId": "abc-123-def"
}
```

---

## 9. Postman Testing

### Test 1: Valid seed URL
```
POST http://localhost:8080/api/crawl
Body: {"url": "https://example.com", "maxPages": 10}
Expected: 202 with jobId
```

### Test 2: Invalid URL
```
POST http://localhost:8080/api/crawl
Body: {"url": "not-a-url", "maxPages": 10}
Expected: 400 with error message
```

### Test 3: Empty URL
```
POST http://localhost:8080/api/crawl
Body: {"url": "", "maxPages": 10}
Expected: 400 with error message
```

### Test 4: Get job status
```
GET http://localhost:8080/api/crawl/{jobId}
Expected: 200 with status information
```

### Test 5: Get crawled URLs
```
GET http://localhost:8080/api/crawl/{jobId}/urls
Expected: 200 with list of UrlResult objects
```

### Test 6: Stop a job
```
DELETE http://localhost:8080/api/crawl/{jobId}
Expected: 200 with confirmation
```

### Test 7: Non-existent job
```
GET http://localhost:8080/api/crawl/nonexistent-id
Expected: 404 Not Found
```

---

## 10. JMeter Testing

### Test Plan Configuration

| Thread Group | Threads | Ramp-Up | Purpose |
|---|---|---|---|
| T1 | 1 | 0s | Baseline |
| T2 | 5 | 2s | Low concurrency |
| T3 | 10 | 3s | Medium concurrency |
| T4 | 25 | 5s | High concurrency |
| T5 | 50 | 5s | Stress test |

### How to Run
1. Open Apache JMeter
2. File → Open → `jmeter/crawler_test_plan.jmx`
3. Update `${HOST}` and `${PORT}` variables if needed
4. Run each thread group separately (disable others)
5. View results in Summary Report and Aggregate Report

### Metrics to Record

| Metric | T1 (1) | T2 (5) | T3 (10) | T4 (25) | T5 (50) |
|---|---|---|---|---|---|
| Avg Response Time (ms) | | | | | |
| Throughput (req/sec) | | | | | |
| Error % | | | | | |
| 95th Percentile (ms) | | | | | |
| Min Response Time (ms) | | | | | |
| Max Response Time (ms) | | | | | |

---

## 11. Performance Analysis

### Expected Behavior

- **Low concurrency (1-5):** Response time stays low (~10-50ms for POST /api/crawl) since it only creates a job and returns immediately.
- **Medium concurrency (10-25):** Slight increase due to Redis connection pooling and thread scheduling.
- **High concurrency (50+):** Response time may increase due to Redis connection saturation and thread pool exhaustion.

### Bottlenecks
1. **Redis connection pool** — Limited Lettuce connections (default 8)
2. **Network I/O** — External URL fetching is the real bottleneck
3. **Thread pool** — Each crawl job uses a dedicated thread

### Why Performance Stays Reasonable
- The REST API endpoint only creates a job and returns immediately (non-blocking)
- Actual crawling happens in background threads
- Redis SADD/HSET operations are O(1) and sub-millisecond
- The bottleneck is external page fetching, not internal processing

---

## 12. Limitations

1. **Single-instance Redis** — No clustering or replication
2. **In-memory queue** — Queue is lost if application crashes mid-crawl
3. **No rate limiting** — Crawler may overwhelm target servers
4. **No robots.txt respect** — Does not check robots.txt
5. **No content storage** — Only stores metadata, not page content
6. **No depth tracking** — Max depth is configured but not enforced per-branch
7. **Single-threaded worker** — Each job uses one thread (configurable but not implemented as pool)

---

## 13. Possible Future Improvements

1. **Distributed crawling** — Multiple worker instances with Redis-backed queue
2. **robots.txt compliance** — Respect crawl-delay and disallowed paths
3. **Content storage** — Store page content in database or object storage
4. **Rate limiting** — Configurable delay between requests
5. **Priority queue** — Crawl important pages first
6. **Webhook callbacks** — Notify when crawl completes
7. **Crawl scheduling** — Cron-based recurring crawls
8. **Proxy support** — Route requests through proxies
9. **JavaScript rendering** — Use headless browser for SPA content
10. **Metrics dashboard** — Prometheus + Grafana monitoring

---

## Analysis Questions (from Assignment)

### 1. How does the crawler discover new URLs?
The crawler fetches a page using Jsoup, parses the HTML, and extracts all `<a href="...">` links using CSS selectors. Relative URLs are resolved against the base URL. Each discovered URL is checked against the Redis visited set — if new, it's added to the FIFO queue.

### 2. How does the system determine whether a newly discovered URL should be crawled?
A URL is crawled only if: (a) it passes URL validation (valid syntax, HTTP/HTTPS protocol), (b) it is not already in the Redis visited set (SADD returns 1), and (c) the crawl page limit hasn't been reached.

### 3. How does the system process a URL that has not been visited?
The URL is marked as visited via `SADD` (atomic add to Redis set), added to the FIFO queue, and processed by the next available worker iteration which fetches the page and extracts links.

### 4. How does the system process a URL that has already been visited?
The `SADD` operation returns 0 (element already exists), so the URL is simply skipped — not added to the queue, not fetched again.

### 5. Queue-based crawling vs repeatedly scanning the complete collection?
Queue-based (BFS) crawls URLs in discovery order — O(1) dequeue. Repeatedly scanning the full URL collection would be O(n) per iteration, redundant, and unable to determine crawl order. Queue ensures each URL is processed exactly once in FIFO order.

### 6. Performance under different concurrent requests?
As concurrency increases, the REST API response time increases slightly due to thread scheduling and Redis connection contention. However, since the API only creates jobs (non-blocking), the impact is minimal. The real performance impact is on crawl throughput — more concurrent jobs compete for Redis connections and network bandwidth for external page fetching.
