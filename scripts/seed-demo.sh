#!/usr/bin/env bash
# One-time (or occasional) Lotline demo seed — Aiven PostgreSQL only.
#
# WARNING: Wipes users, lots, bids, payments, and collections on Aiven, then
# reseeds via Spring DataInitializer (profile "seed"). Irreversible for cloud data.
#
# Usage (from repo root):
#   ./scripts/seed-demo.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Never write __pycache__ / .pyc when running helper Python.
export PYTHONDONTWRITEBYTECODE=1

JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

PROPS="$ROOT/config/application-aiven.properties"
if [[ ! -f "$PROPS" ]]; then
  echo "Missing $PROPS"
  echo "Create it with spring.datasource.url / username / password (gitignored)."
  exit 1
fi

PY="$ROOT/scripts/.venv/bin/python"
if [[ ! -x "$PY" ]]; then
  echo "==> Creating scripts/.venv and installing psycopg2-binary"
  python3 -m venv "$ROOT/scripts/.venv"
  "$ROOT/scripts/.venv/bin/pip" install -q psycopg2-binary
  PY="$ROOT/scripts/.venv/bin/python"
fi

echo "==> Checking Aiven connectivity and wiping demo tables"
"$PY" -B "$ROOT/scripts/show-db.py" --wipe

echo "==> Seeding Aiven via Spring Boot profiles seed,aiven (exits when done)"
mvn -q spring-boot:run \
  -Dspring-boot.run.profiles=seed,aiven \
  -Dspring-boot.run.arguments="--spring.main.web-application-type=servlet --spring.task.scheduling.enabled=false"

echo "==> Verifying row counts on Aiven"
"$PY" -B "$ROOT/scripts/show-db.py" | head -n 40

echo "==> Seed complete on Aiven."
echo "    Start API: mvn spring-boot:run"
echo "    Demo login: mara / julian / seller / admin  ·  password123"
