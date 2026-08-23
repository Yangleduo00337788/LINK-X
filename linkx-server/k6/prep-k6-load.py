"""
压测前准备：将本机 IP 加入限流白名单，并清理 k6 测试账号的登录/限流计数。
Usage: python prep-k6-load.py [--ip 127.0.0.1] [--username k6_load_test]
"""

from __future__ import annotations

import argparse
import socket
import sys


WHITELIST_KEY = "linkx:rate:whitelist"
RATE_PREFIX = "linkx:rate:"


def redis_cmd(conn: socket.socket, *parts: str) -> str:
    payload = f"*{len(parts)}\r\n"
    for p in parts:
        payload += f"${len(p.encode())}\r\n{p}\r\n"
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


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepare Redis for LinkX k6 load tests")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=6379)
    parser.add_argument("--ip", default="127.0.0.1", help="IP to whitelist for rate limits")
    parser.add_argument("--username", default="k6_load_test")
    args = parser.parse_args()

    conn = socket.create_connection((args.host, args.port), timeout=5)
    try:
        pong = redis_cmd(conn, "PING")
        if "+PONG" not in pong:
            print(f"Redis PING unexpected: {pong.strip()}")
            return 1

        add = redis_cmd(conn, "SADD", WHITELIST_KEY, args.ip)
        print(f"whitelist SADD {args.ip}: {add.strip()}")

        patterns = [
            f"{RATE_PREFIX}*login*",
            f"{RATE_PREFIX}*k6*",
            f"{RATE_PREFIX}biz:*{args.username}*",
        ]
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

        # 扫描并删除该用户 id 维度的 biz 限流键（若存在）
        user_id = None
        try:
            import pymysql

            c = pymysql.connect(
                host="127.0.0.1",
                port=3306,
                user="root",
                password="root",
                database="linkx",
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
            scan = redis_cmd(conn, "KEYS", f"{RATE_PREFIX}biz:*:{user_id}")
            if scan.startswith("*"):
                lines = [ln.strip() for ln in scan.splitlines() if ln.startswith("$")]
                # KEYS 返回 bulk strings — 用 SCAN 简化：直接删常见 scope
                for scope in ("global-default", "chat:list", "friend:list"):
                    key = f"{RATE_PREFIX}biz:{scope}:{user_id}"
                    resp = redis_cmd(conn, "DEL", key)
                    if ":1" in resp:
                        deleted += 1

        redis_cmd(conn, "DEL", f"{RATE_PREFIX}login-request:client:ip:{args.ip}")

        print(f"deleted {deleted} rate-limit keys (partial)")
        print("prep complete — 127.0.0.1 should bypass RateLimitInterceptor when whitelisted")
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
