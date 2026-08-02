#!/bin/bash
set -e
export MSYS2_ARG_CONV_EXCL="*"

echo "=== Starting containers ==="
docker compose up -d

echo "=== Waiting for containers to be ready ==="
sleep 10

echo "=== Waiting for config server to be ready ==="
until docker compose exec -T configsvr mongosh --quiet --port 27017 --eval "db.runCommand({ping:1})" 2>/dev/null; do
  echo "  waiting for configsvr..."
  sleep 2
done

echo "=== Init config server replica set ==="
for i in $(seq 1 10); do
  docker compose exec -T configsvr mongosh --quiet --port 27017 /scripts/init-config.js 2>/dev/null && break
  echo "  retrying configsvr init in 3s... (attempt $i)"
  sleep 3
done

echo "=== Waiting for config replica set primary ==="
sleep 5

echo "=== Init shard1 replica set ==="
docker compose exec -T shard1 mongosh --quiet --port 27017 /scripts/init-shard1.js

echo "=== Init shard2 replica set ==="
docker compose exec -T shard2 mongosh --quiet --port 27017 /scripts/init-shard2.js

echo "=== Waiting for shard replica sets ==="
sleep 5

echo "=== Waiting for mongos to be ready ==="
until docker compose exec -T mongos mongosh --quiet --port 27017 --eval "db.runCommand({ping:1})" 2>/dev/null; do
  echo "  waiting for mongos..."
  sleep 2
done

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