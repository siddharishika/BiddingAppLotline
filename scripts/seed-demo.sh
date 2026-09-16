#!/usr/bin/env bash
# Rebuild the local Lotline demo catalogue from a clean H2 file database.
#
# WARNING: This deletes data/lotline* and recreates users, lots, bids, and payments
# from DataInitializer. Real charges you made against the old file DB cannot be recovered.
#
# What this seeds (relative to "now" at seed time — stored in the database, not the browser):
#   Users:  mara, julian, seller, admin  /  password123
#   Sold lots with winners, including payment rows (SUCCEEDED + FAILED) for receipts
#   Unpaid SOLD wins for Awaiting payment
#   Live / upcoming collections and consignments
#
# Consignor/payment rules are status-based only (not title/user hardcoding in product logic).
#
# Usage (from repo root or scripts/):
#   ./scripts/seed-demo.sh
#
# After a successful seed, start the API as usual (without the seed profile).

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

DATA_DIR="$ROOT/data"
mkdir -p "$DATA_DIR"

echo "==> Stopping anything on :8080 (if present)"
if command -v lsof >/dev/null 2>&1; then
  PIDS="$(lsof -ti tcp:8080 || true)"
  if [[ -n "${PIDS}" ]]; then
    kill ${PIDS} || true
    sleep 1
  fi
fi

echo "==> Removing local H2 files under data/"
rm -f \
  "$DATA_DIR/lotline.mv.db" \
  "$DATA_DIR/lotline.trace.db" \
  "$DATA_DIR/lotline.lock.db" \
  "$DATA_DIR/lotline.mv.db.tmp" \
  2>/dev/null || true

H2_URL='jdbc:h2:file:./data/lotline;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1'

echo "==> Seeding via Spring Boot profile 'seed' (exits when DataInitializer finishes)"
mvn -q spring-boot:run \
  -Dspring-boot.run.profiles=seed \
  -Dspring-boot.run.useTestClasspath=true \
  -Dspring-boot.run.arguments="--spring.datasource.url=${H2_URL} --spring.datasource.driver-class-name=org.h2.Driver --spring.datasource.username=sa --spring.datasource.password= --spring.jpa.database-platform=org.hibernate.dialect.H2Dialect --spring.main.web-application-type=servlet"

echo "==> Seed complete. Demo DB is at data/lotline*"
echo "    Start the API without --spring.profiles.active=seed to use it."
echo "    Demo login: mara / julian / seller / admin  ·  password123"
