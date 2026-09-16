#!/usr/bin/env python3
"""Seed demo collections into Aiven PostgreSQL.

Safe to run more than once: skips a collection if that name already exists.
Reads config/application-aiven.properties. Does not print the password.

Demo logins stay mara / julian / seller / admin with password123.
"""

from __future__ import annotations

import sys
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path
from urllib.parse import parse_qs, urlparse

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


def user_id(cur, username: str) -> int:
    cur.execute("SELECT id FROM users WHERE username = %s", (username,))
    row = cur.fetchone()
    if not row:
        sys.exit(f"No user named {username}. Start the API once so demo accounts exist.")
    return row[0]


def collection_exists(cur, name: str) -> bool:
    cur.execute("SELECT 1 FROM lot_collections WHERE name = %s", (name,))
    return cur.fetchone() is not None


def insert_collection(cur, name: str, seller_id: int, now: datetime, description: str = "") -> int:
    cur.execute(
        """
        INSERT INTO lot_collections (name, description, seller_id, created_at)
        VALUES (%s, %s, %s, %s)
        RETURNING id
        """,
        (name, description or "", seller_id, now),
    )
    return cur.fetchone()[0]


def insert_lot(cur, *, title, description, category, image_url, start, increment,
               current, seller_id, collection_id, start_time, end_time, status, now) -> int:
    cur.execute(
        """
        INSERT INTO auction_items (
            title, description, category, image_url,
            starting_price, min_increment, current_price,
            seller_id, collection_id, start_time, end_time, status, created_at
        )
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        RETURNING id
        """,
        (
            title, description, category, image_url,
            start, increment, current,
            seller_id, collection_id, start_time, end_time, status, now,
        ),
    )
    return cur.fetchone()[0]


def insert_bid(cur, auction_id: int, bidder_id: int, amount: Decimal, placed_at: datetime) -> None:
    cur.execute(
        """
        INSERT INTO bids (auction_id, bidder_id, amount, placed_at)
        VALUES (%s, %s, %s, %s)
        """,
        (auction_id, bidder_id, amount, placed_at),
    )


def wrap_orphans(cur) -> int:
    cur.execute(
        """
        SELECT id, title, description, seller_id, created_at
        FROM auction_items
        WHERE collection_id IS NULL
        ORDER BY id
        """
    )
    orphans = cur.fetchall()
    wrapped = 0
    for lot_id, title, description, seller_id, created_at in orphans:
        cid = insert_collection(cur, title, seller_id, created_at, description or "")
        cur.execute(
            "UPDATE auction_items SET collection_id = %s WHERE id = %s",
            (cid, lot_id),
        )
        wrapped += 1
    return wrapped


def main() -> None:
    try:
        import psycopg2
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
    now = datetime.now(timezone.utc)
    added: list[str] = []

    try:
        with conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT 1 FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_name = 'lot_collections'
                    """
                )
                if cur.fetchone() is None:
                    sys.exit("lot_collections is missing. Run scripts/migrate-db.py first.")

                cur.execute(
                    "ALTER TABLE lot_collections ADD COLUMN IF NOT EXISTS description VARCHAR(4000)"
                )
                cur.execute(
                    "UPDATE lot_collections SET description = '' WHERE description IS NULL"
                )

                wrapped = wrap_orphans(cur)
                if wrapped:
                    added.append(f"{wrapped} standalone lots wrapped as collections")

                seller = user_id(cur, "seller")
                mara = user_id(cur, "mara")
                julian = user_id(cur, "julian")

                if not collection_exists(cur, "A weekend by the sea"):
                    cid = insert_collection(cur, "A weekend by the sea", seller, now)
                    start = now - timedelta(minutes=25)
                    end = now + timedelta(minutes=70)
                    deck = insert_lot(
                        cur,
                        title="Canvas deck chair, striped",
                        description="Folding beech frame with original red-and-cream canvas. Hardware complete. From a coastal house clearance.",
                        category="Antiques",
                        image_url="https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=1400&q=80",
                        start=Decimal("180.00"),
                        increment=Decimal("15.00"),
                        current=Decimal("210.00"),
                        seller_id=seller,
                        collection_id=cid,
                        start_time=start,
                        end_time=end,
                        status="LIVE",
                        now=now,
                    )
                    hamper = insert_lot(
                        cur,
                        title="Enamel picnic hamper",
                        description="Cream enamel with navy trim, original cutlery straps intact. Light wear to the lid. A practical beach companion.",
                        category="Collectibles",
                        image_url="https://images.unsplash.com/photo-1473093295043-cdd812d0e601?auto=format&fit=crop&w=1400&q=80",
                        start=Decimal("90.00"),
                        increment=Decimal("10.00"),
                        current=Decimal("110.00"),
                        seller_id=seller,
                        collection_id=cid,
                        start_time=start,
                        end_time=end,
                        status="LIVE",
                        now=now,
                    )
                    tide = insert_lot(
                        cur,
                        title="Tide chart in a gilt frame",
                        description="Hand-coloured coastal chart under glass. Frame later, print mid-century. Hung in a porch overlooking the water.",
                        category="Art",
                        image_url="https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=1400&q=80",
                        start=Decimal("240.00"),
                        increment=Decimal("20.00"),
                        current=Decimal("280.00"),
                        seller_id=seller,
                        collection_id=cid,
                        start_time=start,
                        end_time=end,
                        status="LIVE",
                        now=now,
                    )
                    insert_bid(cur, deck, mara, Decimal("195.00"), now - timedelta(minutes=18))
                    insert_bid(cur, deck, julian, Decimal("210.00"), now - timedelta(minutes=9))
                    insert_bid(cur, hamper, mara, Decimal("110.00"), now - timedelta(minutes=12))
                    insert_bid(cur, tide, julian, Decimal("260.00"), now - timedelta(minutes=14))
                    insert_bid(cur, tide, mara, Decimal("280.00"), now - timedelta(minutes=4))
                    added.append("A weekend by the sea (3 live lots)")

                if not collection_exists(cur, "After midnight at the club"):
                    cid = insert_collection(cur, "After midnight at the club", julian, now)
                    start = now - timedelta(minutes=12)
                    end = now + timedelta(minutes=95)
                    shaker = insert_lot(
                        cur,
                        title="Silver cocktail shaker, 1930s",
                        description="Plated shaker with intact strainer cap. Dents modest. The sort of piece that lived behind a hotel bar.",
                        category="Antiques",
                        image_url="https://images.unsplash.com/photo-1514362545857-3bc16c4c7d1b?auto=format&fit=crop&w=1400&q=80",
                        start=Decimal("320.00"),
                        increment=Decimal("20.00"),
                        current=Decimal("360.00"),
                        seller_id=julian,
                        collection_id=cid,
                        start_time=start,
                        end_time=end,
                        status="LIVE",
                        now=now,
                    )
                    photo = insert_lot(
                        cur,
                        title="Nightclub photograph, signed",
                        description="Gelatin silver print of a crowded dance floor. Photographer’s stamp and later signature on the verso.",
                        category="Art",
                        image_url="https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?auto=format&fit=crop&w=1400&q=80",
                        start=Decimal("150.00"),
                        increment=Decimal("15.00"),
                        current=Decimal("180.00"),
                        seller_id=julian,
                        collection_id=cid,
                        start_time=start,
                        end_time=end,
                        status="LIVE",
                        now=now,
                    )
                    insert_bid(cur, shaker, mara, Decimal("340.00"), now - timedelta(minutes=8))
                    insert_bid(cur, shaker, seller, Decimal("360.00"), now - timedelta(minutes=3))
                    insert_bid(cur, photo, mara, Decimal("180.00"), now - timedelta(minutes=6))
                    added.append("After midnight at the club (2 live lots)")

                if not collection_exists(cur, "The quiet study"):
                    cid = insert_collection(cur, "The quiet study", mara, now)
                    start = now + timedelta(minutes=20)
                    end = start + timedelta(hours=4)
                    insert_lot(
                        cur,
                        title="Brass reading lamp",
                        description="Adjustable arm, original switch, warm patina. Shade later. Meant for a desk rather than a drawing room.",
                        category="Antiques",
                        image_url="https://images.unsplash.com/photo-1507473885765-e6ed357f3443?auto=format&fit=crop&w=1400&q=80",
                        start=Decimal("220.00"),
                        increment=Decimal("20.00"),
                        current=Decimal("220.00"),
                        seller_id=mara,
                        collection_id=cid,
                        start_time=start,
                        end_time=end,
                        status="SCHEDULED",
                        now=now,
                    )
                    insert_lot(
                        cur,
                        title="Set of morocco diaries, 1920s",
                        description="Five small volumes, gilt edges, most pages unused. A librarian’s unused stock from a quiet house.",
                        category="Books",
                        image_url="https://images.unsplash.com/photo-1519682337058-a94d519337bc?auto=format&fit=crop&w=1400&q=80",
                        start=Decimal("140.00"),
                        increment=Decimal("10.00"),
                        current=Decimal("140.00"),
                        seller_id=mara,
                        collection_id=cid,
                        start_time=start,
                        end_time=end,
                        status="SCHEDULED",
                        now=now,
                    )
                    added.append("The quiet study (2 upcoming lots)")
    finally:
        conn.close()

    if added:
        print("Seeded:")
        for name in added:
            print(f"  {name}")
    else:
        print("Collections already present. Nothing to insert.")
        print("Open Collection catalogue to browse every posting, including one-lot collections.")


if __name__ == "__main__":
    main()
