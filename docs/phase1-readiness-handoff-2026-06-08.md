# Phase 1 readiness handoff: read-only vehicle state / Redis / protobuf

Date: 2026-06-08
Project: `VehicleOfflineVoice`

## Phase 1 completion target

Phase 1 is the software and test-condition development stage before real vehicle access. The APK and desktop scripts now support:

- built-in simulated Redis/protobuf snapshots;
- phone-to-computer remote Redis reads through a minimal socket RESP client;
- desktop dependency-free Redis-compatible simulator;
- protobuf fixtures for Group A basic status and Group B explanation/cooperation status;
- non-mutating voice queries and Chinese summary replies;
- debug-panel visibility for source, connection, decoded count, basic status, warnings, and cooperation;
- negative-path handling for missing/corrupt keys and Redis connection errors.

## Scope and safety boundary

Implemented as **read-only vehicle state** only.

Not implemented:

- real vehicle control writes;
- voice binding to `CLOUD2VEH_REMOTECTL_1`;
- steering, throttle, brake, or gear publishing;
- real HVAC/window/door control writes, because the ingested docs do not provide safe write channels for those functions.

`android.permission.INTERNET` is now intentionally present so the phone APK can read the computer Redis simulator during Phase 2.

## Simulator key coverage

| Function | Redis key |
| --- | --- |
| speed | `BC_Veh_Spd` |
| gear/parking | `DCU_INFO_1` |
| auto-driving warning/explanation | `DCU_INFO_2` |
| SOC | `DCU_Battery_St` |
| remaining range | `DCU_INFO` |
| ACC/LKA/L2 | `DCU_L2_St` |
| AC temperatures | `ACM_INF2` |
| AC state | `ACM_INF4` |
| doors/horn/wiper | `BC_AutoD_Veh_St` |
| tire pressure/alarm | `TPMS_INFO` |
| location | `Sensor_Location` |
| obstacles | `Sensor_Mmobstacles` |
| traffic light | `Sensor_Trafficlightlist` |
| main obstacle/faults | `PFC_Main_Obstacle_INF` |
| cooperative driving | `Sam` |

`Sam` is a simulator/app default key based on the ingested `sam.proto`; confirm the real Redis key spelling in Phase 2.

## Desktop commands

Start the simulator for phone access:

```bash
cd /home/weathour/document/programs/chongqingUNITY/android-apk
SIM_REDIS_HOST=0.0.0.0 SIM_REDIS_PORT=6379 bash scripts/start_sim_redis.sh
```

In another terminal, write defaults:

```bash
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 defaults
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 status
```

Change values during Phase 2:

```bash
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-speed --value 36.8
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-battery --soc 64
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-ac --power on --temp 23 --fan 3
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-door --front open --mid closed
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-tire --alarm low --pressure 650
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-traffic-light --color red
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-obstacle --type pedestrian --x 8.5 --y 0.2
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-warning --auto-limit 32 --takeover 1 --low-fault 2
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-l2 --acc-status 7 --acc-fail 9 --lka-status 5 --lka-fail 12
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-perception-fault --camera 1 --radar 3 --fusion 1
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-sam --scene 11 --event start --count 3
```

Negative tests:

```bash
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 corrupt --key BC_Veh_Spd
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 delete --key Sam
```

Local script smoke:

```bash
bash scripts/smoke_sim_redis.sh
```

## Phone operation needed for Phase 2

When moving to Phase 2, user phone operation is needed here:

1. Keep `scripts/start_sim_redis.sh` running on the computer.
2. Confirm the computer Wi-Fi IP. Earlier observed value: `10.85.145.111`.
3. Install/open the debug APK on the phone.
4. In the APK top panel:
   - tick **读取电脑 Redis**;
   - host: `10.85.145.111` or the current computer Wi-Fi IP;
   - port: `6379`.
5. Start **真实麦克风手动验证** for real voice, or **Mock 预览/虚拟麦克风烟测** for controlled smoke.
6. Ask the Group A/B questions and change Redis values from the computer terminal.
7. Check the debug panel: `只读车况`, `告警解释`, `协作信息` should update after each query.

## Phase 2 entry criteria

Phase 2 may start when:

- local unit/build/lint/package/permission gates pass;
- simulator smoke passes;
- APK exists at `app/build/outputs/apk/debug/app-debug.apk`;
- phone has the remote Redis UI fields and can be pointed at the computer IP;
- no real vehicle control write path has been added.
