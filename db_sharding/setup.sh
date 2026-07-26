#!/bin/bash
set -e

echo "=== Starting containers ==="
docker compose up -d

echo "=== Waiting for containers to be ready ==="
sleep 10

echo "=== Init config server replica set ==="
docker compose exec -T configsvr mongosh --quiet --port 27017 /scripts/init-config.js

echo "=== Init shard1 replica set ==="
docker compose exec -T shard1 mongosh --quiet --port 27017 /scripts/init-shard1.js

echo "=== Init shard2 replica set ==="
docker compose exec -T shard2 mongosh --quiet --port 27017 /scripts/init-shard2.js

echo "=== Waiting for replica sets to stabilize ==="
sleep 5

echo "=== Configuring sharding via mongos ==="
docker compose exec -T mongos mongosh --quiet --port 27017 /scripts/shard-cluster.js

echo "=== Waiting for sharding to take effect ==="
sleep 5

echo "=== Inserting seed data ==="
docker compose exec -T mongos mongosh --quiet --port 27017 /scripts/seed-data.js

echo "=== Verifying sharding ==="
docker compose exec -T mongos mongosh --quiet --port 27017 --eval "
db = db.getSiblingDB('College')
print('=== Shard Status ===')
sh.status()
print('\n=== Shard Distribution ===')
db.Student.getShardDistribution()
"

echo "=== Done ==="
