#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

HOST="${SIM_REDIS_HOST:-0.0.0.0}"
PORT="${SIM_REDIS_PORT:-6379}"

echo "Starting simulated Redis on ${HOST}:${PORT}"
echo "For phone access, keep this terminal running and set the APK Redis host to this computer's Wi-Fi IP."
echo "Security note: this simulator has no auth and should only be used on a trusted local test network."
exec python3 scripts/sim_redis_server.py --host "$HOST" --port "$PORT"
