#!/usr/bin/env python3
"""Apply scripts/schema*.sql to Aiven PostgreSQL.

Reads config/application-aiven.properties. Does not print the password.
Does not write __pycache__ / .pyc.
Safe to run more than once.
"""

from __future__ import annotations

import sys
from pathlib import Path
from urllib.parse import parse_qs, urlparse

sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
PROPS = ROOT / "config" / "application-aiven.properties"
SCHEMA_FILES = [
    ROOT / "scripts" / "schema.sql",
    ROOT / "scripts" / "schema-collections.sql",
]

INACTIVE_HINT = (
    "Aiven PostgreSQL looks inactive or unreachable.\n"
    "  → Open https://console.aiven.io and power on / start the service, then retry."
)


def load_props(path: Path = PROPS) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        sys.exit(f"Missing {path}.")
    for raw in path.read_text().splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def jdbc_to_dsn(url: str, username: str, password: str) -> dict[str, str]:
    if url.startswith("jdbc:"):
        url = url[len("jdbc:") :]
    parsed = urlparse(url)
    query = parse_qs(parsed.query)
    sslmode = (query.get("sslmode") or ["require"])[0]
    return {
        "host": parsed.hostname or "",
        "port": str(parsed.port or 5432),
        "dbname": (parsed.path or "/defaultdb").lstrip("/") or "defaultdb",
        "user": username or parsed.username or "",
        "password": password or parsed.password or "",
        "sslmode": sslmode,
    }


def connect():
    try:
        import psycopg2
        from psycopg2 import OperationalError
    except ImportError:
        sys.exit("Install the driver first: python3 -m pip install psycopg2-binary")

    props = load_props()
    dsn = jdbc_to_dsn(
        props.get("spring.datasource.url", ""),
        props.get("spring.datasource.username", ""),
        props.get("spring.datasource.password", ""),
    )
    if not dsn["host"] or "YOUR_AIVEN" in dsn["host"]:
        sys.exit("Fill host/port/password in config/application-aiven.properties first.")
    try:
        conn = psycopg2.connect(**dsn, connect_timeout=15)
        conn.autocommit = True
        return conn, dsn
    except OperationalError as exc:
        sys.exit(f"{INACTIVE_HINT}\n\nDriver detail: {exc}")


def main() -> None:
    missing = [path for path in SCHEMA_FILES if not path.exists()]
    if missing:
        sys.exit("Missing " + ", ".join(str(path) for path in missing))

    conn, dsn = connect()
    applied = []
    try:
        with conn.cursor() as cur:
            for path in SCHEMA_FILES:
                cur.execute(path.read_text())
                applied.append(path.name)
            cur.execute(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname LIKE 'idx_%'
                ORDER BY indexname
                """
            )
            indexes = [row[0] for row in cur.fetchall()]
    finally:
        conn.close()

    print(f"Applied {', '.join(applied)} to {dsn['dbname']} @ {dsn['host']}")
    print("Query indexes:")
    for name in indexes:
        print(f"  {name}")


if __name__ == "__main__":
    main()
