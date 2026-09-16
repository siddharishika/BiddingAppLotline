#!/usr/bin/env python3
"""Inspect (or wipe) Lotline tables on Aiven PostgreSQL.

Reads config/application-aiven.properties. Does not print the password.
Does not write __pycache__ / .pyc.

Usage:
  python3 -B scripts/show-db.py           # print tables + samples
  python3 -B scripts/show-db.py --wipe    # truncate demo tables (used by seed-demo.sh)
"""

from __future__ import annotations

import sys
from pathlib import Path
from urllib.parse import parse_qs, urlparse

sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
PROPS = ROOT / "config" / "application-aiven.properties"

INACTIVE_HINT = (
    "Aiven PostgreSQL looks inactive or unreachable.\n"
    "  → Open https://console.aiven.io and power on / start the service,\n"
    "    wait until it is Running, then retry.\n"
    "  → Also confirm Allowed IP addresses include this machine (or 0.0.0.0/0 for demos)."
)


def load_props(path: Path = PROPS) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        sys.exit(
            f"Missing {path}. Create config/application-aiven.properties "
            "with spring.datasource.url / username / password (gitignored)."
        )
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
        msg = str(exc).lower()
        if any(
            token in msg
            for token in (
                "timeout",
                "could not connect",
                "connection refused",
                "network is unreachable",
                "name or service not known",
                "ssl connection has been closed",
                "server closed the connection",
            )
        ):
            sys.exit(f"{INACTIVE_HINT}\n\nDriver detail: {exc}")
        sys.exit(f"Could not connect to Aiven PostgreSQL: {exc}")


def wipe(conn) -> list[str]:
    tables = ["payments", "bids", "auction_items", "lot_collections", "users"]
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
            """
        )
        existing = {row[0] for row in cur.fetchall()}
        to_wipe = [name for name in tables if name in existing]
        if not to_wipe:
            return []
        cur.execute(
            "TRUNCATE TABLE "
            + ", ".join(f'"{name}"' for name in to_wipe)
            + " RESTART IDENTITY CASCADE"
        )
    return to_wipe


def show(conn, dsn: dict[str, str]) -> None:
    from psycopg2.extras import RealDictCursor

    print(f"Connected to {dsn['host']}:{dsn['port']}/{dsn['dbname']} as {dsn['user']}\n")
    with conn.cursor(cursor_factory=RealDictCursor) as cur:
        cur.execute(
            """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
            ORDER BY table_name
            """
        )
        tables = [row["table_name"] for row in cur.fetchall()]
        if not tables:
            print("No tables in public schema.")
            return

        print("Tables")
        print("------")
        for name in tables:
            cur.execute(f'SELECT COUNT(*) AS n FROM "{name}"')
            print(f"  {name:20} {cur.fetchone()['n']} rows")

        previews = {
            "users": 'SELECT id, username, role, enabled FROM "users" ORDER BY id',
            "lot_collections": 'SELECT id, name, seller_id FROM "lot_collections" ORDER BY id',
            "auction_items": """
                SELECT i.id, i.title, i.status, i.current_price, i.seller_id, i.winner_id,
                       COALESCE(c.name, '—') AS collection
                FROM auction_items i
                LEFT JOIN lot_collections c ON c.id = i.collection_id
                ORDER BY i.id
                """,
            "bids": 'SELECT id, auction_id, bidder_id, amount, placed_at FROM "bids" ORDER BY id',
            "payments": 'SELECT id, auction_id, payer_id, amount, status FROM "payments" ORDER BY id',
        }

        for name, sql in previews.items():
            if name not in tables:
                continue
            print(f"\n{name}")
            print("-" * len(name))
            cur.execute(sql)
            rows = cur.fetchall()
            if not rows:
                print("  (empty)")
                continue
            columns = list(rows[0].keys())
            print("  " + " | ".join(columns))
            for row in rows:
                print("  " + " | ".join(str(row[col]) for col in columns))


def main() -> None:
    wipe_only = "--wipe" in sys.argv[1:]
    conn, dsn = connect()
    try:
        if wipe_only:
            wiped = wipe(conn)
            print(f"Connected to {dsn['host']}:{dsn['port']}/{dsn['dbname']} as {dsn['user']}")
            if wiped:
                print("Wiped tables: " + ", ".join(wiped))
            else:
                print("No Lotline tables yet (Hibernate/ddl will create them on seed).")
            return
        show(conn, dsn)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
