# Analysis — Consistent Hashing Sharding (Demo Lab)

Observed with 10,000 students (RollNo 1–10,000), SHA-256 → 256-bit ring positions,
100 virtual nodes per physical node, 3 base MongoDB instances.

## 1. Role of the Hash Ring, Virtual Nodes, and the MongoDB Storage Node

**Hash Ring:** a logical circle on which every possible hash value (SHA-256 → integer)
has a position. Physical nodes place one or more points on the circle; a key is assigned
to the first node encountered walking clockwise from the key's own hash position. It is
the data structure that answers "which node owns record X?" in O(log n).

**Virtual Nodes:** each physical node is placed on the ring not once but V times
(here 100), at distinct hash positions (`node1#0 .. node1#99`). Without them, a node's
single point only owns the small arc after it, so nodes with few neighboring points get
very little data — poor balance. With 100 evenly-ish hashed points per node, each node
owns roughly `1/N` of the arcs and data distribution approaches uniform (see the
31.58–35.58% spread in the README).

**MongoDB Storage Node:** one independent mongod instance holding the subset of
`Student` documents assigned to it by the ring. It is a dumb data store — it has no
knowledge of the ring, of other nodes, or of routing. All intelligence lives in the
Spring Boot application, which owns one `MongoTemplate` (connection) per node.

## 2. How Consistent Hashing Determines the Storage Location of a Student

`location(RollNo) = ring.findNode(SHA-256("student:" + RollNo))`:

1. Hash the key `student:<RollNo>` to a 256-bit integer — the key's position on the ring.
2. Walk clockwise from that position to the first virtual-node point encountered
   (`TreeMap.tailMap(key)`; wrap around to the first entry if none).
3. That virtual point belongs to exactly one physical node → the record is written to /
   read from that node's MongoDB instance.

Because the same deterministic function is used on every insert and lookup, the same
record always maps to the same node — no metadata server or central index required.

## 3. What Happens When a New MongoDB Storage Node Is Added

1. The new node is started as a standalone mongod and registered on the ring
   (`addNode` places its 100 virtual points at fresh positions).
2. Only keys whose ring position now falls in an arc owned by the new node change owner.
3. The app scans existing records, recomputes the owner for each, and moves only those
   whose owner changed (copy to new owner, delete from old) — here **2,743 of 10,000
   (27.4%)**, close to the theoretical `1/(N+1) = 25%`.
4. Other nodes are untouched; the service keeps serving during migration.

## 4. What Happens When an Existing MongoDB Storage Node Is Removed

1. The node is removed from the ring; its 100 virtual points disappear.
2. Only the arcs previously owned by the removed node get reassigned to the next
   clockwise node(s) — here all **2,248 records on node1** were the only ones affected.
3. The app drains the removed node's collection, re-inserts each record into its new
   owner, and deletes it from the removed node (verified: node1 ended with 0 documents).
4. No other record in the system is touched. The physical container can then be
   stopped/removed without any further cleanup.

## 5. Modulo Hashing vs Consistent Hashing

| Aspect | Modulo hashing (`node = hash(key) % N`) | Consistent hashing |
|--------|------------------------------------------|--------------------|
| Ring rebuild on node change | Whole mapping changes | Only arcs adjacent to the change |
| Records migrated on add/remove | ~`N/(N+1)` of all records (7,500 of 10,000 here) | ~`1/(N+1)` on add (2,743 here), `1/N` on remove (2,248 here) |
| Hit on a dead node | Service must pick a fallback explicitly | Removal is a local ring edit; remaining nodes take over |
| Balance | Perfect if N divides hash space evenly | Approximate; improved by virtual nodes |
| Simplicity | Trivial to implement | More code (ring, vnodes, migration) |

## 6. Application-Level Consistent Hashing vs MongoDB Horizontal Sharding

| Aspect | App-level consistent hashing (this lab) | MongoDB horizontal sharding (`../db_sharding`) |
|--------|-----------------------------------------|------------------------------------------------|
| Where routing lives | Spring Boot `HashRing` — MongoDB is plain standalone nodes | mongos router + config server (chunk metadata) |
| Shard key semantics | Position determined by hash at app layer | Chunk ranges over the shard key, managed by the balancer |
| Node add/remove | App-side migration, you control when/how | Balancer auto-migrates chunks; mongos `addShard`/`removeShard` |
| Query visibility | App must know the ring; no cross-node queries | mongos gives a single logical view; scatter-gather for non-shard-key queries |
| Scaling unit | Independent mongod (no cluster coordination) | Replica sets per shard, coordinated cluster |
| Balance | Approximate, tuned by virtual node count | Exact chunk splitting (default 64 MB chunks) |
