#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."

# Docker Desktop's service may start before its daemon is ready.
ready=false
deadline=$((SECONDS + 180))
while ((SECONDS < deadline)); do
  if timeout 5 docker --context desktop-linux info >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 2
done
if [[ "$ready" != true ]]; then
  echo 'Docker Desktop did not become ready within 180 seconds.' >&2
  exit 1
fi

# Startup uses the previously built image; it never builds or pulls new code.
docker --context desktop-linux compose -p chubb-review up --no-build --pull never -d
curl --retry 60 --retry-all-errors --retry-delay 2 --retry-max-time 180 --max-time 5 -fsS http://127.0.0.1:18080/actuator/health
