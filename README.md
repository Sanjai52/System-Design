# System Design Lab

| Project | Description |
|---------|-------------|
| [URL-SHORTENER](URL-SHORTENER) | URL shortener service — Spring Boot, PostgreSQL, Redis cache |
| [db_sharding](db_sharding) | MongoDB horizontal sharding (mongos + config server + 2 shards, range-based chunks) for Student records |
| [consistent-hashing](consistent-hashing) | Application-level Consistent Hashing sharding of Student records across standalone MongoDB instances (Docker) |

The `consistent-hashing` project is the follow-up to `db_sharding`: it replaces
mongos/range-chunk routing with an in-app hash ring (SHA-256 + 100 virtual nodes/node)
so that adding or removing a storage node migrates only the affected records.
