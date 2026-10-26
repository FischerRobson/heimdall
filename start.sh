#!/usr/bin/env bash
# Starts the Heimdall API (Quarkus) and the frontend (Vite) together.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cleanup() {
  echo "Stopping..."
  jobs -p | xargs -r kill 2>/dev/null
}
trap cleanup EXIT INT TERM

echo "Starting API (quarkus dev)..."
(cd "$ROOT_DIR/heimdall-core" && quarkus dev) &

echo "Starting frontend (npm run dev)..."
(cd "$ROOT_DIR/heimdall-ui" && npm run dev) &

wait
