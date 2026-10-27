#!/usr/bin/env bash
# Development: runs the demo app (Quarkus, with Heimdall embedded) and the Vite dev
# server for the dashboard with hot reload.
#   API + packaged dashboard: http://localhost:8080/heimdall/
#   dashboard (hot reload):   http://localhost:5173/heimdall/
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cleanup() {
  echo "Stopping..."
  jobs -p | xargs -r kill 2>/dev/null
}
trap cleanup EXIT INT TERM

echo "Installing Heimdall modules (skipping the UI build — Vite serves it)..."
(cd "$ROOT_DIR" && ./mvnw -q install -DskipTests -Dskip.npm -Dskip.installnodenpm -pl heimdall-core,heimdall-docker -am)

echo "Starting demo app (quarkus dev)..."
(cd "$ROOT_DIR/heimdall-demo" && ../mvnw quarkus:dev) &

echo "Starting frontend (npm run dev)..."
(cd "$ROOT_DIR/heimdall-ui" && npm run dev) &

wait
