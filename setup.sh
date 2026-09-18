#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

command -v bun >/dev/null 2>&1 || { echo "Bun 1.4+ is required." >&2; exit 1; }
command -v java >/dev/null 2>&1 || { echo "Java 21+ is required." >&2; exit 1; }
command -v mongosh >/dev/null 2>&1 || { echo "mongosh is required." >&2; exit 1; }

MONGODB_URI="${MONGODB_URI:-mongodb://localhost:27018/launchfleet?replicaSet=rs0&directConnection=true}"
if ! mongosh "$MONGODB_URI" --quiet --eval 'db.runCommand({ ping: 1 }).ok' 2>/dev/null | grep -q '^1$'; then
  if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
    echo "MongoDB is not reachable and Docker Compose is unavailable." >&2
    exit 1
  fi

  docker compose up -d mongodb
  for _ in {1..60}; do
    docker compose exec -T mongodb mongosh --quiet --eval \
      'try { rs.status().ok } catch (e) { rs.initiate({_id:"rs0",members:[{_id:0,host:"localhost:27017"}]}).ok }' >/dev/null 2>&1 || true
    if mongosh "$MONGODB_URI" --quiet --eval 'db.runCommand({ ping: 1 }).ok' 2>/dev/null | grep -q '^1$'; then
      break
    fi
    sleep 1
  done
fi

if ! mongosh "$MONGODB_URI" --quiet --eval 'db.runCommand({ ping: 1 }).ok' 2>/dev/null | grep -q '^1$'; then
  echo "MongoDB is not reachable at MONGODB_URI after Docker startup." >&2
  exit 1
fi

if [[ ! -f backend/.env && -f backend/.env.example ]]; then cp backend/.env.example backend/.env; fi
if [[ ! -f frontend/.env && -f frontend/.env.example ]]; then cp frontend/.env.example frontend/.env; fi
bun install
if [[ "${1:-}" == "--seed" ]]; then
  MONGODB_URI="$MONGODB_URI" backend/gradlew -p backend seed
fi
echo "Setup complete."
