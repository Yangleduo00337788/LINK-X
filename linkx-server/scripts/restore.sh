#!/usr/bin/env bash
# =============================================================================
# LinkX 恢复脚本（慎用：会覆盖当前数据）
# -----------------------------------------------------------------------------
# 用法：./scripts/restore.sh backups/20260730_120000
# =============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SRC="${1:-}"
if [[ -z "$SRC" || ! -d "$SRC" ]]; then
  echo "Usage: $0 <backup-dir>" >&2
  exit 1
fi
SRC="$(cd "$SRC" && pwd)"

load_env_file() {
  local f="$1"
  [[ -f "$f" ]] || return 0
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
    if [[ "$line" =~ ^[A-Za-z_][A-Za-z0-9_]*= ]]; then
      export "$line"
    fi
  done < "$f"
}
load_env_file .env.local
load_env_file .env.prod
load_env_file .env

: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
: "${REDIS_PASSWORD:?REDIS_PASSWORD is required}"
: "${MINIO_ACCESS_KEY:?MINIO_ACCESS_KEY is required}"
: "${MINIO_SECRET_KEY:?MINIO_SECRET_KEY is required}"

NETWORK="${COMPOSE_NETWORK:-linkx-server_linkx-net}"
BUCKET="${MINIO_BUCKET_NAME:-linkx}"

echo "[restore] WARNING: overwrite MySQL/Redis/MinIO from: $SRC"
read -r -p "Type YES to continue: " confirm
[[ "$confirm" == "YES" ]] || { echo "aborted"; exit 1; }

if [[ -f "$SRC/mysql_linkx.sql.gz" ]]; then
  echo "[restore] MySQL..."
  gunzip -c "$SRC/mysql_linkx.sql.gz" | docker exec -i linkx-mysql \
    mysql -uroot -p"$MYSQL_ROOT_PASSWORD" linkx
fi

if [[ -f "$SRC/redis_dump.rdb" ]]; then
  echo "[restore] Redis..."
  docker exec linkx-redis redis-cli -a "$REDIS_PASSWORD" --no-auth-warning SHUTDOWN NOSAVE || true
  # 容器可能已退出，先确保可写 volume 再启动
  docker cp "$SRC/redis_dump.rdb" linkx-redis:/data/dump.rdb 2>/dev/null \
    || docker run --rm -v linkx-server_redis-data:/data -v "$SRC:/backup:ro" alpine \
         cp /backup/redis_dump.rdb /data/dump.rdb
  docker start linkx-redis >/dev/null || docker compose up -d redis
  sleep 2
fi

if [[ -d "$SRC/minio" ]]; then
  echo "[restore] MinIO..."
  docker run --rm --network "$NETWORK" \
    -v "$SRC/minio:/backup:ro" \
    -e MINIO_ACCESS_KEY -e MINIO_SECRET_KEY -e BUCKET="$BUCKET" \
    minio/mc:RELEASE.2024-05-09T17-04-24Z \
    /bin/sh -c 'mc alias set linkx http://minio:9000 "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" >/dev/null && mc mirror --overwrite --remove /backup "linkx/$BUCKET"'
fi

echo "[restore] done"
