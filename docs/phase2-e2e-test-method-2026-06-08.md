# Phase 2 E2E test method: phone reads computer Redis/protobuf

Date: 2026-06-08
APK installed on phone: `com.company.vehiclevoice.debug`
Phone ADB serial used: `10.85.250.222:38901`
Computer Wi-Fi IP: `10.85.145.111`
Redis simulator port: `6379`

## Goal

Prove the Phase 1 software path works end-to-end:

```text
Computer simulated Redis/protobuf -> phone APK remote Redis reader -> voice query -> TTS reply + debug panel update
```

The stage remains read-only. No vehicle control write path is tested.

## Roles

### Computer-side operations
Codex/operator can run these commands.

### Phone-side operations needed from user
Marked as **[PHONE]** below. These are needed because the APK UI, permission prompts, and speech input are on the phone.

## 0. Pre-check

```bash
cd /home/weathour/document/programs/chongqingUNITY/android-apk
source scripts/env.sh
$ANDROID_HOME/platform-tools/adb -s 10.85.250.222:38901 devices -l
```

Expected:

```text
10.85.250.222:38901 device ... model:V2509A
```

## 1. Start computer Redis simulator

Terminal A:

```bash
cd /home/weathour/document/programs/chongqingUNITY/android-apk
SIM_REDIS_HOST=0.0.0.0 SIM_REDIS_PORT=6379 bash scripts/start_sim_redis.sh
```

Keep this terminal open.

Terminal B:

```bash
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 defaults
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 status
```

Expected: 15 keys, including `BC_Veh_Spd`, `DCU_INFO_2`, `DCU_L2_St`, `TPMS_INFO`, `PFC_Main_Obstacle_INF`, `Sam`.

## 2. Phone app setup

**[PHONE]** In the APK:

1. Open `Vehicle Offline Voice`.
2. Tick **读取电脑 Redis**.
3. Host: `10.85.145.111`.
4. Port: `6379`.
5. Start **真实麦克风手动验证**.
6. If Android asks microphone/notification permissions, allow them.

Expected debug panel:

- service starts;
- source shows computer Redis / `10.85.145.111:6379` after the first read-only query;
- no Unity control JSON should appear for query intents.

## 3. Basic status matrix

For each row:

1. Computer updates Redis.
2. **[PHONE]** say wake phrase + query.
3. Verify phone reply and debug panel.

| ID | Computer command | Phone query | Expected phone signal |
| --- | --- | --- | --- |
| A1 speed | `python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-speed --value 36.8` | “小车小车，当前车速多少” | reply contains `36.8` |
| A2 battery | `python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-battery --soc 64` | “小车小车，电量多少” | reply contains `64.0%` |
| A3 range | `python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-range --km 88` | “小车小车，还能跑多远” | reply contains `88.0公里` |
| A4 AC | `python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-ac --power on --temp 23 --fan 3` | “小车小车，空调开了吗” | reply says AC on, fan 3 |
| A5 door | `python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-door --front open --mid closed` | “小车小车，车门关了吗” | reply says front door open |
| A6 tire | `python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-tire --alarm low --pressure 650` | “小车小车，胎压正常吗” | reply says tire pressure alarm / low pressure |
| A7 traffic | `python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-traffic-light --color red` | “小车小车，红绿灯” | reply says red light |
| A8 obstacle | `python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-obstacle --type pedestrian --x 8.5 --y 0.2` | “小车小车，前方有没有障碍物” | reply says pedestrian / 8.5m |

## 4. Explanation / warning / cooperation matrix

| ID | Computer command | Phone query | Expected phone signal |
| --- | --- | --- | --- |
| B1 autoD limit | `python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-warning --auto-limit 32 --takeover 1 --low-fault 2` | “小车小车，为什么不能进入自动驾驶” | reply says door not closed or related limit reason |
| B2 takeover | same as B1 | “小车小车，有没有需要接管” | reply says takeover alert |
| B3 ACC fail | `python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-l2 --acc-status 7 --acc-fail 9` | “小车小车，ACC状态” | reply says ACC fault / lane width not satisfied |
| B4 LKA fail | `python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-l2 --lka-status 5 --lka-fail 12` | “小车小车，LKA状态” | reply says LKA fault / lane confidence not satisfied |
| B5 perception fault | `python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-perception-fault --camera 1 --radar 3 --fusion 1` | “小车小车，有故障吗” | reply includes camera/radar/fusion fault |
| B6 Sam scene | `python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-sam --scene 11 --event start --count 3` | “小车小车，当前协作场景是什么” | reply says V2I dynamic speed limit |
| B7 Sam event | same as B6 | “小车小车，协作事件开始了吗” | reply says start |
| B8 Sam count | same as B6 | “小车小车，现在有几辆协作车” | reply says 3 vehicles |
| B9 Sam decision | same as B6 | “小车小车，引导决策是什么” | reply says guide decision / feedback / behavior |

## 5. Negative matrix

| ID | Setup | Phone query | Expected behavior |
| --- | --- | --- | --- |
| N1 missing key | `python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 delete --key BC_Veh_Spd` | “小车小车，当前车速多少” | no crash; reply says speed not read |
| N2 corrupt key | `python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 corrupt --key BC_Veh_Spd` | “小车小车，当前车速多少” | no stale old speed; reply says speed not read; debug shows decode error |
| N3 Redis stopped | stop Terminal A simulator | any status query | no crash; debug shows connection error / connected false |
| N4 wrong host | **[PHONE]** set host to `10.85.145.250` | any query | bounded timeout; no crash |
| N5 recover | restart simulator and run `defaults` | repeat speed query | replies recover with fresh value |

## 6. Completion criteria

Phase 2 passes when:

1. Phone reads computer Redis over Wi-Fi.
2. Computer-side Redis value changes are reflected on phone replies/debug panel without reinstalling APK.
3. All Group A and Group B matrix rows pass.
4. Negative cases do not crash and do not reuse stale old `vehicle.*` values.
5. No real vehicle control write path is used.

## 7. Evidence to capture

For each failed row, capture:

- computer command used;
- phone reply text;
- debug panel `只读车况 / 告警解释 / 协作信息` text;
- `adb logcat` excerpt if the app crashes or times out.

Optional logcat command:

```bash
source scripts/env.sh
$ANDROID_HOME/platform-tools/adb -s 10.85.250.222:38901 logcat -d | grep -E 'Vehicle read-only snapshot|VoicePipelineController|ERROR|WARN' | tail -n 120
```
