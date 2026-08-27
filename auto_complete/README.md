# Autocomplete Search System (EXP7)

Spring Boot + **Trie** (prefix tree) + **Redis** cache for real-time search suggestions.

## Architecture

```
Client → Spring Boot REST API → Redis Cache (Hit: return | Miss: Trie → Cache → Return)
```

| Component | Role |
|-----------|------|
| **Trie** | O(L) prefix search — stores terms in tree, DFS from prefix node |
| **Redis** | In-memory cache, key `autocomplete:{prefix}`, TTL 10 min |
| **Top-K** | Returns K most frequent matches (default: 8) |

## Quick Start

```bash
# Start with Docker (recommended)
cd auto_complete
docker-compose up --build

# Access UI
http://localhost:8080

# Test API
curl "http://localhost:8080/api/autocomplete?prefix=app&k=5"
```

## API

| Method | Endpoint | Parameters | Response |
|--------|----------|------------|----------|
| GET | `/api/autocomplete` | `prefix` (string), `k` (int, default=8) | `{prefix, suggestions[], count, cached}` |
| GET | `/api/health` | — | `{status: "UP"}` |

**Example:**
```bash
curl "http://localhost:8080/api/autocomplete?prefix=app&k=5"
```
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

## Project Structure

```
auto_complete/
├── pom.xml                    # Maven (Spring Boot 3.2, Java 17)
├── Dockerfile                 # Multi-stage build
├── docker-compose.yml         # App + Redis
├── src/main/java/com/example/autocomplete/
│   ├── AutocompleteApplication.java
│   ├── config/RedisConfig.java
│   ├── model/SearchTerm.java
│   ├── trie/TrieNode.java, TrieService.java
│   ├── service/CacheService.java, DataLoaderService.java, AutocompleteService.java
│   └── controller/AutocompleteController.java
├── src/main/resources/
│   ├── application.properties
│   ├── data/search_terms.json  # 107 terms with frequencies
│   └── static/index.html       # Demo UI
└── jmeter/                     # Load test scripts
```

## Performance

| Scenario | Response Time | Operations |
|----------|---------------|------------|
| Cache Hit | ~1-2ms | 1 Redis GET |
| Cache Miss | ~5-15ms | 1 Redis GET + Trie + 1 Redis SET |

## Key Concepts

- **Trie search**: Navigate to prefix node → DFS collect words → sort by frequency → top-K
- **Cache hit flow**: Redis GET → return (no Trie)
- **Cache miss flow**: Redis GET (miss) → Trie search → Redis SET → return
- **Trie vs Linear**: O(L+M) vs O(N×L) — 40x faster at 100K terms

## Load Testing

```bash
cd jmeter
./load_test.sh
```

See `DOCUMENTATION.md` for full analysis, Postman test guide, and JMeter results.