#!/usr/bin/env python3
"""
Extract SQL from LinkX Java sources, benchmark each statement 1000 times, report slow queries.
Usage: python benchmark-sql.py [--iterations 1000] [--slow-ms 5]
"""

from __future__ import annotations

import argparse
import os
import re
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path
from typing import Iterable

import pymysql

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "src" / "main" / "java"

ANNOTATION_START = re.compile(r"@(Select|Update|Insert|Delete)\(\s*", re.I)
TEXT_BLOCK = re.compile(r'"""\s*((?:SELECT|INSERT|UPDATE|DELETE|WITH|SHOW)[\s\S]*?)"""', re.I)
TABLE_ANNOTATION = re.compile(r'@Table\("([^"]+)"\)')
MYBATIS_PARAM = re.compile(r"#\{[^}]+\}")
JAVA_STRING = re.compile(r'"((?:[^"\\]|\\.)*)"')
SQL_HEAD = re.compile(r"^\s*(SELECT|INSERT|UPDATE|DELETE|WITH|SHOW)\b", re.I)

DEFAULT_HOST = os.environ.get("DB_HOST", "127.0.0.1")
DEFAULT_PORT = int(os.environ.get("DB_PORT", "3306"))
DEFAULT_USER = os.environ.get("DB_USERNAME", "root")
DEFAULT_PASSWORD = os.environ.get("DB_PASSWORD", "root")
DEFAULT_DB = os.environ.get("DB_NAME", "linkx")

PARAM_DEFAULTS = {
    "id": "1",
    "userId": "1",
    "senderId": "1",
    "conversationId": "1",
    "jobId": "1",
    "taskBatchId": "1",
    "status": "'active'",
    "newStatus": "'expired'",
    "expireTime": "NOW()",
    "limit": "10",
    "offset": "0",
    "amount": "0.01",
    "newCount": "1",
    "version": "1",
    "expectedVersion": "1",
    "delta": "0",
    "itemCountDelta": "0",
    "fileCountDelta": "0",
    "newQuota": "1073741824",
    "hours": "24",
    "days": "7",
    "category": "'hikari'",
    "metricKey": "'total_connections'",
    "metricName": "'total_requests'",
    "schema": "DATABASE()",
}


@dataclass
class SqlEntry:
    sql: str
    source: str
    kind: str


def log(msg: str) -> None:
    print(msg, flush=True)


def extract_annotation_sql(text: str, start: int) -> str | None:
    i = text.find("(", start) + 1
    parts: list[str] = []
    while i < len(text):
        while i < len(text) and text[i] in " \t\n\r":
            i += 1
        if i >= len(text):
            break
        if text[i] == ")":
            break
        if text[i] == '"':
            i += 1
            buf: list[str] = []
            while i < len(text):
                c = text[i]
                if c == "\\" and i + 1 < len(text):
                    buf.append(text[i : i + 2])
                    i += 2
                    continue
                if c == '"':
                    parts.append("".join(buf))
                    i += 1
                    break
                buf.append(c)
                i += 1
            while i < len(text) and text[i] in " \t\n\r":
                i += 1
            if i < len(text) and text[i] == "+":
                i += 1
                continue
            if i < len(text) and text[i] == ")":
                break
            continue
        break
    sql = "".join(parts).strip()
    return sql or None


def extract_concat_sql(raw: str) -> str:
    parts = JAVA_STRING.findall(raw)
    return "".join(parts)


def normalize_sql(raw: str) -> str:
    s = raw.strip()
    if "+" in s and '"' in s:
        s = extract_concat_sql(s)
    s = s.replace('\\"', '"').replace("\\n", " ").replace("\\t", " ")
    s = re.sub(r"\s+", " ", s).strip()
    return s


def substitute_mybatis(sql: str) -> str:
    def repl(m: re.Match) -> str:
        inner = m.group(0)[2:-1]
        key = inner.split(":", 1)[0].strip()
        if key in PARAM_DEFAULTS:
            return PARAM_DEFAULTS[key]
        lk = key.lower()
        if lk.endswith("time") or lk.endswith("at") or lk.endswith("date"):
            return "NOW()"
        if "id" in lk:
            return "1"
        if "status" in lk:
            return "'active'"
        if "limit" in lk or "offset" in lk:
            return "10" if "limit" in lk else "0"
        if "schema" in lk:
            return "DATABASE()"
        return "1"

    return MYBATIS_PARAM.sub(repl, sql)


def substitute_placeholders(sql: str) -> str:
    now = datetime.now()
    start = (now - timedelta(days=30)).strftime("%Y-%m-%d %H:%M:%S")
    end = now.strftime("%Y-%m-%d %H:%M:%S")
    upper = sql.upper()
    idx = 0

    def repl(_: re.Match) -> str:
        nonlocal idx
        prefix = upper[max(0, idx - 40) : idx + 1]
        idx += 1
        if " LIMIT " in prefix or prefix.rstrip().endswith("LIMIT"):
            return "10"
        if " OFFSET " in prefix or prefix.rstrip().endswith("OFFSET"):
            return "0"
        if " LIKE " in prefix or prefix.rstrip().endswith("LIKE"):
            return "'Threads_connected'"
        if " SCHEMA" in prefix or "TABLE_SCHEMA" in prefix:
            return f"'{DEFAULT_DB}'"
        if " CATEGORY" in prefix:
            return "'hikari'"
        if " METRIC_KEY" in prefix or " METRIC_NAME" in prefix:
            return "'total_connections'"
        if " TYPE " in prefix and "conversation" in sql.lower():
            return "'group'"
        if idx % 2 == 1:
            return f"'{start}'"
        return f"'{end}'"

    return re.sub(r"\?", repl, sql)


def finalize_sql(raw: str) -> str:
    sql = normalize_sql(raw)
    sql = substitute_mybatis(sql)
    sql = substitute_placeholders(sql)
    sql = re.sub(r"\+\s*timeClause\b", "create_time >= NOW() - INTERVAL 30 DAY", sql)
    sql = re.sub(r"\btimeClause\b", "create_time >= NOW() - INTERVAL 30 DAY", sql)
    return sql.strip()


def is_complete_sql(sql: str) -> bool:
    if not SQL_HEAD.match(sql):
        return False
    if len(sql.split()) < 4:
        return False
    bad_endings = (
        " WHERE",
        " AND",
        " OR",
        " SET",
        " JOIN",
        " ON",
        " FROM",
        ",",
        "(",
    )
    upper = sql.upper().rstrip()
    for ending in bad_endings:
        if upper.endswith(ending):
            return False
  # dailyCounts fragments without GROUP BY
    if re.search(r"SELECT\s+DATE\([^)]+\)\s+AS\s+\w+,\s+COUNT\(\*\)", sql, re.I):
        if "GROUP BY" not in sql.upper():
            return False
    return True


def sql_kind(sql: str) -> str:
    head = sql.lstrip().upper()
    if head.startswith(("SELECT", "WITH", "SHOW")):
        return "select"
    return "write"


def expand_time_clause_variants(sql: str) -> list[str]:
    if "timeClause" not in sql:
        return [finalize_sql(sql)]
    out = [
        finalize_sql(sql.replace("timeClause", "create_time >= NOW() - INTERVAL 30 DAY")),
        finalize_sql(
            sql.replace("timeClause", "create_time >= ? AND create_time < ?")
        ),
        finalize_sql(sql.replace("timeClause", "create_time >= ?")),
    ]
    return list(dict.fromkeys(out))


def collect_from_java(path: Path) -> list[tuple[str, str]]:
    text = path.read_text(encoding="utf-8", errors="ignore")
    rel = str(path.relative_to(ROOT))
    found: list[tuple[str, str]] = []

    for m in ANNOTATION_START.finditer(text):
        sql = extract_annotation_sql(text, m.start())
        if sql:
            found.append((sql, rel))

    for m in TEXT_BLOCK.finditer(text):
        found.append((normalize_sql(m.group(1)), rel))

    for m in re.finditer(
        r'"((?:SELECT|INSERT|UPDATE|DELETE|SHOW)[^"]*(?:\s*\+\s*"[^"]*)*)"',
        text,
        re.I,
    ):
        for v in expand_time_clause_variants(m.group(1)):
            found.append((v, rel))

    return found


def collect_entity_baselines() -> list[tuple[str, str]]:
    baselines: list[tuple[str, str]] = []
    entity_dir = JAVA_ROOT / "com" / "linkx" / "server" / "entity"
    for path in entity_dir.rglob("*.java"):
        text = path.read_text(encoding="utf-8", errors="ignore")
        m = TABLE_ANNOTATION.search(text)
        if not m:
            continue
        table = m.group(1)
        rel = str(path.relative_to(ROOT))
        baselines.append((f"SELECT COUNT(*) FROM {table}", f"{rel}#count"))
        baselines.append((f"SELECT * FROM {table} LIMIT 10", f"{rel}#sample"))
        if "deleted" in text:
            baselines.append(
                (f"SELECT COUNT(*) FROM {table} WHERE deleted = 0", f"{rel}#count_active")
            )
    return baselines


def dedupe_entries(items: Iterable[tuple[str, str]]) -> list[SqlEntry]:
    seen: set[str] = set()
    out: list[SqlEntry] = []
    for raw, source in items:
        sql = finalize_sql(raw)
        if not is_complete_sql(sql):
            continue
        key = re.sub(r"\s+", " ", sql.lower())
        if key in seen:
            continue
        seen.add(key)
        out.append(SqlEntry(sql=sql, source=source, kind=sql_kind(sql)))
    return sort_entries(out)


def entry_weight(entry: SqlEntry) -> tuple[int, str]:
    sql = entry.sql.lower()
    # heavy / cross-db queries last so the report fills in sooner
    if "snail_job." in sql or "performance_schema." in sql:
        return (2, entry.source)
    if "#count" in entry.source or "#sample" in entry.source:
        return (1, entry.source)
    return (0, entry.source)


def sort_entries(entries: list[SqlEntry]) -> list[SqlEntry]:
    return sorted(entries, key=entry_weight)


def benchmark(conn, entry: SqlEntry, iterations: int, *, max_total_ms: float = 0) -> dict:
    cur = conn.cursor()
    total_ms = 0.0
    min_ms = float("inf")
    max_ms = 0.0
    errors = 0
    last_error = ""
    ok_runs = 0
    truncated = False
    needs_tx = entry.kind == "write" or " FOR UPDATE" in entry.sql.upper()

    try:
        for _ in range(iterations):
            if max_total_ms > 0 and ok_runs > 0 and total_ms >= max_total_ms:
                truncated = True
                break
            if needs_tx:
                conn.begin()
            t0 = time.perf_counter()
            try:
                cur.execute(entry.sql)
                if entry.kind == "select":
                    cur.fetchall()
                ok_runs += 1
            except Exception as ex:  # noqa: BLE001
                errors += 1
                last_error = str(ex)[:240]
                if needs_tx:
                    conn.rollback()
                break
            finally:
                if needs_tx:
                    conn.rollback()
            elapsed = (time.perf_counter() - t0) * 1000
            total_ms += elapsed
            min_ms = min(min_ms, elapsed)
            max_ms = max(max_ms, elapsed)
    finally:
        cur.close()

    avg_ms = total_ms / ok_runs if ok_runs else 0.0
    return {
        "sql": entry.sql,
        "source": entry.source,
        "kind": entry.kind,
        "iterations": iterations,
        "ok_runs": ok_runs,
        "errors": errors,
        "error": last_error,
        "truncated": truncated,
        "total_ms": total_ms,
        "avg_ms": avg_ms,
        "min_ms": 0.0 if min_ms == float("inf") else min_ms,
        "max_ms": max_ms,
    }


def write_full_report(path: Path, results: list[dict], failed: list[dict], iterations: int) -> None:
    with path.open("w", encoding="utf-8") as f:
        f.write("SQL Benchmark Full Ranking\n")
        f.write(f"iterations_target={iterations} success={len(results)} failed={len(failed)}\n\n")
        for idx, r in enumerate(results, 1):
            note = " (truncated)" if r.get("truncated") else ""
            f.write(
                f"#{idx} avg={r['avg_ms']:.4f}ms min={r['min_ms']:.4f} "
                f"max={r['max_ms']:.4f}ms runs={r['ok_runs']}{note} [{r['source']}]\n"
            )
            f.write(r["sql"] + "\n\n")
        if failed:
            f.write("\n--- FAILED ---\n")
            for r in failed:
                f.write(f"[{r['source']}] {r['error']}\n{r['sql']}\n\n")


def write_report(
    path: Path,
    results: list[dict],
    failed: list[dict],
    iterations: int,
    slow_ms: float,
    *,
    partial: bool = False,
) -> None:
    slow = [r for r in results if r["avg_ms"] >= slow_ms]
    with path.open("w", encoding="utf-8") as f:
        title = "SQL Benchmark Report (partial)" if partial else "SQL Benchmark Report"
        f.write(title + "\n")
        f.write(f"iterations={iterations} slow_threshold_ms={slow_ms}\n")
        f.write(f"success={len(results)} failed={len(failed)} slow={len(slow)}\n\n")
        for r in slow:
            f.write(
                f"avg={r['avg_ms']:.4f}ms min={r['min_ms']:.4f} max={r['max_ms']:.4f} "
                f"total={r['total_ms']:.2f}ms [{r['source']}]\n"
            )
            f.write(r["sql"] + "\n\n")
        if failed:
            f.write("\n--- FAILED ---\n")
            for r in failed:
                f.write(f"[{r['source']}] {r['error']}\n{r['sql']}\n\n")


def load_env_local() -> None:
    env_path = ROOT / ".env.local"
    if not env_path.exists():
        return
    for line in env_path.read_text(encoding="utf-8", errors="ignore").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, _, v = line.partition("=")
        k, v = k.strip(), v.strip()
        if k == "DB_URL" and "jdbc:mysql://" in v:
            m = re.search(r"jdbc:mysql://([^:/]+):?(\d+)?/([^?]+)", v)
            if m:
                os.environ.setdefault("DB_HOST", m.group(1))
                if m.group(2):
                    os.environ.setdefault("DB_PORT", m.group(2))
                os.environ.setdefault("DB_NAME", m.group(3))
        elif k == "DB_USERNAME":
            os.environ.setdefault("DB_USERNAME", v)
        elif k == "DB_PASSWORD":
            os.environ.setdefault("DB_PASSWORD", v)


def main() -> int:
    parser = argparse.ArgumentParser(description="Benchmark project SQL statements")
    parser.add_argument("--iterations", type=int, default=1000)
    parser.add_argument("--slow-ms", type=float, default=5.0)
    parser.add_argument("--top", type=int, default=50)
    parser.add_argument("--skip-write", action="store_true")
    parser.add_argument(
        "--max-total-ms",
        type=float,
        default=15000,
        help="Stop benchmarking a single SQL after this many ms of successful runs (0=unlimited)",
    )
    args = parser.parse_args()

    load_env_local()

    java_files = list(JAVA_ROOT.rglob("*.java"))
    collected: list[tuple[str, str]] = []
    for p in java_files:
        collected.extend(collect_from_java(p))
    collected.extend(collect_entity_baselines())

    entries = dedupe_entries(collected)
    if args.skip_write:
        entries = [e for e in entries if e.kind == "select"]

    log(f"Collected {len(entries)} unique SQL statements from {len(java_files)} Java files")
    log(
        f"Benchmarking each {args.iterations} times against "
        f"{os.environ.get('DB_HOST', DEFAULT_HOST)}:{os.environ.get('DB_PORT', DEFAULT_PORT)}/"
        f"{os.environ.get('DB_NAME', DEFAULT_DB)} ..."
    )

    conn = pymysql.connect(
        host=os.environ.get("DB_HOST", DEFAULT_HOST),
        port=int(os.environ.get("DB_PORT", DEFAULT_PORT)),
        user=os.environ.get("DB_USERNAME", DEFAULT_USER),
        password=os.environ.get("DB_PASSWORD", DEFAULT_PASSWORD),
        database=os.environ.get("DB_NAME", DEFAULT_DB),
        charset="utf8mb4",
        autocommit=False,
    )

    results: list[dict] = []
    failed: list[dict] = []
    started = time.time()

    report_path = ROOT / "scripts" / "sql-benchmark-report.txt"
    full_report_path = ROOT / "scripts" / "sql-benchmark-full.txt"

    for i, entry in enumerate(entries, 1):
        short_src = entry.source.replace("\\", "/").split("/")[-1]
        log(f"  [{i}/{len(entries)}] {short_src}")
        r = benchmark(
            conn,
            entry,
            args.iterations,
            max_total_ms=args.max_total_ms,
        )
        if r["errors"]:
            failed.append(r)
        else:
            results.append(r)
        if i % 10 == 0 or i == len(entries):
            partial = sorted(results, key=lambda x: x["avg_ms"], reverse=True)
            write_report(report_path, partial, failed, args.iterations, args.slow_ms, partial=True)
            log(f"  progress: {i}/{len(entries)} (saved partial report)")

    conn.close()
    elapsed = time.time() - started

    results.sort(key=lambda x: x["avg_ms"], reverse=True)
    slow = [r for r in results if r["avg_ms"] >= args.slow_ms]

    log("\n" + "=" * 100)
    log(
        f"Done in {elapsed:.1f}s | success={len(results)} failed={len(failed)} "
        f"slow(>={args.slow_ms}ms)={len(slow)}"
    )
    log("=" * 100)

    log(f"\n## Top slow SQL (avg >= {args.slow_ms} ms, showing up to {args.top})\n")
    for idx, r in enumerate(slow[: args.top], 1):
        sql_preview = r["sql"] if len(r["sql"]) <= 200 else r["sql"][:197] + "..."
        log(
            f"{idx:3}. avg={r['avg_ms']:.3f}ms  min={r['min_ms']:.3f}ms  "
            f"max={r['max_ms']:.3f}ms  total={r['total_ms']:.1f}ms"
        )
        log(f"     source: {r['source']}")
        log(f"     sql: {sql_preview}\n")

    if failed:
        log(f"\n## Failed SQL ({len(failed)})\n")
        for r in failed[:20]:
            log(f"- {r['source']}: {r['error']}")
            log(f"  {r['sql'][:140]}\n")

    write_report(report_path, results, failed, args.iterations, args.slow_ms)
    write_full_report(full_report_path, results, failed, args.iterations)
    log(f"Slow SQL report written to: {report_path}")
    log(f"Full ranking report written to: {full_report_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
