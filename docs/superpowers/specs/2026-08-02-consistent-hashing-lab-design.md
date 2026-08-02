# Design Spec — Consistent Hashing Distributed Storage Lab

## Goal

Deploy multiple **independent** MongoDB instances in Docker and implement
**application-level Consistent Hashing** in a Spring Boot app to route Student
records (`College.Student` style: RollNo, Name, Dept, Year) across storage
nodes. Support dynamic add/remove of nodes with minimal data migration, and
analyze distribution before/after topology changes.

## Architecture

This is **application-level** consistent hashing — the Spring Boot app owns the
hash ring and the routing logic, talking to each MongoDB instance as a plain
standalone `mongod`. This is deliberately different from `db_sharding/`, which
uses MongoDB's native sharded cluster (configsvr + mongos + shard SRs).

```
               Spring Boot App
               ┌─────────────────────┐
               │  ConsistentHashRing │  (TreeMap<Long, StorageNode>)
               │  virtual nodes (150)│
               └────────┬───────────┘
                        │ maps RollNo-hash → node
        ┌───────────────┼───────────────┐
        ▼               ▼               ▼
  mongod:27017      mongod:27018      mongod:27019
   (mongo1)          (mongo2)          (mongo3)
   each = single DB  "College"  collection "Student"
```

## Key decisions

| Decision | Choice |
|---|---|
| Hash key | `RollNo` as string (stable, high-cardinality, matches db_sharding) |
| Hash function | SHA-256 (reuse existing `HashService.sha256Hash` pattern) → map to ring as long |
| Virtual nodes | 150 vnodes per physical node (config, low overhead) |
| Ring storage | `SortedMap<Long, StorageNode>` (TreeMap); O(log n) lookup, easy add/remove |
| Mongo access | One `MongoClient` per node, selected by ring lookup; collection `College.Student` on each |
| Migration | On add: re-insert records whose new owner differs; on remove: stream from removed node, insert to new owner |
| Data model | Student { rollNo (Long, @Id), name, dept, year }, stored as MongoDocument |
| Conventions | Java 21, Lombok, Maven, springdoc-openapi (mirror URL-SHORTENER) |

## Components

1. `consistenthash/`
   - `HashFunction` — SHA-256 → long (reuse `HashService`).
   - `StorageNode` — host, port, mongoClientFactory ref; `toString`.
   - `ConsistentHashRing` — add/remove node + vnodes, `getNode(key)` lookup, `ringSnapshot()` for analysis.
2. `model/Student` — document with `rollNo`, `name`, `dept`, `year`.
3. `config/MongoConfig` — builds a `MongoClient` per configured node; exposes a `Map<String,MongoClient>` keyed by `host:port`. The `MongoClient` for the current node set is resolved at runtime via the ring.
4. `service/StudentService` — route by hash → write to owning collection; read by scanning all nodes (RollNo is unique, first match wins).
5. `service/NodeService` — add node (rebuild ring, migrate affected records) / remove node (move its records to new owners, then drop collection).
6. `service/DistributionService` — count students per node, before/after diff.
7. `controller/` — `StudentController`, `NodeController`, `DistributionController`, + `/ping`.
8. `static/index.html` — minimal UI to add students, view routing, trigger add/remove.
9. `scripts/seed-data.js` — seed ~20 students directly into mongos-less instances via app or mongosh.
10. `docker-compose.yml` — 3 standalone `mongo:7.0` (mongo1:27017, mongo2:27018, mongo3:27019) + mongo-express per node; port 27020 reserved for the 4th node added at runtime.
11. `docs/analysis.md` — written answers to the 6 analysis questions.

## Data flow

- **Write** `POST /api/students`: hash RollNo → ring lookup → owning node → `MongoClient` for that node → insert into `College.Student`.
- **Read** `GET /api/students/{rollNo}`: query owning node directly (deterministic from ring).
- **Add node** `POST /api/nodes {host,port}`: register vnodes on ring, for each existing student whose new owner != current, copy to new owner then delete from old; report migrated count.
- **Remove node** `DELETE /api/nodes/{host}:{port}`: for each record on that node, re-resolve owner, move to new owner; then drop the node's collection.
- **Distribution** `GET /api/distribution`: `{node: count}` per node.

## Error handling
- RollNo uniqueness is enforced per-node by Mongo. Read resolves to owning node only.
- Node down during migration → caught and reported per-record (lab scenario assumes healthy nodes).

## Testing
- Unit tests: `ConsistentHashRingTest` — add/remove changes only ~1/N of keys; `getNode` deterministic; virtual nodes spread evenly.
- `StudentServiceTest` if Docker available; else algorithm tests suffice.

## Deliverables (mapping to lab tasks)
1. Three independent MongoDB instances — `docker-compose.yml` (mongo1/2/3).
2. Spring Boot consistent hashing app — core + services + controllers.
3. Register on hash ring with virtual nodes — `ConsistentHashRing`.
4. Route by hash — `StudentService`.
5. Retrieve — `StudentController GET`.
6. Add node + update ring — `NodeService.addNode`.
7. Migrate affected only — `NodeService.migrateOnAdd`.
8. Remove node + redistribute — `NodeService.removeNode`.
9. Compare distribution — `DistributionService`.

## Out of scope
- MongoDB native sharding / replica sets / config server.
- Transactions across nodes (single-document writes are atomic per node).
