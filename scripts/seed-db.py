#!/usr/bin/env python3
"""One Lotline Aiven helper: inspect, wipe, or reseed.

Reads config/application-aiven.properties. Does not print the password.

  python3 -B scripts/seed-db.py                 # show tables
  python3 -B scripts/seed-db.py --empty-payments
  python3 -B scripts/seed-db.py --wipe          # truncate demo tables
  python3 -B scripts/seed-db.py --seed          # wipe + Spring seed (catalogue only)

Payments are never invented. Unpaid SOLD lots stay in Awaiting until Stripe Checkout.
"""

from __future__ import annotations

import os
import subprocess
import sys
import venv
from pathlib import Path
from urllib.parse import parse_qs, urlparse

sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
PROPS = ROOT / "config" / "application-aiven.properties"
SCHEMA = ROOT / "scripts" / "schema.sql"
VENV_PY = ROOT / "scripts" / ".venv" / "bin" / "python"

INACTIVE_HINT = (
    "Aiven PostgreSQL looks inactive or unreachable.\n"
    "  → Open https://console.aiven.io and power on / start the service,\n"
    "    wait until it is Running, then retry.\n"
    "  → Also confirm Allowed IP addresses include this machine (or 0.0.0.0/0 for demos)."
)


def ensure_venv() -> None:
    if VENV_PY.exists():
        return
    venv.EnvBuilder(with_pip=True).create(ROOT / "scripts" / ".venv")
    subprocess.check_call([str(VENV_PY), "-m", "pip", "install", "-q", "psycopg2-binary"])


def reexec_venv() -> None:
    ensure_venv()
    if Path(sys.executable).resolve() == VENV_PY.resolve():
        return
    os.execv(str(VENV_PY), [str(VENV_PY), "-B", str(Path(__file__).resolve()), *sys.argv[1:]])


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
        sys.exit("Install psycopg2-binary in scripts/.venv")

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


def migrate(conn) -> None:
    if not SCHEMA.exists():
        sys.exit(f"Missing {SCHEMA}")
    with conn.cursor() as cur:
        cur.execute(SCHEMA.read_text())


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


def empty_payments(conn) -> int:
    with conn.cursor() as cur:
        cur.execute("DELETE FROM payments")
        return cur.rowcount


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
            "payments": """
                SELECT id, auction_id, payer_id, amount, status,
                       gateway_transaction_id, checkout_session_id
                FROM "payments" ORDER BY id
                """,
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


def spring_seed() -> None:
    java_home = os.environ.get("JAVA_HOME", "/opt/homebrew/opt/openjdk@17")
    env = os.environ.copy()
    env["JAVA_HOME"] = java_home
    env["PATH"] = f"{java_home}/bin:{env.get('PATH', '')}"
    subprocess.check_call(
        [
            "mvn",
            "-q",
            "spring-boot:run",
            "-Dspring-boot.run.profiles=seed,aiven",
            "-Dspring-boot.run.arguments=--spring.main.web-application-type=servlet "
            "--spring.task.scheduling.enabled=false",
        ],
        cwd=ROOT,
        env=env,
    )


def main() -> None:
    reexec_venv()
    args = sys.argv[1:]
    conn, dsn = connect()
    try:
        if "--seed" in args:
            wiped = wipe(conn)
            migrate(conn)
            print(f"Wiped: {', '.join(wiped) if wiped else '(empty schema)'}")
            print("Seeding catalogue via Spring (no payment receipts)…")
            conn.close()
            conn = None
            spring_seed()
            conn, dsn = connect()
            show(conn, dsn)
            return
        if "--wipe" in args:
            wiped = wipe(conn)
            print(f"Connected to {dsn['host']}:{dsn['port']}/{dsn['dbname']} as {dsn['user']}")
            print("Wiped tables: " + (", ".join(wiped) if wiped else "(none yet)"))
            return
        if "--empty-payments" in args:
            deleted = empty_payments(conn)
            print(f"Cleared {deleted} payment row(s).")
            show(conn, dsn)
            return
        show(conn, dsn)
    finally:
        if conn is not None:
            conn.close()


if __name__ == "__main__":
    main()
