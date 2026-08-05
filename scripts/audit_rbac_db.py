import json
import subprocess
from collections import defaultdict

USERS = [
    ("admin", "super_admin", 1001),
    ("ops_admin", "ops_admin", 1003),
    ("audit_admin", "audit_admin", 1004),
    ("security_admin", "security_admin", 1005),
    ("readonly_observer", "readonly_observer", 1006),
]

REVIEW_PERMS = {
    "admin:review:list",
    "admin:review:approve",
    "admin:approval:inbox",
    "admin:approval:action",
    "admin:approval-flow:list",
}


def mysql(query: str) -> str:
    return subprocess.check_output(
        ["mysql", "-uroot", "-proot", "-D", "linkx", "-N", "-B", "-e", query],
        text=True,
    )


def perms_for_role(role_id: int) -> set[str]:
    raw = mysql(
        f"""
        SELECT p.permission_code
        FROM sys_role_permission rp
        JOIN sys_permission p ON p.id = rp.permission_id AND p.status = 1
        WHERE rp.role_id = {role_id} AND rp.deleted = 0
        """
    )
    codes = {line.strip() for line in raw.splitlines() if line.strip()}
    if role_id == 1001:
        codes.add("*")
    return codes


def menus_for_role(role_id: int) -> set[str]:
    raw = mysql(
        f"""
        SELECT m.name
        FROM sys_admin_role_menu rm
        JOIN sys_admin_menu m ON m.id = rm.menu_id AND m.deleted = 0 AND m.status = 1
        WHERE rm.role_id = {role_id}
        """
    )
    return {line.strip() for line in raw.splitlines() if line.strip()}


def main():
    report = []
    for username, role_code, role_id in USERS:
        perms = sorted(perms_for_role(role_id))
        menus = sorted(menus_for_role(role_id))
        review = sorted(set(perms) & REVIEW_PERMS)
        report.append(
            {
                "username": username,
                "roleCode": role_code,
                "permissionCount": len(perms),
                "menuCount": len(menus),
                "reviewApprovalPermissions": review,
                "hasReviewList": "admin:review:list" in perms or "*" in perms,
                "hasApprovalInbox": "admin:approval:inbox" in perms or "*" in perms,
                "menus_sample": [m for m in menus if m in {
                    "review-task", "report-task", "announcement-review",
                    "approval-flows", "approval-inbox", "feedback", "risk-event",
                }],
            }
        )
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
