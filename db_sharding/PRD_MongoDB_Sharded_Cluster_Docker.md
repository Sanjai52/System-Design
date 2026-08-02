# PRD - MongoDB Sharded Cluster using Docker

## Goal

Implement a MongoDB sharded cluster using Docker Compose for the System
Design Lab.

## Architecture

``` text
                 Client (mongosh)
                        |
                        v
                +---------------+
                |    mongos     |
                +---------------+
                        |
          +-------------+-------------+
          |                           |
          v                           v
   +---------------+          +---------------+
   | Config Server |          |    Shard 1    |
   |   (mongod)    |          |   (mongod)    |
   +---------------+          +---------------+
                                      |
                                      v
                               +---------------+
                               |    Shard 2    |
                               |   (mongod)    |
                               +---------------+
```

## Components

-   **Config Server**: Stores cluster metadata (chunk-to-shard mapping).
    It does **not** store application data.
-   **Shard Servers**: Independent `mongod` instances storing subsets of
    the Student collection.
-   **mongos**: Query router. Applications and `mongosh` connect here.
-   **mongosh**: CLI client used to administer and query the cluster.

## Networking

All containers are attached to the same Docker network.

Example service names:

-   configsvr
-   shard1
-   shard2
-   mongos

Docker DNS allows containers to communicate by service name
(e.g. `shard1:27017`).

## Project Structure

``` text
project/
├── docker-compose.yml
├── config/
├── shard1/
├── shard2/
├── scripts/
│   ├── init-config.js
│   ├── init-shards.js
│   └── shard-cluster.js
└── README.md
```

## Implementation Plan

### Phase 1

-   Install Docker and Docker Compose.
-   Create Docker network.

### Phase 2

Create containers for: - Config Server - Shard1 - Shard2 - mongos

### Phase 3

Initialize replica sets if required.

### Phase 4

Start `mongos` and connect it to the Config Server.

### Phase 5

Connect using:

``` bash
mongosh mongodb://mongos:27017
```

### Phase 6

Configure sharding:

``` javascript
sh.addShard("shard1rs/shard1:27017")
sh.addShard("shard2rs/shard2:27017")

sh.enableSharding("College")

sh.shardCollection(
  "College.Student",
  { RollNo: 1 }
)
```

(Hashed shard key may also be used if required.)

### Phase 7

Insert sample student documents.

### Phase 8

Verify:

``` javascript
sh.status()
db.Student.getShardDistribution()
```

## Data Flow

1.  Client issues query via `mongosh`.
2.  Query reaches `mongos`.
3.  `mongos` consults Config Server metadata.
4.  `mongos` routes request to the correct shard.
5.  Shard executes request and returns result.

## Deliverables

-   docker-compose.yml
-   Initialization scripts
-   Running sharded cluster
-   Sample Student dataset
-   Verification screenshots
-   Analysis report

## Stretch Goals

-   Add a third shard.
-   Use hashed sharding.
-   Demonstrate automatic balancing.
-   Show query routing with `explain()`.
