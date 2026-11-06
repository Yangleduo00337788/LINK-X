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

ARGS=()
[[ -n "${K6_BASE_URL:-}" ]] && ARGS+=(-e "K6_BASE_URL=${K6_BASE_URL}")
[[ -n "${K6_USERNAME:-}" ]] && ARGS+=(-e "K6_USERNAME=${K6_USERNAME}")
[[ -n "${K6_PASSWORD:-}" ]] && ARGS+=(-e "K6_PASSWORD=${K6_PASSWORD}")
[[ -n "${K6_WS_URL:-}" ]] && ARGS+=(-e "K6_WS_URL=${K6_WS_URL}")
[[ -n "${K6_WS_ORIGIN:-}" ]] && ARGS+=(-e "K6_WS_ORIGIN=${K6_WS_ORIGIN}")
[[ -n "${K6_CONVERSATION_ID:-}" ]] && ARGS+=(-e "K6_CONVERSATION_ID=${K6_CONVERSATION_ID}")
ARGS+=(-e "K6_PROFILE=${PROFILE}")
[[ -n "${K6_VUS:-}" ]] && ARGS+=(-e "K6_VUS=${K6_VUS}")
[[ -n "${K6_DURATION:-}" ]] && ARGS+=(-e "K6_DURATION=${K6_DURATION}")
[[ -n "${K6_WS_SEND:-}" ]] && ARGS+=(-e "K6_WS_SEND=${K6_WS_SEND}")

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
