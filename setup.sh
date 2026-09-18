#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

command -v bun >/dev/null 2>&1 || { echo "Bun 1.4+ is required." >&2; exit 1; }
command -v java >/dev/null 2>&1 || { echo "Java 21+ is required." >&2; exit 1; }
command -v mongosh >/dev/null 2>&1 || { echo "mongosh is required." >&2; exit 1; }

MONGODB_URI="${MONGODB_URI:-mongodb://localhost:27018/launchfleet?replicaSet=rs0&directConnection=true}"
if ! mongosh "$MONGODB_URI" --quiet --eval 'db.runCommand({ ping: 1 }).ok' | grep -q '^1$'; then
  echo "MongoDB is not reachable at MONGODB_URI." >&2
  exit 1
fi

if [[ ! -f backend/.env && -f backend/.env.example ]]; then cp backend/.env.example backend/.env; fi
if [[ ! -f frontend/.env && -f frontend/.env.example ]]; then cp frontend/.env.example frontend/.env; fi
bun install
if [[ "${1:-}" == "--seed" ]]; then
  MONGODB_URI="$MONGODB_URI" backend/gradlew -p backend seed
fi
echo "Setup complete."
