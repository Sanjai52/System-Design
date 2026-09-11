# System Design Lab

| Project | Description |
|---------|-------------|
| [URL-SHORTENER](URL-SHORTENER) | URL shortener service — Spring Boot, PostgreSQL, Redis cache |
| [db_sharding](db_sharding) | MongoDB horizontal sharding (mongos + config server + 2 shards, range-based chunks) for Student records |
| [consistent_hashing_demo](consistent_hashing_demo) | Consistent Hashing sharding — demo variant (by **Sanjai**): dashboard UI, docker container lifecycle, measured distribution on 10,000 records |
| [consistent_hashing_prod](consistent_hashing_prod) | Consistent Hashing sharding — prod-style variant (by **Jovan**): 21 unit tests, Dockerfile, mongo-express, analysis docs |
| [rate_limiter](rate_limiter) | API Rate Limiter — Spring Boot + Redis, Token Bucket via atomic Lua script, 429 + Retry-After, normal/burst load test |
| [auto_complete](auto_complete) | Autocomplete Search — Spring Boot + Trie + Redis cache, real-time prefix suggestions, JMeter load tests |
| [web-crawler](web-crawler) | Web Crawler — Spring Boot BFS crawler, Redis state management, Jsoup HTML parsing, JMeter load tests |
| [docs](docs) | Lab plans and design specs |

Both `consistent_hashing_*` projects replace mongos/range-chunk routing with an in-app
hash ring (SHA-256 + virtual nodes/node) so that adding or removing a storage node
migrates only the affected records. See [their READMEs](consistent_hashing_demo/README.md)
for the full lab analysis.
