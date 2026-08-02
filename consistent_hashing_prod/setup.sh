#!/bin/bash
set -e

echo "=== Starting MongoDB storage nodes (mongo1..mongo4) ==="
docker compose up -d mongo1 mongo2 mongo3 mongo4

echo "=== Waiting for mongod instances to accept connections ==="
for svc in mongo1 mongo2 mongo3 mongo4; do
  until docker compose exec -T "$svc" mongosh --quiet --eval "db.runCommand({ping:1}).ok" 2>/dev/null | grep -q 1; do
    echo "  waiting for $svc..."
    sleep 2
  done
  echo "  $svc is up"
done

echo "=== Storage nodes ready ==="
echo "=== Start the Spring Boot app:  mvn spring-boot:run ==="
echo "=== Open the UI:                 http://localhost:8080/ ==="
echo "=== Swagger UI:                  http://localhost:8080/swagger-ui.html ==="
