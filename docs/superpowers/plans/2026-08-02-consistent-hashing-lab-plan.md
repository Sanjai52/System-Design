# Consistent Hashing Lab - Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `subagent-driven-development` (recommended) or `executing-plans` to implement this plan task-by-task. Steps use checkbox syntax.

**Goal:** Build a Spring Boot app that implements application-level consistent hashing to route Student records across multiple standalone MongoDB instances in Docker, with dynamic add/remove node + minimal migration, plus an analysis write-up.

**Architecture:** The Spring Boot app owns a `ConsistentHashRing` (SHA-256 keys on a `TreeMap` ring with 150 virtual nodes per physical node). It holds one lazy `MongoClient` per node and routes each `Student` by hashing its `rollNo`. Add/remove node rebuilds the ring and migrates only the keys whose owner changed. Three standalone `mongo:7.0` containers run in Docker Compose; a fourth is pre-provisioned (registered on add).

**Tech Stack:** Java 21 · Spring Boot 3.3 · Maven · `mongodb-driver-sync` (managed by Spring Boot BOM) · Lombok · springdoc-openapi · JUnit 5 · Docker Compose. No Spring Data JPA (MongoDB, not Postgres). Raw sync driver for multi-node clients.

## Global Constraints
- Java 21, Maven (mirror `URL-SHORTENER/pom.xml`).
- Package: `com.consistenthashing`.
- Virtual nodes: 150 per physical node.
- Hash key: `rollNo` (Long) serialized as String, SHA-256 → first 8 bytes as `long` (unsigned compare).
- Hash ring: `SortedMap<Long, StorageNode>` (TreeMap), ceiling-key lookup with wrap-around.
- Mongo: DB `College`, collection `Student`. Each container holds the full collection locally; app routes by hash.
- Docker: 3 active MongoDB instances (mongo1:27017, mongo2:27018, mongo3:27019); mongo4:27020 pre-provisioned for the add-node demo but not registered initially.
- No git commits forced by this plan; commit per completed task.

---

## Core API (contracts all tasks agree on)

```java
// consistenthash/HashFunction.java
public final class HashFunction {
    public long hash(String key);           // SHA-256 first 8 bytes, unsigned
    public static long toUnsigned(long v);  // for consistent comparison
}

// consistenthash/StorageNode.java
public record StorageNode(String host, int port) {
    public String key();                    // host + ":" + port
}

// consistenthash/ConsistentHashRing.java
public class ConsistentHashRing {
    public ConsistentHashRing(HashFunction hf, int virtualNodesPerNode);
    public void addNode(StorageNode n);
    public void removeNode(StorageNode n);
    public StorageNode getNode(String key);              // deterministic owner
    public StorageNode getNode(long hash);               // deterministic owner
    public List<StorageNode> getPhysicalNodes();         // distinct, sorted
    public Map<String,Integer> vnodeCounts();            // node.key -> vnode count
}

// storage/MongoClientProvider.java  (Spring @Component)
public class MongoClientProvider {
    public MongoClient get(StorageNode node);
    public MongoCollection<Document> studentCollection(StorageNode node);
}
```

---

### Task 1: Bootstrap Maven project

**Files:**
- Create `consistent_hashing/pom.xml` (deps: spring-boot-starter-web, mongodb-driver-sync, springdoc-openapi-starter-webmvc-ui, lombok, spring-boot-starter-test; java 21; mirror URL-SHORTENER style).
- Create `consistent_hashing/src/main/resources/application.properties` (server.port=8080, logging; Mongo host list via custom property `app.nodes`).
- Create `consistent_hashing/src/main/java/com/consistenthashing/ConsistentHashingApplication.java`.
- Create `consistent_hashing/src/main/resources/static/index.html` (stub placeholder).

**Interfaces:** produces build that compiles. No external interfaces yet.

- [ ] **Step 1: Write `pom.xml`** with dependencies above, `<properties><java.version>21</java.version></properties>`, spring-boot-maven-plugin.
- [ ] **Step 2: `mvn -q compile`** → passes (empty sources OK).
- [ ] **Step 3: Create `application.properties`** with `app.nodes=mongo1:27017,mongo2:27018,mongo3:27019` and logging levels.
- [ ] **Step 4: Create `ConsistentHashingApplication.java`** (standard `@SpringBootApplication`).
- [ ] **Step 5: Commit.**

---

### Task 2: Consistent hashing core (pure, no Docker)

**Files:**
- Create `consistenthash/HashFunction.java` — SHA-256, `hash(String) -> long` using first 8 bytes (`ByteBuffer.wrap(digest).getLong()`), unsigned normalization via `Long.compareUnsigned`.
- Create `consistenthash/StorageNode.java` — record `(host, port)`, `key()`.
- Create `consistenthash/ConsistentHashRing.java` — `addNode`/`removeNode` place `vn` vnodes (`hash(node.key()+"#"+i)`), `getNode` via `ceilingEntry`/`firstEntry`, thread-safe on TreeMap.

**Test: `consistenthash/ConsistentHashRingTest.java`** (unit, no Docker):
- `testDeterministicLookup`: same key → same node, 100 calls.
- `testAddNodeMovesSmallFraction`: 1000 fake rollNos; before=3 nodes, add 4th → only ~1/4 of keys change owner.
- `testRemoveNodeRedistributes`: remove a node → all its keys now belong to other nodes (none missing).
- `testEvenVnodeDistribution`: 3 nodes, 150 vnodes → each node owns ~150 (±10%) on the ring.

**Interfaces:**
- Produces: `ConsistentHashRing.getNode(String) -> StorageNode`, `addNode`/`removeNode`.
- Consumed by: Task 4 (StudentService), Task 5 (NodeService), Task 6 (DistributionService).

- [ ] **Step 1: Write `ConsistentHashRingTest`** with the 4 test methods above (TDD).
- [ ] **Step 2: `mvn test -Dtest=ConsistentHashRingTest`** → FAIL (classes missing).
- [ ] **Step 3: Implement `HashFunction`, `StorageNode`, `ConsistentHashRing`.**
- [ ] **Step 4: `mvn test -Dtest=ConsistentHashRingTest`** → PASS all 4.
- [ ] **Step 5: Commit.**

---

### Task 3: Student model + MongoDB client provider

**Files:**
- Create `model/Student.java` — Lombok `@Data @AllArgsConstructor @NoArgsConstructor @Builder`; fields `Long rollNo`, `String name`, `String dept`, `int year`.
- Create `storage/MongoClientProvider.java` — `@Component`; `ConcurrentMap<String,MongoClient>`; `get(StorageNode)` lazy-creates `MongoClients.create("mongodb://host:port")`; `studentCollection(node) -> MongoCollection<Document>`; `closeAll()`.
- Create `dto/StudentDto.java` — `@Data`, same fields (response).

**Interfaces:**
- Produces: `MongoClientProvider.studentCollection(StorageNode) -> MongoCollection<Document>`.
- Consumed by: Task 4 (StudentService), Task 5 (migration).

- [ ] **Step 1: Write `MongoClientProviderTest`** — `get(StorageNode)` returns non-null client; provider caches same client for same key (use a bogus host — no connect). (no-op connect check)
- [ ] **Step 2: `mvn test -Dtest=MongoClientProviderTest`** → FAIL.
- [ ] **Step 3: Implement `Student`, `MongoClientProvider`, `StudentDto`.**
- [ ] **Step 4: `mvn test -Dtest=MongoClientProviderTest`** → PASS.
- [ ] **Step 5: Commit.**

---

### Task 4: StudentService (routed CRUD)

**Files:**
- Create `service/StudentService.java` — `@Service @RequiredArgsConstructor`; deps `ConsistentHashRing ring`, `MongoClientProvider mongo`.
  - `Student createStudent(Long rollNo, String name, String dept, int year)` → `Document` from fields, `rollNo` as `_id`; `ring.getNode(rollNo.toString())` → insert one. Returns mapped `Student`.
  - `Student getByRollNo(Long rollNo)` → owner = `ring.getNode(rollNo)`; find `_id == rollNo` on that node; return or null.
  - `List<Student> allStudents()` → iterate `ring.getPhysicalNodes()`, collect all.
  - private `Document toDoc(Student)` / `Student fromDoc(Document)`.
- Create `dto/CreateStudentRequest.java` — `@Data @NoArgsConstructor @AllArgsConstructor`, fields + validation (`@NotNull rollNo`).

**Interfaces:**
- Produces: `StudentService.createStudent/getByRollNo/allStudents`.
- Consumed by: Task 5 (migration reads/students), Task 7 (controllers).

- [ ] **Step 1: Write `StudentServiceTest`** — build a real `ConsistentHashRing` with 3 fake nodes (no DB); assert `createStudent` resolves owner via `ring.getNode`, `getByRollNo` uses same owner. (Mock MongoClientProvider returning a fake collection or use an in-memory map — inject a `Function<StorageNode, Map<Long,Document>>`.) Keep test pure.
- [ ] **Step 2: `mvn test -Dtest=StudentServiceTest`** → FAIL.
- [ ] **Step 3: Implement `StudentService`, `CreateStudentRequest`.
- [ ] **Step 4: `mvn test -Dtest=StudentServiceTest`** → PASS.
- [ ] **Step 5: Commit.**

---

### Task 5: NodeService (add/remove + migration)

**Files:**
- Create `service/NodeService.java` — `@Service @RequiredArgsConstructor`; deps `ConsistentHashRing ring`, `MongoClientProvider mongo`.
  - `record NodeChange(String nodeKey, int migrated, int before, int after)` for reporting.
  - `NodeChange addNode(String host, int port)` → build `StorageNode`, `ring.addNode`, then **migrate**: iterate `StudentService.allStudents()`; for each student whose `ring.getNode(rollNo)` now != old owner, insert into new owner, delete from old owner; count `migrated`. Return counts via `DistributionService` snapshot.
  - `NodeChange removeNode(String host, int port)` → capture all students on that node; for each, resolve new owner (after `ring.removeNode`), insert to new owner; then drop that node's `College.Student` collection.
  - `List<StorageNode> listNodes()` → `ring.getPhysicalNodes()`.

**NOTE:** `NodeService` needs `StudentService` to list students and `DistributionService` for counts. To avoid cycle, `StudentService.allStudents()` is the source of truth; `DistributionService.counts()` reads counts only.

**Interfaces:**
- Produces: `NodeService.addNode/removeNode/listNodes`.
- Consumed by: Task 7 (`NodeController`).

- [ ] **Step 1: Write `NodeServiceTest`** — pure: ring with 3 nodes; seed an in-memory student map (no real Mongo); add a 4th node; assert migrated count > 0 and < total; assert students still retrievable from new owners; remove that node; assert its keys redistributed.
- [ ] **Step 2: `mvn test -Dtest=NodeServiceTest`** → FAIL.
- [ ] **Step 3: Implement `NodeService` (depends on Task 4 stubs present).
- [ ] **Step 4: `mvn test -Dtest=NodeServiceTest`** → PASS.
- [ ] **Step 5: Commit.**

---

### Task 6: DistributionService + DTOs

**Files:**
- Create `service/DistributionService.java` — `@Service @RequiredArgsConstructor`; dep `ConsistentHashRing ring`, `MongoClientProvider mongo`.
  - `Map<String,Integer> counts()` → for each physical node, count docs in `College.Student`.
  - `Map<String,Object> distributionReport()` → counts + total + vnode counts; keys = node keys.
- Create `dto/DistributionReport.java` — `@Data @Builder`; `Map<String,Integer> counts`, `int total`, `Map<String,Integer> vnodes`.

**Interfaces:**
- Produces: `DistributionService.distributionReport()`.
- Consumed by: Task 5 (NodeService before/after), Task 7 (`DistributionController`).

- [ ] **Step 1: Write `DistributionServiceTest`** — ring + fake node→count map; assert total = sum of counts.
- [ ] **Step 2: Run failing.
- [ ] **Step 3: Implement `DistributionService` + `DistributionReport` DTO.
- [ ] **Step 4: Run passing.
- [ ] **Step 5: Commit.**

---

### Task 7: Controllers + UI + seed runner

**Files:**
- Create `controller/StudentController.java` — `@RestController @RequestMapping("/api/students")`; `POST /` (create), `GET /{rollNo}` (get), `GET /` (list), `HEAD /ping`.
- Create `controller/NodeController.java` — `@RestController @RequestMapping("/api/nodes")`; `GET /` (list), `POST /` (add), `DELETE /{host}/{port}` (remove), `GET /ring` (vnode + owner info).
- Create `controller/DistributionController.java` — `@RestController @RequestMapping("/api/distribution")`; `GET /` (counts), `GET /report` (full report).
- Create `dto/CreateNodeRequest.java` — `@Data @NoArgsConstructor @AllArgsConstructor`, `host`, `port`.
- Rewrite `static/index.html` — minimal HTML+JS: form to add student (RollNo/Name/Dept/Year), list students, add/remove node buttons, distribution table. Mirror `URL-SHORTENER/index.html` palette.
- Create `StudentSeeder.java` — `CommandLineRunner` bean; if `College.Student` total == 0 across nodes, insert 20 students (Alice..Tina, RollNo 1001..1020, like `db_sharding/scripts/seed-data.js`) via `StudentService.createStudent`. Logs routing result.

**Interfaces:**
- Produces: REST endpoints documented in spec.
- Consumed by: integration testing (Task 9) and human/lab demo.

- [ ] **Step 1: Implement 3 controllers + DTOs + `StudentSeeder`.
- [ ] **Step 2: Implement `index.html` UI.
- [ ] **Step 3: `mvn -q compile` passes.
- [ ] **Step 4: Commit.

---

### Task 8: Docker Compose + scripts + docs

**Files:**
- Create `docker-compose.yml` — standalone `mongo:7.0` services `mongo1`(27017), `mongo2`(27018), `mongo3`(27019), `mongo4`(27020) with named volumes under `data/`; `mongo-express` per node (8081-8084) like `db_sharding`. Same `shard-net` bridge network. NOTE: no configsvr/mongos — independent instances.
- Create `scripts/seed-data.js` — informational mongosh snippet listing the 20 students (document that app auto-seeds via `StudentSeeder` instead, since no mongos routes the ring).
- Create `README.md` — architecture, quick-start (`./setup.sh` runs `docker compose up -d` then `mvn spring-boot:run`), API table, add/remove-node demo steps.
- Create `setup.sh` — `docker compose up -d` + wait for mongos.
- Add `.gitignore` entry for `data/`.

**Interfaces:**
- Produces: runnable 4 MongoDB containers.
- Consumed by: Task 9 integration.

- [ ] **Step 1: Write `docker-compose.yml` (4 mongod + 4 mongo-express).
- [ ] **Step 2: Write `README.md`, `setup.sh`, `scripts/seed-data.js`, `.gitignore`.
- [ ] **Step 3: `docker compose config` valid (no errors).
- [ ] **Step 4: Commit.

---

### Task 9: Integration verification (Docker + app)

**Files:** none new (run existing).

Steps:
- [ ] **Step 1:** `docker compose up -d` (from `consistent_hashing/`).
- [ ] **Step 2:** `mvn spring-boot:run` (app starts; `StudentSeeder` inserts 20 students across mongo1/2/3).
- [ ] **Step 3:** `curl http://localhost:8080/api/distribution` → shows non-zero counts on 3 nodes.
- [ ] **Step 4:** `curl http://localhost:8080/api/nodes` → lists 3 nodes.
- [ ] **Step 5:** `curl -X POST /api/nodes -d '{"host":"mongo4","port":27020}'` → adds 4th node, migrates only affected records.
- [ ] **Step 6:** `curl /api/distribution` → 4 nodes, total still 20.
- [ ] **Step 7:** `curl -X DELETE /api/nodes/mongo4/27020` → removes, redistributes.
- [ ] **Step 8:** `curl /api/distribution` → 3 nodes, total still 20; verify no data loss.
- [ ] **Step 9:** `mvn test` → all unit tests green.
- [ ] **Step 10:** Commit any fixes.

---

### Task 10: Analysis write-up

**Files:**
- Create `docs/analysis.md` — answers to: (1) Hash Ring, (2) Virtual Nodes, (3) MongoDB Storage Node; (4) how hash decides location; (5) adding a node; (6) removing a node; (7) modulo vs consistent hashing; (8) app-level consistent hashing vs MongoDB horizontal sharding.
- Update `README.md` reference if needed.

- [ ] **Step 1: Write `docs/analysis.md` with all answers, referencing observed distribution numbers from Task 9.**
- [ ] **Step 2: Commit.

---

## Self-Review (run after writing plan)
- Spec coverage: seed (Task 7), add (Task 5+8), remove (Task 5+8), compare (Task 5+6+9), analysis (Task 10) ✓.
- No placeholders: test code referenced, signatures concrete ✓.
- Type consistency: `StorageNode`, `MongoClientProvider.studentCollection`, `StudentService.createStudent(Long,...)` used consistently ✓.
- `NodeServiceTest` uses StudentService via ring only (no real Mongo) — injectable. ✓
