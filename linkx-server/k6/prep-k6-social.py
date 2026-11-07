#!/usr/bin/env python3
"""Seed group / conference / moments / notes / calendar / favorites for social k6."""
from __future__ import annotations

import json
import os
import re
import sys
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.abspath(__file__))
MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
PEER_ID = 9999999900003
STRANGER_ID = 9999999900005
MAIN_ID = 9999999900001
ROLE_ID = 1002
GROUP_NAME = "k6-social-group"
DATE = "2026-11-07"
EXTRA_MEMBER_IDS = [9999999900101 + i for i in range(8)]


def load_k6_env() -> dict[str, str]:
    path = os.path.join(ROOT, "k6.env")
    values: dict[str, str] = {}
    if not os.path.exists(path):
        return values
    with open(path, encoding="utf-8") as f:
        for raw in f:
            line = raw.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def upsert_env(updates: dict[str, str]) -> None:
    path = os.path.join(ROOT, "k6.env")
    lines: list[str] = []
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            lines = f.read().splitlines()
    keys = set(updates)
    out: list[str] = []
    seen: set[str] = set()
    for line in lines:
        stripped = line.strip()
        if stripped and not stripped.startswith("#") and "=" in stripped:
            key = stripped.split("=", 1)[0].strip()
            if key in keys:
                out.append(f"{key}={updates[key]}")
                seen.add(key)
                continue
        out.append(line)
    for key, value in updates.items():
        if key not in seen:
            out.append(f"{key}={value}")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")


def mysql(sql: str) -> str:
    import subprocess

    proc = subprocess.run(
        [MYSQL, "-h127.0.0.1", "-uroot", "-proot", "--database=linkx", "-N", "-e", sql],
        capture_output=True,
        text=True,
        check=False,
    )
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr or proc.stdout)
    return (proc.stdout or "").strip()


def ensure_peer() -> None:
    pw = mysql("SELECT password FROM sys_user WHERE id=9999999900001")
    if not pw:
        raise RuntimeError("k6_load_test missing — create it before social prep")
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
"""
    )
    print("peer user + friendship ok")


def ensure_users() -> None:
    pw = mysql("SELECT password FROM sys_user WHERE id=9999999900001")
    if not pw:
        raise RuntimeError("k6_load_test missing — create it before social prep")
    pw_sql = pw.replace("\\", "\\\\").replace("'", "''")
    mysql(
        f"""
INSERT INTO sys_user (id, username, password, nickname, status, email, deleted, create_time, update_time)
VALUES ({STRANGER_ID}, 'k6_load_stranger', '{pw_sql}', 'k6 stranger', 1, 'k6_load_stranger@linkx.local', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE password=VALUES(password), deleted=0, status=1;
INSERT INTO sys_user_role (id, user_id, role_id, deleted)
VALUES (9999999900006, {STRANGER_ID}, {ROLE_ID}, 0)
ON DUPLICATE KEY UPDATE deleted=0, role_id={ROLE_ID};
"""
    )
    for i, uid in enumerate(EXTRA_MEMBER_IDS):
        mysql(
            f"""
INSERT INTO sys_user (id, username, password, nickname, status, email, deleted, create_time, update_time)
VALUES ({uid}, 'k6_member_{i}', '{pw_sql}', 'k6 member {i}', 1, 'k6_member_{i}@linkx.local', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE password=VALUES(password), deleted=0, status=1;
INSERT INTO sys_user_role (id, user_id, role_id, deleted)
VALUES ({uid + 50}, {uid}, {ROLE_ID}, 0)
ON DUPLICATE KEY UPDATE deleted=0, role_id={ROLE_ID};
INSERT INTO sys_user_relation (id, user_id, friend_id, status, deleted, create_time, update_time)
VALUES ({uid + 100}, {MAIN_ID}, {uid}, 1, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE status=1, deleted=0;
INSERT INTO sys_user_relation (id, user_id, friend_id, status, deleted, create_time, update_time)
VALUES ({uid + 200}, {uid}, {MAIN_ID}, 1, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE status=1, deleted=0;
"""
        )
    print("stranger + extra members ok")


def snowflake(payload: object, key: str = "id") -> str:
    raw = payload if isinstance(payload, str) else json.dumps(payload, ensure_ascii=False)
    m = re.search(rf'"{key}"\s*:\s*"?(\d{{15,}})"?', raw)
    if not m:
        raise RuntimeError(f"no snowflake {key} in {raw[:400]}")
    return m.group(1)


def api(method: str, path: str, token: str | None = None, body: dict | None = None, base: str = "") -> tuple[dict, str]:
    url = base.rstrip("/") + path
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("X-Device-Id", "k6-shared-session")
    req.add_header("X-Device-Name", "k6-load-test")
    req.add_header("X-Device-Type", "desktop")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            text = resp.read().decode()
            return json.loads(text), text
    except urllib.error.HTTPError as e:
        raise RuntimeError(e.read().decode()) from e


def login(base: str, username: str, password: str) -> str:
    body, _ = api("POST", "/auth/login", body={"username": username, "password": password}, base=base)
    if body.get("code") != 200:
        raise RuntimeError(body)
    return body["data"]["accessToken"]


def ensure_group(base: str, token: str) -> str:
    listed, raw = api("GET", "/group/list", token=token, base=base)
    if listed.get("code") == 200:
        for item in listed.get("data") or []:
            if isinstance(item, dict) and item.get("name") == GROUP_NAME:
                gid = snowflake(json.dumps(item), "id")
                print("reuse group", gid)
                return gid
        if listed.get("data"):
            try:
                gid = snowflake(raw, "id")
                print("reuse first group", gid)
                return gid
            except RuntimeError:
                pass
    created, raw = api(
        "POST",
        "/group",
        token=token,
        body={"name": GROUP_NAME, "memberIds": [PEER_ID]},
        base=base,
    )
    if created.get("code") != 200:
        raise RuntimeError(created)
    gid = snowflake(raw, "id")
    print("created group", gid)
    return gid


def ensure_group_members(base: str, token: str, group_id: str) -> None:
    added, _ = api(
        "POST",
        f"/group/{group_id}/members",
        token=token,
        body={"memberIds": EXTRA_MEMBER_IDS},
        base=base,
    )
    if added.get("code") != 200:
        print("add members skipped:", added.get("message") or added.get("code"))
        return
    print("group extra members ok")


def ensure_announcement(base: str, token: str, group_id: str) -> None:
    listed, _ = api("GET", f"/group/{group_id}/announcements", token=token, base=base)
    if listed.get("code") == 200 and listed.get("data"):
        print("announcement exists")
        return
    created, _ = api(
        "POST",
        f"/group/{group_id}/announcements",
        token=token,
        body={"content": "k6 social announcement", "pinned": True},
        base=base,
    )
    if created.get("code") != 200:
        raise RuntimeError(created)
    print("created announcement")


def ensure_conference(base: str, token: str, group_id: str) -> str:
    active, raw = api(
        "GET",
        f"/conference/active-in-conversation?conversationId={group_id}",
        token=token,
        base=base,
    )
    if active.get("code") == 200 and active.get("data"):
        cid = snowflake(raw, "id")
        print("reuse conference", cid)
        return cid
    created, raw = api(
        "POST",
        "/conference/create",
        token=token,
        body={
            "conversationId": int(group_id),
            "type": "video",
            "scene": "meeting",
            "title": "k6 social meeting",
        },
        base=base,
    )
    if created.get("code") != 200:
        raise RuntimeError(created)
    cid = snowflake(raw, "id")
    print("created conference", cid)
    return cid


def ensure_peer_in_conference(base: str, password: str, conference_id: str) -> None:
    peer_token = login(base, "k6_load_peer", password)
    joined, _ = api(
        "POST",
        "/conference/join",
        token=peer_token,
        body={"conferenceId": conference_id},
        base=base,
    )
    print("peer join conference", joined.get("code"), joined.get("message") or "ok")


def ensure_moments(base: str, token: str) -> str:
    listed, raw = api("GET", "/moments?limit=5", token=token, base=base)
    if listed.get("code") == 200 and listed.get("data"):
        pid = snowflake(raw, "id")
        print("reuse moments", pid)
        return pid
    created, raw = api(
        "POST",
        "/moments",
        token=token,
        body={"content": "k6 social seed post", "visibility": 0},
        base=base,
    )
    if created.get("code") != 200:
        raise RuntimeError(created)
    pid = snowflake(raw, "id")
    print("created moments", pid)
    return pid


def ensure_note(base: str, token: str) -> str:
    listed, raw = api("GET", "/notes", token=token, base=base)
    if listed.get("code") == 200 and listed.get("data"):
        nid = snowflake(raw, "id")
        print("reuse note", nid)
        return nid
    created, raw = api(
        "POST",
        "/notes",
        token=token,
        body={"title": "k6 note", "content": "k6 social seed note", "type": "note"},
        base=base,
    )
    if created.get("code") != 200:
        raise RuntimeError(created)
    nid = snowflake(raw, "id")
    print("created note", nid)
    return nid


def ensure_calendar(base: str, token: str) -> str:
    listed, raw = api("GET", f"/calendar/date/{DATE}", token=token, base=base)
    if listed.get("code") == 200 and listed.get("data"):
        eid = snowflake(raw, "id")
        print("reuse calendar", eid)
        return eid
    created, raw = api(
        "POST",
        "/calendar",
        token=token,
        body={"title": "k6 event", "date": DATE, "time": "18:00", "endTime": "19:00", "color": "#3b82f6"},
        base=base,
    )
    if created.get("code") != 200:
        raise RuntimeError(created)
    eid = snowflake(raw, "id")
    print("created calendar", eid)
    return eid


def ensure_short_video(base: str, token: str) -> str:
    listed, raw = api("GET", "/short-video?limit=5", token=token, base=base)
    if listed.get("code") == 200 and listed.get("data"):
        pid = snowflake(raw, "id")
        print("reuse short-video", pid)
        return pid
    print("no short-video post — interact scenario will fail setup")
    return ""


def ensure_favorite(base: str, token: str) -> str:
    listed, raw = api("GET", "/favorites?limit=5", token=token, base=base)
    if listed.get("code") == 200 and listed.get("data"):
        fid = snowflake(raw, "id")
        print("reuse favorite", fid)
        return fid
    created, raw = api(
        "POST",
        "/favorites",
        token=token,
        body={"title": "k6 favorite", "content": "https://example.invalid/k6", "type": "link"},
        base=base,
    )
    if created.get("code") != 200:
        raise RuntimeError(created)
    fid = snowflake(raw, "id")
    print("created favorite", fid)
    return fid


def main() -> int:
    env = load_k6_env()
    base = env.get("K6_BASE_URL") or os.environ.get("K6_BASE_URL") or "http://127.0.0.1:18080/api"
    username = env.get("K6_USERNAME") or "k6_load_test"
    password = env.get("K6_PASSWORD") or "k6test123"
    ensure_peer()
    ensure_users()
    token = login(base, username, password)
    group_id = ensure_group(base, token)
    ensure_group_members(base, token, group_id)
    ensure_announcement(base, token, group_id)
    conference_id = ensure_conference(base, token, group_id)
    ensure_peer_in_conference(base, password, conference_id)
    moments_id = ensure_moments(base, token)
    note_id = ensure_note(base, token)
    calendar_id = ensure_calendar(base, token)
    favorite_id = ensure_favorite(base, token)
    short_video_id = ensure_short_video(base, token)
    env_ids = {
        "K6_GROUP_ID": group_id,
        "K6_CONFERENCE_ID": conference_id,
        "K6_MOMENTS_POST_ID": moments_id,
        "K6_NOTE_ID": note_id,
        "K6_CALENDAR_EVENT_ID": calendar_id,
        "K6_FAVORITE_ID": favorite_id,
        "K6_PEER_ID": str(PEER_ID),
        "K6_STRANGER_USERNAME": "k6_load_stranger",
    }
    if short_video_id:
        env_ids["K6_SHORT_VIDEO_POST_ID"] = short_video_id
    upsert_env(env_ids)
    print("wrote ids to k6.env")
    return 0


if __name__ == "__main__":
    sys.exit(main())
