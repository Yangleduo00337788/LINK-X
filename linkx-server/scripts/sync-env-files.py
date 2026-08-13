#!/usr/bin/env python3
"""按 env-spec 规范生成带分段、逐行注释的 .env 文件，并保留已有配置值。"""
from __future__ import annotations

import argparse
import re
from pathlib import Path

from env_spec import (
    ENV_ENTRIES,
    LOCAL_EXAMPLE_HEADER,
    LOCAL_HEADER,
    ORDER,
    PROD_EXAMPLE_HEADER,
    PROD_HEADER,
)

ROOT = Path(__file__).resolve().parents[1]


def parse_env(path: Path) -> dict[str, str]:
    data: dict[str, str] = {}
    if not path.exists():
        return data
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        if re.match(r"^[A-Z][A-Z0-9_]*=", stripped):
            key, value = stripped.split("=", 1)
            data[key] = value
    return data


def render_env(
    *,
    header: str,
    profile: str,
    existing: dict[str, str],
    use_example_defaults: bool,
) -> str:
    lines = [header, ""]
    for section, key, comments, local_default, prod_default in ENV_ENTRIES:
        if section:
            lines.append("")
            lines.append(f"# ---------- {section} ----------")
        for comment in comments:
            lines.append(f"# {comment}")
        template_default = local_default if profile == "local" else prod_default
        if use_example_defaults:
            value = existing.get(key, template_default)
        else:
            value = existing.get(key, template_default)
        lines.append(f"{key}={value}")
    lines.append("")
    return "\n".join(lines)


def write_target(path: Path, content: str) -> None:
    path.write_text(content, encoding="utf-8")
    print(f"wrote {path.name}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--examples",
        action="store_true",
        help="同时更新 .env.local.example / .env.prod.example（模板默认值）",
    )
    args = parser.parse_args()

    local_existing = parse_env(ROOT / ".env.local")
    prod_existing = parse_env(ROOT / ".env.prod")

    write_target(
        ROOT / ".env.local",
        render_env(
            header=LOCAL_HEADER,
            profile="local",
            existing=local_existing,
            use_example_defaults=False,
        ),
    )
    write_target(
        ROOT / ".env.prod",
        render_env(
            header=PROD_HEADER,
            profile="prod",
            existing=prod_existing,
            use_example_defaults=False,
        ),
    )

    if args.examples:
        write_target(
            ROOT / ".env.local.example",
            render_env(
                header=LOCAL_EXAMPLE_HEADER,
                profile="local",
                existing={},
                use_example_defaults=True,
            ),
        )
        write_target(
            ROOT / ".env.prod.example",
            render_env(
                header=PROD_EXAMPLE_HEADER,
                profile="prod",
                existing={},
                use_example_defaults=True,
            ),
        )


if __name__ == "__main__":
    main()
