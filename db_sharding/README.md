# MongoDB Sharded Cluster - College Database

A MongoDB sharded cluster deployed with Docker Compose using MongoDB 7.0.

## Quick Start

```bash
chmod +x setup.sh
./setup.sh
```

## Architecture

```
mongosh -> mongos -> configsvr (metadata)
                 -> shard1   (data)
                 -> shard2   (data)
```

## Components

| Component | Role | Port |
|-----------|------|------|
| configsvr | Cluster metadata store | 27019 |
| shard1 | Student data subset | 27020 |
| shard2 | Student data subset | 27021 |
| mongos | Query router | 27017 |

## Analysis

### 1. Role of Each Component

**Config Server:** Stores cluster metadata (chunk-to-shard mapping). Does NOT store application data. mongos queries it for routing information.

**Shard Server:** Each shard holds a subset of the Student collection. Together they store the full dataset.

**mongos Router:** Query router. Clients connect here as if it were a single MongoDB instance. mongos routes queries to the correct shard based on config server metadata.

### 2. Why RollNo as Shard Key

RollNo has high cardinality (every student is unique), it aligns with primary query patterns (lookup by RollNo), and it distributes data evenly across shards.

### 3. Adding Additional Shards

When a new shard is added, the balancer automatically migrates chunks from overloaded shards to the new one. The process is transparent to clients.

### 4. Standalone vs Sharded Cluster

| Aspect | Standalone | Sharded Cluster |
|--------|-----------|-----------------|
| Storage | Single server limit | Horizontal scaling |
| Write throughput | Single node | Distributed |
| Complexity | Simple setup | More components |
| Fault tolerance | Single point of failure | Better resilience |
