#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"
bash setup.sh --seed

cleanup() {
  [[ -n "${BACKEND_PID:-}" ]] && kill "$BACKEND_PID" 2>/dev/null || true
  [[ -n "${FRONTEND_PID:-}" ]] && kill "$FRONTEND_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

(cd backend && MONGODB_URI="${MONGODB_URI:-mongodb://localhost:27018/launchfleet?replicaSet=rs0&directConnection=true}" ./gradlew bootRun) &
BACKEND_PID=$!
for _ in {1..60}; do
  curl --silent --fail http://localhost:8000/actuator/health >/dev/null 2>&1 && break
  kill -0 "$BACKEND_PID" 2>/dev/null || { echo "Backend exited before readiness." >&2; exit 1; }
  sleep 1
done
curl --silent --fail http://localhost:8000/actuator/health >/dev/null 2>&1 || { echo "Backend did not become ready on port 8000." >&2; exit 1; }

(cd frontend && bun run dev -- --host 0.0.0.0 --port 3000) &
FRONTEND_PID=$!
wait -n "$BACKEND_PID" "$FRONTEND_PID"
