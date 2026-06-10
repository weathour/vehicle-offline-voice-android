#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

HOST="127.0.0.1"
PORT="${SIM_REDIS_SMOKE_PORT:-6387}"
LOG="$(mktemp)"
python3 scripts/sim_redis_server.py --host "$HOST" --port "$PORT" >"$LOG" 2>&1 &
PID=$!
cleanup() {
  kill "$PID" >/dev/null 2>&1 || true
  rm -f "$LOG"
}
trap cleanup EXIT

for _ in {1..30}; do
  if python3 - <<PY >/dev/null 2>&1
import socket
s=socket.create_connection(("$HOST", $PORT), timeout=0.2)
s.close()
PY
  then
    break
  fi
  sleep 0.1
done

python3 scripts/sim_vehicle_redis.py --host "$HOST" --port "$PORT" defaults
python3 scripts/sim_vehicle_redis.py --host "$HOST" --port "$PORT" status | tee /tmp/sim_vehicle_redis_status.txt
python3 scripts/sim_vehicle_redis.py --host "$HOST" --port "$PORT" set-speed --value 36.8
python3 scripts/sim_vehicle_redis.py --host "$HOST" --port "$PORT" get --key BC_Veh_Spd | tee /tmp/sim_vehicle_redis_get.txt
python3 scripts/sim_vehicle_redis.py --host "$HOST" --port "$PORT" set-tire --alarm low --pressure 650
python3 scripts/sim_vehicle_redis.py --host "$HOST" --port "$PORT" set-sam --scene 11 --event start --count 3
python3 scripts/sim_vehicle_redis.py --host "$HOST" --port "$PORT" set-lane --count 2 --confidence 0.8
python3 scripts/sim_vehicle_redis.py --host "$HOST" --port "$PORT" set-trajectory
python3 scripts/sim_vehicle_redis.py --host "$HOST" --port "$PORT" corrupt --key BC_Veh_Spd
python3 scripts/sim_vehicle_redis.py --host "$HOST" --port "$PORT" delete --key Sensor_SAM
python3 scripts/sim_vehicle_redis.py --host "$HOST" --port "$PORT" status

grep -q 'BC_Veh_Spd' /tmp/sim_vehicle_redis_status.txt
grep -q 'DCU_INFO_St' /tmp/sim_vehicle_redis_status.txt
grep -q 'Sensor_TrafficLightlist' /tmp/sim_vehicle_redis_status.txt
grep -q 'Sensor_Lanelist' /tmp/sim_vehicle_redis_status.txt
grep -q 'planned_trajectory' /tmp/sim_vehicle_redis_status.txt
grep -q 'Sensor_SAM' /tmp/sim_vehicle_redis_status.txt
grep -q 'hex=' /tmp/sim_vehicle_redis_get.txt
echo "sim Redis smoke PASS on ${HOST}:${PORT}"
