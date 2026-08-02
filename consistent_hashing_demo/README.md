# Consistent Hashing — Distributed Student Store (Demo)

Application-level **Consistent Hashing** sharding of a `Student` collection across
three standalone MongoDB instances (Docker), routed entirely inside a Spring Boot app.
No mongos, no config server, no replica set.

Companion to [`consistent_hashing_prod`](../consistent_hashing_prod) (test-heavy
variant) and the range-based mongos lab in [`db_sharding`](../db_sharding).

## Quick Start

```bash
# 1. MongoDB storage nodes (node1-3 on 27030-27032; node4 under the "extra" profile)
cd consistent_hashing_demo
docker compose up -d

# 2. Build & run the app (requires Docker Desktop running)
cd app && mvn -DskipTests package
java -jar target/student-sharding-0.0.1-SNAPSHOT.jar

# 3. Dashboard UI  ->  http://localhost:8080/
#    Swagger        ->  http://localhost:8080/swagger-ui.html
```

Full lifecycle demo: `bash scripts/demo.sh` (seed 10,000 → distribution → add node4 →
remove node1 → final distribution).

## Architecture

```
StudentController  NodeController  DistributionController  RingController  SeedController   (HTTP layer)
        │                 │                 │                  │                │
        ▼                 ▼                 ▼                  ▼                ▼
   StudentService   MigrationService  DistributionService  RingService     SeedService      (use cases)
        │                 │                 │
        ▼                 ▼                 ▼
        └──────────► NodeRegistryService (ring sync + owner lookup + connections)
                          │
        ┌─────────────────┴─────────────────┐
        ▼                                   ▼
   domain/HashRing                   storage/MongoConnectionManager
   (pure ring, SHA-256,             (one MongoTemplate per node)
    virtual nodes)
                          │
   NodeLifecycleService  (docker start/stop + readiness ping)
                          │
        ┌─────────────────┼─────────────────┐
        ▼                 ▼                 ▼
   node1:27030      node2:27031      node3:27032      (node4:27033 for the add demo)
```

Each layer has a single responsibility:

| Layer | Class | Responsibility |
|-------|-------|----------------|
| domain | `HashRing` | Pure ring algorithm: SHA-256 → 256-bit position, 100 virtual points/node, clockwise owner lookup. No Spring/Mongo imports. |
| domain | `ShardNode` | Value object `(id, host, port)`. |
| storage | `MongoConnectionManager` | Owns `MongoClient`/`MongoTemplate` per node; connect/disconnect. Knows nothing about the ring. |
| service | `NodeRegistryService` | Coordinates ring + connections: register/unregister nodes, `ownerOf(rollNo)`, template access. |
| service | `StudentService` | Routed student CRUD (insert/read via owner). |
| service | `SeedService` | Wipe + bulk-insert N students through the ring (batched 500). |
| service | `DistributionService` | Per-node record counts + percentages. |
| service | `RingService` | Ring snapshot DTO (vnodes, point positions). |
| service | `MigrationService` | Add/remove node data migration (move only affected records). |
| service | `NodeLifecycleService` | Docker container lifecycle + mongod ping readiness. |
| controller | `StudentController` / `NodeController` / `DistributionController` / `RingController` / `SeedController` | Thin HTTP adapters, one concern each. |

## REST API

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/students` | Insert one student; response includes assigned node |
| GET | `/api/students` | Read all students (tagged with their storage node) |
| GET | `/api/students/{rollNo}` | Retrieve one student from its ring-assigned node |
| POST | `/api/nodes` | Add a node: start container, register on ring, migrate affected records |
| DELETE | `/api/nodes/{nodeId}` | Remove a node: redistribute its records, stop container |
| POST | `/api/nodes/{nodeId}/start?host=&port=` | Start a node's container + wait for readiness |
| POST | `/api/nodes/{nodeId}/stop` | Stop a node's container |
| GET | `/api/distribution` | Per-node record counts (before/after analysis) |
| GET | `/api/ring` | Ring snapshot: nodes, virtual points, point positions |
| POST | `/api/seed?count=N` | Wipe all nodes and reseed N students through the ring |

## Dashboard UI

Served at `/` (`app/src/main/resources/static/index.html`): SVG hash ring with every
virtual-node point, distribution bars with before/after deltas, one-click add/remove
shard buttons (containers managed automatically), migration history, and a RollNo
lookup that predicts the owning node via client-side SHA-256 and confirms it against
the API.

## Measured Results (10,000 records, 100 virtual nodes/node)

**Seed on 3 nodes** — near-uniform without manual chunk planning:

| Node | Records | % |
|------|--------:|---:|
| node1 | 3158 | 31.58 |
| node2 | 3558 | 35.58 |
| node3 | 3284 | 32.84 |

**Add node4** — `recordsMigrated: 2743` (27.4%, theory 25% = `1/(N+1)`); modulo
hashing would have re-hashed ~7,500:

| Node | Records | % |
|------|--------:|---:|
| node1 | 2248 | 22.48 |
| node2 | 2724 | 27.24 |
| node3 | 2285 | 22.85 |
| node4 | 2743 | 27.43 |

**Remove node1** — `recordsMigrated: 2248` (only records that lived on node1; its
collection ended at 0, then the container was stopped):

| Node | Records | % |
|------|--------:|---:|
| node2 | 3530 | 35.30 |
| node3 | 3035 | 30.35 |
| node4 | 3435 | 34.35 |

## Project Layout

```
consistent_hashing_demo/
├── docker-compose.yml        # 3 base nodes (node4 under "extra" profile)
├── scripts/demo.sh           # end-to-end lifecycle demo
├── docs/analysis.md          # lab analysis (6 questions)
└── app/                      # Spring Boot 3.3, Java 21
    ├── pom.xml
    └── src/main/java/com/studentsharding/
        ├── config/           # ShardProperties (config binding only)
        ├── domain/           # HashRing, ShardNode (pure, no framework)
        ├── storage/          # MongoConnectionManager (per-node connections)
        ├── service/          # registry, student, seed, distribution, ring, migration, lifecycle
        ├── controller/       # 5 single-purpose REST controllers
        ├── dto/  exception/  model/
        └── resources/static/index.html   # dashboard UI
```
