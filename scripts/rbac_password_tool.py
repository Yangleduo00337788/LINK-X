import bcrypt
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BACKUP = ROOT / "scripts" / ".password_backup.json"
HASH = bcrypt.hashpw(b"Test1234abcd", bcrypt.gensalt(rounds=12)).decode()

USERS = ["admin", "ops_admin", "audit_admin", "security_admin", "readonly_observer"]


def mysql(query: str) -> str:
    return subprocess.check_output(
        ["mysql", "-uroot", "-proot", "-D", "linkx", "-N", "-e", query],
        text=True,
    )


def backup():
    rows = []
    for u in USERS:
        pwd = mysql(f"SELECT password FROM sys_user WHERE username='{u}'").strip()
        rows.append({"username": u, "password": pwd})
    BACKUP.write_text(json.dumps(rows, indent=2), encoding="utf-8")
    print(f"backed up {len(rows)} users -> {BACKUP}")


def set_temp():
    for u in USERS:
        mysql(f"UPDATE sys_user SET password='{HASH}' WHERE username='{u}'")
    print("set temporary password Test1234abcd for smoke users")


def restore():
    if not BACKUP.exists():
        raise SystemExit("backup missing")
    rows = json.loads(BACKUP.read_text(encoding="utf-8"))
    for row in rows:
        mysql(
            f"UPDATE sys_user SET password='{row['password']}' WHERE username='{row['username']}'"
        )
    print(f"restored {len(rows)} users")


if __name__ == "__main__":
    import sys

    cmd = sys.argv[1] if len(sys.argv) > 1 else "backup"
    if cmd == "backup":
        backup()
    elif cmd == "temp":
        backup()
        set_temp()
    elif cmd == "restore":
        restore()
    else:
        raise SystemExit("usage: rbac_password_tool.py [backup|temp|restore]")
