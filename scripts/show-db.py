#!/usr/bin/env python3
"""Print Lotline tables and a short sample from Aiven PostgreSQL.

Reads config/application-aiven.properties. Does not print the password.
"""

from __future__ import annotations

import sys
from pathlib import Path
from urllib.parse import urlparse, parse_qs

ROOT = Path(__file__).resolve().parents[1]
PROPS = ROOT / "config" / "application-aiven.properties"


def load_props(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        sys.exit(f"Missing {path}. Copy config/application-aiven.properties.example and fill in credentials.")
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


def main() -> None:
    try:
        import psycopg2
        from psycopg2.extras import RealDictCursor
    except ImportError:
        sys.exit("Install the driver first: python3 -m pip install psycopg2-binary")

    props = load_props(PROPS)
    dsn = jdbc_to_dsn(
        props.get("spring.datasource.url", ""),
        props.get("spring.datasource.username", ""),
        props.get("spring.datasource.password", ""),
    )
    if not dsn["host"] or "YOUR_AIVEN" in dsn["host"]:
        sys.exit("Fill host/port/password in config/application-aiven.properties first.")

    conn = psycopg2.connect(**dsn)
    conn.autocommit = True

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

    conn.close()


if __name__ == "__main__":
    main()
