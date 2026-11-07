#!/usr/bin/env bash
# LinkX k6 封装：读取 k6.env，可选执行 prep-k6-load.py
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"

SCENARIO="${1:-smoke}"
PROFILE="${K6_PROFILE:-load}"
EXPORT_SUMMARY="${EXPORT_SUMMARY:-0}"

if [[ -f k6.env ]]; then
  set -a
  # shellcheck disable=SC1091
  source k6.env
  set +a
fi

if ! command -v k6 >/dev/null 2>&1; then
  echo "未找到 k6，请先安装: https://k6.io/docs/get-started/installation/" >&2
  exit 1
fi

RATE_LIMIT_ENABLED="${RATE_LIMIT_ENABLED:-}"
if [[ -z "$RATE_LIMIT_ENABLED" && -f ../.env.k6 ]]; then
  RATE_LIMIT_ENABLED="$(grep -E '^RATE_LIMIT_ENABLED=' ../.env.k6 | tail -n1 | cut -d= -f2- | tr -d '[:space:]')"
fi
if [[ "${RATE_LIMIT_ENABLED}" == "false" ]]; then
  echo "RATE_LIMIT_ENABLED=false (k6 profile) — skip Redis whitelist prep"
elif command -v python >/dev/null 2>&1; then
  echo "Preparing rate-limit whitelist for k6..."
  python prep-k6-load.py || echo "warning: prep-k6-load.py 未完全成功。请改用 SPRING_PROFILES_ACTIVE=k6 启动后端。" >&2
fi

export K6_PROFILE="${PROFILE}"
ARGS=()
while IFS='=' read -r name value; do
  [[ -n "$name" && -n "$value" ]] && ARGS+=(-e "${name}=${value}")
done < <(env | grep '^K6_' || true)

if [[ "$SCENARIO" != "smoke" && ( -z "${K6_USERNAME:-}" || -z "${K6_PASSWORD:-}" ) ]]; then
  echo "非 smoke 场景需要 k6.env 中的 K6_USERNAME / K6_PASSWORD" >&2
  exit 1
fi

if [[ "$EXPORT_SUMMARY" == "1" ]]; then
  mkdir -p results
  stamp="$(date +%Y%m%d-%H%M%S)"
  ARGS+=(--summary-export "results/${SCENARIO}-${stamp}.json")
fi

echo "Running k6 scenario: ${SCENARIO}.js (profile=${PROFILE})"
exec k6 run "${ARGS[@]}" "${SCENARIO}.js"
