#!/usr/bin/env bash
# Local Redis for OptionsEdge multi-tenant-session integration tests (OE-DDD-001 §6.5/§6.7).
set -euo pipefail

NAME="optionsedge-redis"
HOST_PORT="${REDIS_HOST_PORT:-6380}"
IMAGE="redis:7-alpine"

docker rm -f "$NAME" >/dev/null 2>&1 || true
docker run -d --name "$NAME" -p "${HOST_PORT}:6379" "$IMAGE" \
  redis-server --appendonly yes --maxmemory-policy noeviction >/dev/null

echo "Redis starting on localhost:${HOST_PORT}"
for i in $(seq 1 30); do
  if docker exec "$NAME" redis-cli ping 2>/dev/null | grep -q PONG; then
    echo "Redis ready."
    exit 0
  fi
  sleep 1
done
echo "Redis did not become ready in time." >&2
exit 1
