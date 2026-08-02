# Consistent Hashing Sharding - Student Information System

Application-level **Consistent Hashing** sharding of a Student collection across three
independent MongoDB instances deployed with Docker. Routing is done entirely inside a
Spring Boot application — there is **no mongos, no config server, no replica set**.

Built as the follow-up to the range-based (mongos) sharding lab in [`../db_sharding`](../db_sharding).

## Architecture

```
Spring Boot App (Hash Ring, 100 virtual nodes per physical node)
    |
    +-- SHA-256(RollNo) -> ring position -> clockwise first virtual node
    |
    +-- node1 (mongodb://localhost:27030)
    +-- node2 (mongodb://localhost:27031)
    +-- node3 (mongodb://localhost:27032)
    +-- node4 (mongodb://localhost:27033, started only during the add-node demo)
```

Each storage node is an independent `mongo:7.0` container holding the full `College.Student`
collection of only the records assigned to it. The app maintains one `MongoTemplate`
per node.

## Quick Start

```bash
cd consistent-hashing/app && mvn -DskipTests package   # build the Spring Boot jar
cd consistent-hashing && ./scripts/demo.sh             # full lifecycle demo
```

`demo.sh` does: seed 10,000 students → distribution (3 nodes) → start node4 →
add node4 to the ring (migration) → remove node1 from the ring (redistribution) →
final distribution.

The application starts on `http://localhost:8080` — the dashboard UI is at
`http://localhost:8080/` (Swagger UI at `/swagger-ui.html`).

## Dashboard UI

The app serves a single-page visualizer (`app/src/main/resources/static/index.html`):

- **Hash ring** — SVG circle drawing every virtual-node point (colored per node) with
  live point counts.
- **Data distribution** — per-node bars with record counts, percentages, and
  green/red deltas against the distribution before the last add/remove.
- **+ Add Shard / − Remove Shard** — one-click buttons. The app starts/stops the
  Docker container itself (`docker start/stop ch-<node>`, falling back to
  `docker compose up -d`), waits for MongoDB to accept a real `ping`, updates the ring,
  migrates, and animates the result.
- **Migration history** — log of every add/remove with `recordsMigrated`, elapsed time,
  and before → after per-node counts.
- **Locate a Student** — computes the ring position client-side with WebCrypto
  SHA-256, draws a gold marker on the ring, predicts the clockwise owner, and confirms
  it against the actual stored node.

The add/remove buttons manage containers automatically because `NodeLifecycleService`
shells out to the Docker CLI (`sharding.container-prefix: ch-`,
`sharding.compose-file: ../docker-compose.yml`).

## REST API

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/students` | Insert one student; response includes assigned node |
| GET | `/api/students/{rollNo}` | Retrieve one student from the node chosen by the ring |
| GET | `/api/students` | Read all students (tagged with their storage node) |
| GET | `/api/admin/distribution` | Per-node record counts |
| GET | `/api/admin/ring` | Ring snapshot (physical nodes, virtual points) |
| POST | `/api/admin/nodes` | Register a new node + migrate only affected records (starts the Docker container first) |
| DELETE | `/api/admin/nodes/{nodeId}` | Remove a node + redistribute only its records (stops the container after) |
| POST | `/api/admin/nodes/{nodeId}/start?host=&port=` | Start a node's container + wait until reachable |
| POST | `/api/admin/nodes/{nodeId}/stop` | Stop a node's container |
| POST | `/api/admin/seed?count=N` | Wipe and reseed N students through the ring |

## Measured Results (10,000 records, 100 virtual nodes/node)

### Initial distribution — 3 nodes (seed through the ring)

| Node | Records | % |
|------|---------|---|
| node1 | 3158 | 31.58 |
| node2 | 3558 | 35.58 |
| node3 | 3284 | 32.84 |

Virtual nodes spread records almost evenly (ideal 33.3% each) with no manual chunk
planning — unlike the range-based lab where chunk boundaries had to be pre-decided.

### After adding node4 (ring rebuild + migration)

```
recordsMigrated: 2743  (~27.4% of 10,000; theoretical minimum for N=3 -> N=4 is 1/4 = 25%)
```

| Node | Records | % |
|------|---------|---|
| node1 | 2248 | 22.48 |
| node2 | 2724 | 27.24 |
| node3 | 2285 | 22.85 |
| node4 | 2743 | 27.43 |

Only 2,743 of 10,000 records moved. With modulo hashing, adding a node would have
required re-hashing ~7,500 records (all records whose `hash % 4 != hash % 3`).

### After removing node1 (ring rebuild + redistribution)

```
recordsMigrated: 2248  (only the records that lived on node1)
```

| Node | Records | % |
|------|---------|---|
| node2 | 3530 | 35.30 |
| node3 | 3035 | 30.35 |
| node4 | 3435 | 34.35 |

node1's 2,248 records were re-hashed onto node2/node3/node4 and node1's collection was
emptied (verified: 0 documents after migration; container then stopped).

## Analysis

### 1. Role of the Hash Ring, Virtual Nodes, and the MongoDB Storage Node

**Hash Ring:** a logical circle on which every possible hash value (SHA-256 → integer)
has a position. Physical nodes place one or more points on the circle; a key is assigned
to the first node encountered walking clockwise from the key's own hash position. It is
the data structure that answers "which node owns record X?" in O(log n).

**Virtual Nodes:** each physical node is placed on the ring not once but V times
(here 100), at distinct hash positions (`node1#0 .. node1#99`). Without them, a node's
single point only owns the small arc after it, so nodes with few neighboring points get
very little data — poor balance. With 100 evenly-ish hashed points per node, each node
owns roughly `1/N` of the arcs and data distribution approaches uniform (see the
31.58–35.58% spread above).

**MongoDB Storage Node:** one independent mongod instance holding the subset of
`Student` documents assigned to it by the ring. It is a dumb data store — it has no
knowledge of the ring, of other nodes, or of routing. All intelligence lives in the
Spring Boot application, which owns one `MongoTemplate` (connection) per node.

### 2. How Consistent Hashing Determines the Storage Location of a Student

`location(RollNo) = ring.findNode(SHA-256("student:" + RollNo))`:

1. Hash the key `student:<RollNo>` to a 256-bit integer — the key's position on the ring.
2. Walk clockwise from that position to the first virtual-node point encountered
   (`TreeMap.tailMap(key)`; wrap around to the first entry if none).
3. That virtual point belongs to exactly one physical node → the record is written to /
   read from that node's MongoDB instance.

Because the same deterministic function is used on every insert and lookup, the same
record always maps to the same node — no metadata server or central index required.

### 3. What Happens When a New MongoDB Storage Node Is Added

1. The new node is started as a standalone mongod and registered on the ring
   (`addNode` places its 100 virtual points at fresh positions).
2. Only keys whose ring position now falls in an arc owned by the new node change owner.
3. The app scans existing records, recomputes the owner for each, and moves only those
   whose owner changed (copy to new owner, delete from old) — here **2,743 of 10,000
   (27.4%)**, close to the theoretical `1/(N+1) = 25%`.
4. Other nodes are untouched; the service keeps serving during migration.

### 4. What Happens When an Existing MongoDB Storage Node Is Removed

1. The node is removed from the ring; its 100 virtual points disappear.
2. Only the arcs previously owned by the removed node get reassigned to the next
   clockwise node(s) — here all **2,248 records on node1** were the only ones affected.
3. The app drains the removed node's collection, re-inserts each record into its new
   owner, and deletes it from the removed node (verified: node1 ended with 0 documents).
4. No other record in the system is touched. The physical container can then be
   stopped/removed without any further cleanup.

### 5. Modulo Hashing vs Consistent Hashing

| Aspect | Modulo hashing (`node = hash(key) % N`) | Consistent hashing |
|--------|------------------------------------------|--------------------|
| Ring rebuild on node change | Whole mapping changes | Only arcs adjacent to the change |
| Records migrated on add/remove | ~`N/(N+1)` of all records (7,500 of 10,000 here) | ~`1/(N+1)` on add (2,743 here), `1/N` on remove (2,248 here) |
| Hit on a dead node | Service must pick a fallback explicitly | Removal is a local ring edit; remaining nodes take over |
| Balance | Perfect if N divides hash space evenly | Approximate; improved by virtual nodes |
| Simplicity | Trivial to implement | More code (ring, vnodes, migration) |

### 6. Application-Level Consistent Hashing vs MongoDB Horizontal Sharding

| Aspect | App-level consistent hashing (this lab) | MongoDB horizontal sharding (`../db_sharding`) |
|--------|-----------------------------------------|------------------------------------------------|
| Where routing lives | Spring Boot `HashRing` — MongoDB is plain standalone nodes | mongos router + config server (chunk metadata) |
| Shard key semantics | Position determined by hash at app layer | Chunk ranges over the shard key, managed by the balancer |
| Node add/remove | App-side migration, you control when/how | Balancer auto-migrates chunks; mongos `addShard`/`removeShard` |
| Query visibility | App must know the ring; no cross-node queries | mongos gives a single logical view; scatter-gather for non-shard-key queries |
| Scaling unit | Independent mongod (no cluster coordination) | Replica sets per shard, coordinated cluster |
| Balance | Approximate, tuned by virtual node count | Exact chunk splitting (default 64 MB chunks) |

## Project Layout

```
consistent-hashing/
├── docker-compose.yml          # 3 base nodes (node4 under the "extra" profile)
├── scripts/demo.sh             # end-to-end lifecycle demo
├── app/                        # Spring Boot 3.3, Java 21
│   ├── pom.xml
│   └── src/main/java/com/studentsharding/
│       ├── config/ShardProperties.java   # node registry from application.yml
│       ├── sharding/HashRing.java        # TreeMap ring + SHA-256 + virtual nodes
│       ├── sharding/ShardManager.java    # MongoTemplate per node, register/unregister
│       ├── service/StudentService.java   # routed insert/read/seed/distribution
│       ├── service/MigrationService.java # add/remove node migration
│       ├── service/NodeLifecycleService.java  # docker start/stop + ping readiness
│       ├── controller/                   # Student + Admin REST endpoints
│       ├── model/Student.java
│       └── resources/static/index.html   # dashboard UI (ring, bars, add/remove, lookup)
```
