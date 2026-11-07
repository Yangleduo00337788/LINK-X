#!/usr/bin/env python3
"""Prepare peer user, friendship, conversation, Redis register codes for write-path k6."""
from __future__ import annotations

import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.abspath(__file__))
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
PEER_ID = 9999999900003
MAIN_ID = 9999999900001
ROLE_ID = 1002
CODE = "123456"


def mysql(sql: str) -> str:
    proc = subprocess.run(
        [MYSQL, "-h127.0.0.1", "-uroot", "-proot", "--database=linkx", "-N", "-e", sql],
        capture_output=True,
        text=True,
        check=False,
    )
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr or proc.stdout)
    return (proc.stdout or "").strip()


def redis_cli_pipe(commands: str) -> None:
    proc = subprocess.run(
        [
            "wsl",
            "-d",
            "Ubuntu-22.04",
            "--",
            "/home/yangleduo/.local/bin/redis-cli",
            "-a",
            "admin123",
            "--no-auth-warning",
            "-n",
            "15",
            "--pipe",
        ],
        input=commands,
        capture_output=True,
        text=True,
        check=False,
    )
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr or proc.stdout)
    print((proc.stdout or "").strip() or "redis pipe ok")


def plant_dynamic_codes(max_vu: int = 250, max_iter: int = 200) -> None:
    lines = []
    for vu in range(1, max_vu + 1):
        for it in range(0, max_iter + 1):
            email = f"k6w{vu}x{it}@k6.invalid"
            lines.append(f"SET linkx:register-email:{email} {CODE} EX 3600")
    redis_cli_pipe("\n".join(lines) + "\n")
    print(f"redis codes for k6w{{vu}}x{{iter}} vu=1..{max_vu} iter=0..{max_iter}")


def ensure_peer() -> None:
    pw = mysql("SELECT password FROM sys_user WHERE id=9999999900001")
    if not pw:
        raise RuntimeError("k6_load_test missing")
    pw_sql = pw.replace("\\", "\\\\").replace("'", "''")
    mysql(
        f"""
INSERT INTO sys_user (id, username, password, nickname, status, email, deleted, create_time, update_time)
VALUES ({PEER_ID}, 'k6_load_peer', '{pw_sql}', 'k6 peer', 1, 'k6_load_peer@linkx.local', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE password=VALUES(password), deleted=0, status=1;
INSERT INTO sys_user_role (id, user_id, role_id, deleted)
VALUES (9999999900004, {PEER_ID}, {ROLE_ID}, 0)
ON DUPLICATE KEY UPDATE deleted=0, role_id={ROLE_ID};
INSERT INTO sys_user_relation (id, user_id, friend_id, status, deleted, create_time, update_time)
VALUES (9999999900010, {MAIN_ID}, {PEER_ID}, 1, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE status=1, deleted=0;
INSERT INTO sys_user_relation (id, user_id, friend_id, status, deleted, create_time, update_time)
VALUES (9999999900011, {PEER_ID}, {MAIN_ID}, 1, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE status=1, deleted=0;
INSERT INTO user_balance (id, user_id, balance, frozen, total_recharge, total_withdraw, create_time, update_time)
VALUES (9999999900020, {MAIN_ID}, 10000.00, 0, 10000.00, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE balance=GREATEST(balance, 1000);
INSERT INTO user_balance (id, user_id, balance, frozen, total_recharge, total_withdraw, create_time, update_time)
VALUES (9999999900021, {PEER_ID}, 1000.00, 0, 1000.00, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE balance=GREATEST(balance, 100);
"""
    )
    print("peer user + friendship + balance ok")


def api(method: str, path: str, token: str | None = None, body: dict | None = None) -> dict:
    url = "http://127.0.0.1:18080/api" + path
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("X-Device-Id", "k6-shared-session")
    req.add_header("X-Device-Name", "k6-load-test")
    req.add_header("X-Device-Type", "desktop")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        raise RuntimeError(e.read().decode()) from e


def open_conversation() -> str:
    login = api(
        "POST",
        "/auth/login",
        body={"username": "k6_load_test", "password": "k6test123"},
    )
    if login.get("code") != 200:
        raise RuntimeError(login)
    token = login["data"]["accessToken"]
    conv = api("POST", f"/chat/private/{PEER_ID}", token=token)
    if conv.get("code") != 200:
        raise RuntimeError(conv)
    cid = str(conv["data"]["id"])
    env_path = os.path.join(ROOT, "k6.env")
    lines = []
    if os.path.exists(env_path):
        with open(env_path, encoding="utf-8") as f:
            lines = f.read().splitlines()
    out = []
    found = False
    for line in lines:
        if line.startswith("K6_CONVERSATION_ID="):
            out.append(f"K6_CONVERSATION_ID={cid}")
            found = True
        else:
            out.append(line)
    if not found:
        out.append(f"K6_CONVERSATION_ID={cid}")
    with open(env_path, "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")
    print("conversationId", cid)
    return cid


def main() -> int:
    ensure_peer()
    plant_dynamic_codes()
    open_conversation()
    return 0


if __name__ == "__main__":
    sys.exit(main())
