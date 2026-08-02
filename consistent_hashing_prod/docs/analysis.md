# Analysis — Consistent Hashing Distributed Storage Lab

Observed during the integration run (3 nodes `mongo1:27017`, `mongo2:27018`,
`mongo3:27019`; 150 virtual nodes per physical node; hash key = `rollNo` as a
string hashed with SHA-256 → 64-bit unsigned long).

Initial seed of 20 students (RollNo 1001–1020):

| Node | Students |
|------|---------:|
| localhost:27017 (mongo1) | 7 |
| localhost:27018 (mongo2) | 6 |
| localhost:27019 (mongo3) | 7 |
| **Total** | **20** |

Add node `mongo4:27020` → 6 records migrated (only the affected ones); the new
node received 6 records; total stayed 20.

| Node | Before | After add |
|------|------:|------:|
| mongo1 (27017) | 7 | 3 |
| mongo2 (27018) | 6 | 5 |
| mongo3 (27019) | 7 | 6 |
| mongo4 (27020) | 0 | 6 |
| **Total** | **20** | **20** |

Remove node `mongo4:27020` → the 6 records on it were redistributed to the
remaining nodes; total stayed 20 and the distribution reverted exactly to the
original 7 / 6 / 7 (a clean per-key inverse). All 20 students remained
retrievable throughout (no data loss).

---

## 1. Role of the components

### Hash Ring
The hash ring is an ordered, circular keyspace (here a `TreeMap<Long,
StorageNode>` keyed by the SHA-256-derived position of each virtual node, with
unsigned 64-bit comparison and wrap-around from the largest position back to the
smallest). To route a record we hash its key and walk clockwise to the first
virtual node at or after that position; the owner of that virtual node is the
storage node that should hold the record. Because the ring is ordered and the
walk is `ceilingEntry` + wrap-to-first, lookups are O(log n).

### Virtual Nodes
Each physical MongoDB node is placed on the ring multiple times (150 vnodes per
node in this lab) by hashing `nodeKey + "#" + replicaIndex`. Virtual nodes
spread a physical node's responsibility across the whole ring instead of letting
it own one contiguous arc. Without vnodes, a single node owns a large contiguous
swath of the ring and adding/removing it causes a thundering-herd of data
movement; with vnodes the load and the migration footprint are spread evenly and
each physical node owns ~1/N of the keyspace. `GET /api/nodes/ring` exposes the
vnode counts (150 per node).

### MongoDB Storage Node
A MongoDB storage node is a standalone `mongod` instance (here `mongo1..mongo4`,
each exposing `College.Student`). Unlike the `db_sharding` lab — which used a
MongoDB sharded cluster (config server + `mongos` router + replica-set shards
with MongoDB's own balancer) — here each mongod is independent. The **application**
owns routing: it computes the owner from the hash ring and connects to that
single mongod directly. MongoDB itself is an opaque key/value store for the
Student collection on each node.

## 2. How Consistent Hashing determines the storage location of a student record

1. The record's `rollNo` (e.g. `1005`) is the routing key.
2. `HashFunction.hash("1005")` = first 8 bytes of `SHA-256("1005")` as an
   unsigned 64-bit long — the record's position on the ring.
3. `ConsistentHashRing.getNode("1005")` = the virtual node with the smallest
   position `>= hash("1005")` (wrapping to the first virtual node if none is
   larger). The owning `StorageNode` is the physical node that placed that
   virtual node.
4. `StudentService` writes the record only to `College.Student` on that owning
   node. Reads go to the same deterministic owner, so no broadcast/fan-out is
   needed for a known roll number.

Because the hash is deterministic and the ring is a pure function of the virtual
nodes currently registered, the same roll number always resolves to the same
node for a given topology — verified by `GET /api/students/{rollNo}` returning the
correct record every time.

## 3. What happens when a new MongoDB storage node is added

When `POST /api/nodes { host, port: 27020 }` is called, `NodeService.addNode`:

1. Adds 150 virtual nodes for `mongo4` to the ring (`hash("mongo4:27020#0..149")`).
   Existing virtual nodes are unchanged in position.
2. It then scans every existing node's students and re-resolves each record's
   owner on the **new** ring. A record moves **only** if its new owner differs
   from where it currently lives — and because only `mongo4`'s vnodes were
   inserted, a record either stays put or moves to `mongo4` (it never jumps to
   another pre-existing node). This is the core scalability property of
   consistent hashing.
3. Moved records are copied to `mongo4` and deleted from their old node; only
   the affected records move.

In the run, adding `mongo4` moved **6 of 20** records (~30%, close to the ideal
1/N = 25% with generous spread from 150-vnode placement), and `mongo4` received
exactly those 6. Total stayed at 20.

## 4. What happens when an existing MongoDB storage node is removed

When `DELETE /api/nodes/localhost/27020` is called, `NodeService.removeNode`:

1. Captures the records currently on `mongo4` (the node about to leave).
2. Removes `mongo4`'s 150 virtual nodes from the ring.
3. For each captured record, re-resolves the owner on the **remaining** ring and
   inserts it there (redistributing only `mongo4`'s records).
4. Drops `mongo4`'s `College.Student` collection.

Only the data that lived on the removed node migrates; every other node is
untouched. In the run, the 6 records on `mongo4` were redistributed and the
distribution reverted exactly to `7 / 6 / 7` — i.e. add-then-remove is a clean
inverse at the per-record level, which is exactly the migration-minimisation
guarantee consistent hashing provides.

## 5. Modulo-based hashing vs. Consistent Hashing

| Aspect | Modulo (`hash % N`) | Consistent Hashing |
|--------|---------------------|--------------------|
| Lookup | `hash(key) % N` → bucket | hash → clockwise first vnode |
| Add node (2→3) | **Every** key is remapped (only 1/3 stays by chance); ~N/(N+1) of data moves | ~1/(N+1) of keys move (only those whose owner is the new node) |
| Remove node (3→2) | **Every** key remapped; only 1/2 stays by chance | ~1/N of keys move (only the removed node's data) |
| Balance | Perfect balance *if* hash is uniform AND N fixed | Depends on virtual nodes; more vnodes → better balance, tiny per-node variance |
| Dynamism | Resizing a cluster requires mass reshuffling (or a separate rehash) | Nodes can join/leave with minimal, localized migration |
| State | None (pure function) | A ring of virtual nodes (small in-memory `TreeMap`) |

With modulo hashing, going from 3 to 4 MongoDB nodes would force almost all
records to a different shard (`hash % 3` → `hash % 4` rarely equals), causing a
near-full data reshuffle. Consistent hashing instead relocates only the records
whose owning vnode became the new node — 6 of 20 in this lab rather than ~15.

## 6. Application-Level Consistent Hashing vs. MongoDB Horizontal Sharding

| Aspect | Application-Level Consistent Hashing (this lab) | MongoDB Horizontal Sharding |
|--------|-------------------------------------------------|-----------------------------|
| Where the ring lives | Spring Boot process in-memory | Inside mongos + config server metadata |
| Routing | App computes owner, picks the mongod | `mongos` consults config server chunk map, then routes |
| Shard key | `rollNo` (application-chosen) | Any indexed field; hashed or ranged shard key |
| Balancer / migration | Application migrates records on add/remove | MongoDB balancer migrates **chunks** automatically in the background |
| Range queries | App must fan out across nodes | mongos can target specific shards via chunk ranges |
| Data model | One independent `College.Student` per mongod | Shared namespace across shards via `mongos` |
| Config surface | `app.nodes`, `app.virtual-nodes` | `sh.addShard`, `sh.shardCollection`, zones |
| Failure model | App must handle a dead node (retry/redirect) | Replica-set shards survive node failure natively |

MongoDB's native sharding (as implemented in `db_sharding/`) is the more
production-ready option: it gives automatic balancing, replica-set fault
tolerance, a single `mongos` endpoint, and range-query pruning. Application-level
consistent hashing — as built here — trades those operational benefits for full
application control and a transparent, easy-to-reason-about routing layer that is
ideal for learning and for workloads where the app already owns the keyspace split
and wants to avoid the `mongos`/config-server machinery.

## Testability / design notes

- The routing and migration logic in `StudentService`, `NodeService`, and
  `DistributionService` depend on the `StudentStore` interface, not on MongoDB
  directly. An `InMemoryStudentStore` test double backs 18 unit tests that prove
  determinism, even-key distribution, the add/remove inverse property, and
  migration-only-affected behaviour with **no Docker required** (`mvn test`).
- `MongoStudentStore` is the thin production implementation over the sync
  `mongodb-driver-sync` client; `MongoClientProvider` caches one
  `MongoClient` per `StorageNode`.
