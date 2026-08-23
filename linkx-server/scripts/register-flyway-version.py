#!/usr/bin/env python3
"""Register a Flyway migration in flyway_schema_history if missing."""
from __future__ import annotations

import argparse
import zlib
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parents[1]
MIGRATION_DIR = ROOT / "src" / "main" / "resources" / "db" / "migration"


def flyway_checksum(path: Path) -> int:
    data = path.read_bytes().replace(b"\r\n", b"\n")
    c = zlib.crc32(data) & 0xFFFFFFFF
    return c if c < 0x80000000 else c - 0x100000000


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("version", help="e.g. 106")
    parser.add_argument("--description", required=True)
    parser.add_argument("--script", required=True, help="e.g. V106__moments_post_fulltext.sql")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--user", default="root")
    parser.add_argument("--password", default="root")
    parser.add_argument("--database", default="linkx")
    args = parser.parse_args()

    script_path = MIGRATION_DIR / args.script
    if not script_path.is_file():
        raise SystemExit(f"script not found: {script_path}")

    conn = pymysql.connect(
        host=args.host, user=args.user, password=args.password, database=args.database
    )
    cur = conn.cursor()
    cur.execute("SELECT 1 FROM flyway_schema_history WHERE version=%s", (args.version,))
    if cur.fetchone():
        print("skip", args.version)
        conn.close()
        return 0

    cur.execute("SELECT MAX(installed_rank) FROM flyway_schema_history")
    rank = (cur.fetchone()[0] or 0) + 1
    cs = flyway_checksum(script_path)
    cur.execute(
        "INSERT INTO flyway_schema_history "
        "(installed_rank, version, description, type, script, checksum, installed_by, execution_time, success) "
        "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,1)",
        (rank, args.version, args.description, "SQL", args.script, cs, "manual", 0),
    )
    conn.commit()
    conn.close()
    print("inserted", args.version, cs)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
