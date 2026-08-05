import bcrypt
import subprocess

users_raw = subprocess.check_output(
    [
        "mysql",
        "-uroot",
        "-proot",
        "-D",
        "linkx",
        "-N",
        "-e",
        "SELECT username,password FROM sys_user WHERE username IN ('admin','ops_admin','audit_admin','security_admin','readonly_observer')",
    ],
    text=True,
)
passwords = [
    "Test1234abcd",
    "Admin1234",
    "LinkX@1234",
    "admin12345",
    "12345678",
    "password",
    "Admin@123",
    "linkx1234",
    "OpsAdmin12",
    "AuditAdmin12",
    "SecurityAdmin12",
    "ReadonlyAdmin12",
    "LinkX1234!",
    "Aa123456",
    "admin1234",
    "LinkX@123",
    "Admin123!",
]
for line in users_raw.strip().split("\n"):
    if not line.strip():
        continue
    username, hashed = line.split("\t")
    for pwd in passwords:
        try:
            if bcrypt.checkpw(pwd.encode(), hashed.encode()):
                print(f"MATCH {username} / {pwd}")
        except Exception:
            pass
