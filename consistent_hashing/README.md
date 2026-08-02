# Consistent Hashing - Distributed Student Store

A Spring Boot application that implements **application-level Consistent Hashing**
to distribute `Student` records (College.Student: rollNo, name, dept, year)
across multiple **independent** MongoDB instances deployed with Docker Compose.

This is the consistent-hashing counterpart to `db_sharding/` (which uses MongoDB's
native sharded cluster). Here the **application** owns the hash ring and routing
logic; each MongoDB instance is a standalone `mongod` with no config server and no
`mongos`.

## Quick Start

```bash
# 1. Start the three production MongoDB nodes (+ a 4th pre-provisioned for the demo)
./setup.sh

# 2. Build & run the Spring Boot app (in another terminal)
cd consistent_hashing        # from the repo root
mvn spring-boot:run

# 3. Open the UI
#    http://localhost:8080/
#    Swagger: http://localhost:8080/swagger-ui.html
```

The `StudentSeeder` CommandLineRunner auto-inserts 20 students (RollNo 1001-1020)
on first launch; each is routed to its owning node by hashing the roll number onto
the virtual-node hash ring.

## Architecture

```
                    Spring Boot App
   ┌──────────────────────────────────────────────┐
   │  ConsistentHashRing  (TreeMap<Long,Node>)    │
   │  150 virtual nodes per physical node         │
   │  SHA-256(rollNo) -> ring position -> owner   │
   └─────────────┬────────────────────────────────┘
                 │ routes each Student to its owner
        ┌────────┴────────┬───────────┬──────────┐
        ▼                 ▼           ▼          ▼
  mongo1:27017     mongo2:27018   mongo3:27019   mongo4:27020 (offline ring)
   (active)         (active)        (active)        (added at runtime)
   College.Student  College.Student  College.Student  College.Student
```

## Components (application layer)

| Component | Role |
|-----------|------|
| `HashFunction` | SHA-256 of a key → 64-bit long (ring position) |
| `StorageNode` | A physical mongod (host:port) |
| `ConsistentHashRing` | Sorted ring of virtual nodes; `getNode(key)` O(log n) lookup |
| `MongoClientProvider` | Lazy per-node `MongoClient` cache |
| `MongoStudentStore` | `StudentStore` impl: per-node CRUD on `College.Student` |
| `StudentService` | Routes create/get/delete through the ring |
| `NodeService` | Adds/removes nodes on the ring; migrates only affected records |
| `DistributionService` | Counts records & vnodes per node (before/after diff) |

## Components (Docker layer)

| Service | Role | Host Port |
|---------|------|-----------|
| mongo1 | Storage node 1 | 27017 |
| mongo2 | Storage node 2 | 27018 |
| mongo3 | Storage node 3 | 27019 |
| mongo4 | Storage node 4 (pre-provisioned) | 27020 |
| mongo-express-* | GUI per node | 8081-8084 |
| app | Spring Boot (optional, `docker compose up --build`) | 8080 |

All mongod containers are **standalone** (no replica set, no config server, no
mongos). See `docker-compose.yml`.

## REST API

### Students
```
POST   /api/students            { rollNo, name, dept, year }  -> 201
GET    /api/students            list all
GET    /api/students/{rollNo}   -> 200 | 404
DELETE /api/students/{rollNo}   -> 204 | 404
```

### Storage nodes (hash ring)
```
GET    /api/nodes               list registered nodes
GET    /api/nodes/ring          virtual-node counts per node
POST   /api/nodes               { host, port }  -> registers + migrates
DELETE /api/nodes/{host}/{port} -> removes + redistributes
```

### Distribution
```
GET /api/distribution          counts, vnode counts, total
GET /api/distribution/counts   node -> record count
```

## Demo: Add a storage node

```bash
# 1. Before adding
curl -s localhost:8080/api/distribution | jq '{total, counts}'

# 2. Add mongo4 (already running on 27020; only its hash-ring placement is new)
curl -s -X POST localhost:8080/api/nodes \
  -H 'Content-Type: application/json' \
  -d '{"host":"mongo4","port":27020}' | jq '{migrated, before, after}'

# 3. Verify only a subset migrated; total unchanged
curl -s localhost:8080/api/distribution | jq '{total, counts}'
```

Expected: with 3 nodes, only ~25% of the 20 seed records move to `mongo4`.

## Demo: Remove a storage node

```bash
curl -s -X DELETE localhost:8080/api/nodes/mongo4/27020 | jq '{migrated, before, after}'
curl -s localhost:8080/api/distribution | jq '{total, counts}'
```

Expected: all records formerly on `mongo4` are redistributed to the remaining 3
nodes; total unchanged; no data loss.

## How routing works

1. `StudentService.createStudent` hashes `rollNo` (SHA-256 → long).
2. `ConsistentHashRing.getNode(rollNo)` finds the first virtual node clockwise on
   the ring (`ceilingKey`, wrapping to the start) → the owning `StorageNode`.
3. The record is written to `College.Student` on that node only.
4. Reads go to the same deterministic owner.

## Configuration (`application.properties`)

| Property | Default | Description |
|----------|---------|-------------|
| `app.nodes` | `localhost:27017,localhost:27018,localhost:27019` | Initial ring nodes |
| `app.virtual-nodes` | `150` | Virtual nodes per physical node |

To run the app **inside** Docker (uses service names): set
`APP_NODES=mongo1:27017,mongo2:27017,mongo3:27017` (see the `app` service).

## Tests

```bash
mvn test            # 21 unit tests (no Docker required)
```

Unit tests cover: hash-ring determinism/add-remove migration/even distribution,
student mapping, in-memory store, student + node + distribution services.

## Project structure

```
consistent_hashing/
├── docker-compose.yml
├── Dockerfile
├── setup.sh
├── pom.xml
├── scripts/seed-data.js
├── src/main/java/com/consistenthashing/
│   ├── ConsistentHashingApplication.java
│   ├── config/            (ConsistentHashingConfig, StudentSeeder)
│   ├── consistenthash/    (HashFunction, StorageNode, ConsistentHashRing)
│   ├── storage/           (StudentStore, MongoStudentStore, MongoClientProvider, StudentMapper)
│   ├── model/Student.java
│   ├── dto/               (StudentDto, CreateStudentRequest, CreateNodeRequest, DistributionReport)
│   ├── service/           (StudentService, NodeService, DistributionService)
│   ├── controller/        (StudentController, NodeController, DistributionController, RootController)
│   └── exception/         (GlobalExceptionHandler, StudentNotFoundException)
├── src/main/resources/{application.properties,static/index.html}
├── src/test/...
└── docs/analysis.md   (lab analysis write-up)
```

## Analysis

See [`docs/analysis.md`](docs/analysis.md) for the written answers covering the
hash ring, virtual nodes, routing, node add/remove, modulo vs consistent hashing,
and application-level consistent hashing vs MongoDB horizontal sharding.
