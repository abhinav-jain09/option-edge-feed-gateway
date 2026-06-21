#!/usr/bin/env bash
# Local Keycloak for OptionsEdge multi-tenant-session integration tests (OE-DDD-001 §5).
# Brings up Keycloak with the `optionsedge` realm pre-imported on http://localhost:8099.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NAME="optionsedge-keycloak"
HOST_PORT="${KEYCLOAK_HOST_PORT:-8099}"
IMAGE="quay.io/keycloak/keycloak:26.0"

docker rm -f "$NAME" >/dev/null 2>&1 || true

docker run -d --name "$NAME" \
  -p "${HOST_PORT}:8080" \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  -v "${HERE}/realm-export.json:/opt/keycloak/data/import/realm-export.json:ro" \
  "$IMAGE" start-dev --import-realm >/dev/null

echo "Keycloak starting on http://localhost:${HOST_PORT} (realm: optionsedge)"
echo "Waiting for readiness..."
for i in $(seq 1 60); do
  if curl -fsS "http://localhost:${HOST_PORT}/realms/optionsedge/.well-known/openid-configuration" >/dev/null 2>&1; then
    echo "Keycloak ready."
    exit 0
  fi
  sleep 2
done
echo "Keycloak did not become ready in time." >&2
exit 1
