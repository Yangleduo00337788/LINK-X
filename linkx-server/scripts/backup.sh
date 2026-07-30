#!/usr/bin/env bash
# =============================================================================
# LinkX 备份脚本（MySQL + Redis + MinIO）
# -----------------------------------------------------------------------------
# 用法（在 linkx-server 目录，Git Bash / WSL / Linux）：
#   chmod +x scripts/*.sh
#   ./scripts/backup.sh
#
# 环境变量：MYSQL_ROOT_PASSWORD、REDIS_PASSWORD、MINIO_ACCESS_KEY、MINIO_SECRET_KEY
# 可自动从 .env.local / .env.prod 加载。可选：BACKUP_DIR、BACKUP_KEEP、COMPOSE_NETWORK
# =============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

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

STAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="${BACKUP_DIR:-$ROOT_DIR/backups}/$STAMP"
NETWORK="${COMPOSE_NETWORK:-linkx-server_linkx-net}"
BUCKET="${MINIO_BUCKET_NAME:-linkx}"
mkdir -p "$OUT_DIR/minio"

echo "[backup] output -> $OUT_DIR"

echo "[backup] MySQL dump..."
docker exec linkx-mysql mysqldump \
  -uroot -p"$MYSQL_ROOT_PASSWORD" \
  --single-transaction --routines --triggers --hex-blob \
  linkx | gzip -c > "$OUT_DIR/mysql_linkx.sql.gz"

echo "[backup] Redis RDB..."
before="$(docker exec linkx-redis redis-cli -a "$REDIS_PASSWORD" --no-auth-warning LASTSAVE)"
docker exec linkx-redis redis-cli -a "$REDIS_PASSWORD" --no-auth-warning BGSAVE >/dev/null
for _ in $(seq 1 30); do
  sleep 1
  after="$(docker exec linkx-redis redis-cli -a "$REDIS_PASSWORD" --no-auth-warning LASTSAVE)"
  if [[ "$after" != "$before" ]]; then
    break
  fi
done
docker cp linkx-redis:/data/dump.rdb "$OUT_DIR/redis_dump.rdb"

echo "[backup] MinIO mirror..."
docker run --rm --network "$NETWORK" \
  -v "$OUT_DIR/minio:/backup" \
  -e MINIO_ACCESS_KEY -e MINIO_SECRET_KEY -e BUCKET="$BUCKET" \
  minio/mc:RELEASE.2024-05-09T17-04-24Z \
  /bin/sh -c 'mc alias set linkx http://minio:9000 "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" >/dev/null && mc mirror --quiet "linkx/$BUCKET" /backup'

if command -v sha256sum >/dev/null 2>&1; then
  (
    cd "$OUT_DIR"
    find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS
  )
fi

KEEP="${BACKUP_KEEP:-7}"
BACKUP_ROOT="${BACKUP_DIR:-$ROOT_DIR/backups}"
mapfile -t ALL < <(ls -1dt "$BACKUP_ROOT"/*/ 2>/dev/null || true)
if (( ${#ALL[@]} > KEEP )); then
  for old in "${ALL[@]:$KEEP}"; do
    echo "[backup] prune $old"
    rm -rf "$old"
  done
fi

echo "[backup] done: $OUT_DIR"
