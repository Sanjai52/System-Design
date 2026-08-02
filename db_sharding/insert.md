# Insert & Shard Routing Test Cheat Sheet

Connect directly to mongos (host port 27017). No docker exec needed if `mongosh` is installed locally.

## Layout rule

| Shard | RollNo range | Chunk |
|-------|--------------|-------|
| shard2 | 1 - 1000 | `[MinKey, 1001)` |
| shard1 | 1001 - 2000 | `[1001, MaxKey)` |

## Commands

```bash
# interactive shell
mongosh mongodb://localhost:27017/College

# insert one
mongosh mongodb://localhost:27017/College --eval "db.Student.insertOne({RollNo: 750, Name: 'X', Dept: 'CS', Year: 2})"

# bulk insert 10
mongosh mongodb://localhost:27017/College --eval "db.Student.insertMany(Array.from({length: 10}, (_, i) => ({RollNo: 2000 + i, Name: 'Bulk' + i, Dept: 'CS', Year: 1})))"

# where did RollNo 750 go
mongosh mongodb://localhost:27017/College --eval "db.Student.find({RollNo: 750}).explain('executionStats').queryPlanner.winningPlan.shards.forEach(s => print('RollNo 750 -> ' + s.shardName))"

# live distribution
mongosh mongodb://localhost:27017/College --eval "db.Student.getShardDistribution()"

# delete a row
mongosh mongodb://localhost:27017/College --eval "db.Student.deleteOne({RollNo: 750})"

# realtime watcher (terminal 1) - run inserts in terminal 2
while true; do clear; docker compose exec -T mongos mongosh --quiet --port 27017 --eval "db.getSiblingDB('College').Student.getShardDistribution()"; sleep 2; done
```

## Routing rule of thumb

- `RollNo <= 1000` -> shard2
- `RollNo >= 1001` -> shard1

## Cleanup test data

```bash
mongosh mongodb://localhost:27017/College --eval "db.Student.deleteMany({Name: /Bulk|^X$/})"
```
