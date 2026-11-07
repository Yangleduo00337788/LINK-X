#!/usr/bin/env bash
set -euo pipefail
BIN="${HOME}/.local/bin"
DATA="${HOME}/minio-data"
mkdir -p "${BIN}" "${DATA}"
if [ ! -x "${BIN}/minio" ]; then
  curl -fL --retry 3 -o "${BIN}/minio" "https://github.com/minio/minio/releases/download/RELEASE.2024-11-07T00-52-20Z/minio.linux-amd64.RELEASE.2024-11-07T00-52-20Z"
  chmod +x "${BIN}/minio"
fi
pkill -f "minio server" 2>/dev/null || true
export MINIO_ROOT_USER=admin
export MINIO_ROOT_PASSWORD=admin123
nohup "${BIN}/minio" server "${DATA}" --address 0.0.0.0:9000 --console-address 0.0.0.0:9001 >/tmp/minio-k6.log 2>&1 &
sleep 3
ss -lntp | grep 9000 || true
head -30 /tmp/minio-k6.log
