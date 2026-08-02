#!/usr/bin/env bash
# 从已启动的 linkx-server 导出 OpenAPI 文档
# 用法: ./export-openapi.sh [baseUrl]
# 默认: http://127.0.0.1:8080/api

set -euo pipefail
BASE_URL="${1:-http://127.0.0.1:8080/api}"
OUT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT_FILE="${OUT_DIR}/openapi.json"

echo "Fetching OpenAPI from ${BASE_URL}/v3/api-docs ..."
curl -fsSL "${BASE_URL}/v3/api-docs" -o "${OUT_FILE}"
echo "Wrote ${OUT_FILE}"
node "$(dirname "$0")/generate-full-api.js" "${OUT_FILE}"
node "$(dirname "$0")/generate-postman-collection.mjs" --all
