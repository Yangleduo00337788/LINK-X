#!/usr/bin/env bash
# Curl each public URL; fail on 5xx or connection errors (4xx is OK).
set -euo pipefail

BASE_URL="${1:-http://127.0.0.1:8080/api}"
BASE_URL="${BASE_URL%/}"
LIST="$(cd "$(dirname "$0")" && pwd)/urls-public.txt"

fail=0
while IFS= read -r line || [[ -n "$line" ]]; do
  [[ -z "$line" || "$line" =~ ^# ]] && continue
  path="$line"
  [[ "$path" == /* ]] || path="/$path"
  url="${BASE_URL}${path}"
  code="$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 5 --max-time 15 "$url" || echo 000)"
  if [[ "$code" =~ ^[23][0-9][0-9]$ || "$code" =~ ^4[0-9][0-9]$ ]]; then
    echo "OK  $code $url"
  else
    echo "FAIL $code $url"
    fail=1
  fi
done < "$LIST"

exit "$fail"
