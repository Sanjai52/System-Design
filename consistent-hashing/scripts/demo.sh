#!/bin/bash
set -e
export MSYS2_ARG_CONV_EXCL="*"

BASE=http://localhost:8080
SEED_COUNT=10000

echo "=== Starting base containers (node1-node3) ==="
docker compose up -d

echo "=== Waiting for MongoDB containers to be healthy ==="
for NODE in node1 node2 node3; do
  until [ "$(docker inspect -f '{{.State.Health.Status}}' ch-$NODE 2>/dev/null)" = "healthy" ]; do
    printf "  waiting for %s...\n" "$NODE"
    sleep 2
  done
done

echo "=== Seeding $SEED_COUNT students through the hash ring ==="
curl -s -X POST "$BASE/api/admin/seed?count=$SEED_COUNT"
echo

echo "=== Distribution with 3 nodes ==="
curl -s "$BASE/api/admin/distribution"
echo

echo "=== Ring snapshot (3 nodes) ==="
curl -s "$BASE/api/admin/ring"
echo

echo "=== Reading back a sample of students (verifies retrieval routing) ==="
for ROLL in 1 2500 5000 7500 10000; do
  curl -s "$BASE/api/students/$ROLL"
  echo
done

echo "=== Starting node4 container ==="
docker compose --profile extra up -d node4
until [ "$(docker inspect -f '{{.State.Health.Status}}' ch-node4 2>/dev/null)" = "healthy" ]; do
  printf "  waiting for node4...\n"
  sleep 2
done

echo "=== Adding node4 to the hash ring (migrates only affected records) ==="
curl -s -X POST "$BASE/api/admin/nodes" -H "Content-Type: application/json" \
  -d '{"id":"node4","host":"localhost","port":27033}'
echo

echo "=== Ring snapshot (4 nodes) ==="
curl -s "$BASE/api/admin/ring"
echo

echo "=== Removing node1 from the hash ring (redistributes only its records) ==="
curl -s -X DELETE "$BASE/api/admin/nodes/node1"
echo

echo "=== Physically stopping the removed node1 container ==="
docker compose stop node1

echo "=== Final distribution (3 nodes) ==="
curl -s "$BASE/api/admin/distribution"
echo

echo "=== Done ==="
