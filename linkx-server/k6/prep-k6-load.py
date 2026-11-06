"""
压测前准备：将本机 IP 加入限流白名单，并清理 k6 测试账号的登录/限流计数。
Usage: python prep-k6-load.py [--ip 127.0.0.1] [--username k6_load_test]
"""

from __future__ import annotations

import argparse
import os
import socket
import sys
from pathlib import Path


WHITELIST_KEY = "linkx:rate:whitelist"
RATE_PREFIX = "linkx:rate:"


def load_dotenv_files() -> None:
    here = Path(__file__).resolve().parent
    candidates = [
        here / "k6.env",
        here.parent / ".env.local",
        here.parent / ".env.docker",
    ]
    for path in candidates:
        if not path.is_file():
            continue
        for raw in path.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            name, value = line.split("=", 1)
            name = name.strip()
            value = value.strip().strip('"').strip("'")
            if name and name not in os.environ:
                os.environ[name] = value


def redis_cmd(conn: socket.socket, *parts: str) -> str:
    payload = f"*{len(parts)}\r\n"
    for p in parts:
        encoded = p.encode()
        payload += f"${len(encoded)}\r\n{p}\r\n"
    conn.sendall(payload.encode())
    data = b""
    while True:
        chunk = conn.recv(8192)
        if not chunk:
            break
        data += chunk
        if len(chunk) < 8192:
            break
    return data.decode("utf-8", errors="replace")


def redis_auth(conn: socket.socket, password: str) -> None:
    if not password:
        return
    resp = redis_cmd(conn, "AUTH", password)
    if "+OK" in resp:
        return
    resp = redis_cmd(conn, "AUTH", "default", password)
    if "+OK" not in resp:
        raise RuntimeError(f"Redis AUTH failed: {resp.strip()}")


def main() -> int:
    load_dotenv_files()
    parser = argparse.ArgumentParser(description="Prepare Redis for LinkX k6 load tests")
    parser.add_argument("--host", default=os.environ.get("REDIS_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("REDIS_PORT", "6379")))
    parser.add_argument("--password", default=os.environ.get("REDIS_PASSWORD", ""))
    parser.add_argument("--ip", default="127.0.0.1", help="IP to whitelist for rate limits")
    parser.add_argument("--username", default=os.environ.get("K6_USERNAME", "k6_load_test"))
    args = parser.parse_args()

    conn = socket.create_connection((args.host, args.port), timeout=5)
    try:
        redis_auth(conn, args.password)
        pong = redis_cmd(conn, "PING")
        if "+PONG" not in pong:
            print(f"Redis PING unexpected: {pong.strip()}")
            return 1

        add = redis_cmd(conn, "SADD", WHITELIST_KEY, args.ip)
        print(f"whitelist SADD {args.ip}: {add.strip()}")

        deleted = 0
        for username in {args.username, "k6_load_test"}:
            keys_to_del = [
                f"{RATE_PREFIX}linkx:login:fail:client:{username}",
                f"linkx:login:lock:client:{username}",
            ]
            for key in keys_to_del:
                resp = redis_cmd(conn, "DEL", key)
                if ":1" in resp:
                    deleted += 1

        user_id = None
        try:
            import pymysql

            c = pymysql.connect(
                host=os.environ.get("DB_HOST", "127.0.0.1"),
                port=int(os.environ.get("DB_PORT", "3306")),
                user=os.environ.get("DB_USERNAME", "root"),
                password=os.environ.get("DB_PASSWORD", os.environ.get("MYSQL_ROOT_PASSWORD", "root")),
                database=os.environ.get("DB_NAME", "linkx"),
                charset="utf8mb4",
            )
            cur = c.cursor()
            cur.execute("SELECT id FROM sys_user WHERE username=%s", (args.username,))
            row = cur.fetchone()
            c.close()
            if row:
                user_id = str(row[0])
        except Exception as ex:  # noqa: BLE001
            print(f"skip user biz key cleanup (db): {ex}")

        if user_id:
            for scope in ("global-default", "chat:list", "friend:list"):
                key = f"{RATE_PREFIX}biz:{scope}:{user_id}"
                resp = redis_cmd(conn, "DEL", key)
                if ":1" in resp:
                    deleted += 1

        redis_cmd(conn, "DEL", f"{RATE_PREFIX}login-request:client:ip:{args.ip}")

        print(f"deleted {deleted} rate-limit keys (partial)")
        print(f"prep complete — {args.ip} should bypass RateLimitInterceptor when whitelisted")
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
