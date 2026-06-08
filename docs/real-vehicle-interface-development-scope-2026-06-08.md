# Real vehicle interface ingest and development scope

Date: 2026-06-08
Project: `VehicleOfflineVoice`

## 1. Ingested source documents

The two local PDF interface documents have been copied into the repository and text-extracted for local search/analysis.

Repository paths:

- `docs/interface-ingest/2026-06-08/source/chassis-data-interface.pdf`
- `docs/interface-ingest/2026-06-08/source/perception-data-interface.pdf`
- `docs/interface-ingest/2026-06-08/extracted/chassis-data-interface.txt`
- `docs/interface-ingest/2026-06-08/extracted/perception-data-interface.txt`
- `docs/interface-ingest/2026-06-08/SHA256SUMS`

Original local files:

- `/home/weathour/文档/xwechat_files/wxid_cirtg5hzmhm222_1def/msg/file/2026-06/数据隔舱-底盘数据接口文档.pdf`
- `/home/weathour/文档/xwechat_files/wxid_cirtg5hzmhm222_1def/msg/file/2026-06/数据隔舱-感知数据接口文档.pdf`

## 2. Interface format conclusion

Both documents define a Redis + protobuf integration surface.

### Chassis document

- State data: read Redis cache by key.
- Control data: publish/subscribe through Redis channel.
- Payload format: protobuf serialized directly to string/binary value.

### Perception document

- State data: read Redis cache by key.
- Payload format: protobuf serialized directly to string/binary value.
- No perception control channel is defined in the provided document.

## 3. Chassis interface inventory

### 3.1 Control channel

| Function | Redis channel | Protobuf message | Fields |
| --- | --- | --- | --- |
| 云端控制指令 | `CLOUD2VEH_REMOTECTL_1` | `CLOUD2VEH_REMOTECTL_1` | `cloud_message_id`, `steering_angle`, `steering_angular_velocity`, `accel_pos`, `brake_flag`, `brake_pos`, `tap_pos` |

Important boundary: this is a remote driving/control command surface: steering, throttle, brake, and gear. It is **not** an HVAC/window/door command interface.

### 3.2 State keys

| Redis key | Protobuf message | Main meaning for this app |
| --- | --- | --- |
| `DCU_Drive_St` | `DCU_Drive_St` | intelligent-driving confirmation, accelerator/brake position, real speed, life signal |
| `DCU_INFO_1` | `DCU_INFO_1` | gear, high-voltage drive state, parking state, steering pump state |
| `DCU_INFO_2` | `DCU_INFO_2` | auto-driving limit/out reasons, emergency stop reason, low/high-voltage faults, takeover request, drive mode, ABS/brake/start-key states |
| `DCU_Battery_St` | `DCU_Battery_St` | battery voltage/current/SOC |
| `DCU_INFO` | `DCU_INFO` | remaining driving range |
| `Strg_StrgSys_St` | `Strg_StrgSys_St` | steering torque, assist torque, steering angle/speed, steering mode, life signal |
| `DCU_L2_St` | `DCU_L2_St` | ACC/LKA/LDW/CMS status and failure/quit reasons |
| `BC_Intelligent_Ctrl_St` | `BC_Intelligent_Ctrl_St` | emergency/autod/start buttons, seatbelt/driver-left alarms, door buttons, horn, defrosting, hazard light |
| `BC_Veh_Mileage` | `BC_Veh_Mileage` | short/long mileage |
| `BC_AutoD_Veh_St` | `BC_AutoD_Veh_St` | door states, brake/day/fog/high/low/turn/driver/mini/roof lights, horn, wiper, alarms |
| `BC_Veh_Spd` | `BC_Veh_Spd` | vehicle speed |
| `Rel_SpeedSteer` | `Rel_SpeedSteer` | front/rear wheel speeds |
| `TPMS_INFO` | `TPMS_INFO` | tire location, tire pressure/temp, tire alarms |
| `VIN_INFO` | `VIN_INFO` | VIN data fragments |
| `ACM_INF2` | `ACM_INF2` | in-car/out-car temperature |
| `ACM_INF4` | `ACM_INF4` | AC power state, AC mode, fan gear, set temperature |
| `YRS_INF` | `YRS_INF` | longitudinal/lateral acceleration and yaw-rate sensor state |
| `PFC_Main_Obstacle_INF` | `PFC_Main_Obstacle_INF` | main obstacle type, relative position/velocity, camera/radar/vehicle/fusion faults |

## 3.3 Cooperative driving / SAM interface

Additional protobuf source:

- `docs/interface-ingest/2026-06-08/source/sam.proto`

`sam.proto` defines `Sam`, a read-only cooperative-driving context message: scene id, vehicle id/plate, auto level, driving mode, gear, steering feedback, speed, collaborative vehicle list, intention/reason, guide decision, feedback result, coordinate behavior, and event lifecycle. This belongs to the voice module's explanation/cooperation Q&A scope, not to low-level control.

## 4. Perception interface inventory

| Redis key | Protobuf message | Main meaning for this app |
| --- | --- | --- |
| `Sensor_Location` | `Location` | timestamp, lon/lat/height, pitch/roll/heading, velocity, acceleration, angular velocity, origin point, UTM position |
| `Sensor_Mmobstacles`* | `MMobstacles` containing repeated `Obstacle` | obstacle count and obstacle list: id, vehicle/world position, velocity, heading, size, type, confidence, lane/cross/source fields |
| `Sensor_Lanelist` | `LaneList` | lane line list with vehicle/world point sets and confidence |
| `Sensor_Trafficlightlist` | `TrafficLightlist` | traffic-light count and list: color, confidence, box position/size |

`*` Note: the PDF line breaks the key as `Sensor_Mmobstacl` + `es`; the intended key is most likely `Sensor_Mmobstacles`. Confirm against actual Redis key list before implementation.

## 5. What this means for current app development

The current app already has:

- offline wake/ASR/NLU/TTS loop;
- `VehicleStateStore` abstraction;
- in-memory `MockRedisStore`;
- Unity action JSON logging;
- no `INTERNET` permission;
- mock/virtual/real microphone runtime modes.

The new documents mainly enable **real vehicle state reading** and **perception/state query features**. They do **not** provide a safe/complete interface for the app's existing mock commands `打开空调`, `关闭空调`, `打开车窗`, `关闭车窗` as real vehicle control writes. The only provided control channel is remote driving control, which is safety-critical and should not be voice-enabled casually.

## 6. Required development functions

### P0 — Interface foundation

1. **Create protobuf schema set**
   - Convert the PDF message definitions into `.proto` files.
   - Split into chassis and perception packages.
   - Add generated Kotlin/Java protobuf classes or a lightweight decoder strategy.
   - Add schema compile tests.

2. **Add real Redis integration as an optional mode**
   - Add a Redis client adapter behind existing abstractions, not replacing mock paths.
   - Read protobuf payloads by Redis key.
   - Keep `MockRedisStore` and scripted tests.
   - Add explicit runtime config for host/port/auth/db/timeouts.
   - Only add Android `INTERNET` permission if real network Redis mode is intentionally enabled.

3. **Introduce typed vehicle snapshot model**
   - Map raw protobuf messages into an app-domain `VehicleSnapshot`.
   - Minimum fields: speed, gear, drive state, parking state, battery SOC, remaining range, AC state, in/out temperature, set temperature, door state, lights, wiper, tire pressure/temp alarms, location, main obstacle, L2 status.

4. **Add Redis/protobuf fixtures and tests**
   - Synthetic binary payload fixtures for each selected key.
   - Decode tests: protobuf bytes -> typed raw message -> `VehicleSnapshot`.
   - Missing/expired/malformed Redis value tests.

### P1 — Voice/query features enabled by these documents

5. **Vehicle status voice queries**
   - Add NLU intents and replies for:
     - 当前车速 / 速度多少;
     - 当前档位;
     - 电量 / SOC;
     - 剩余里程;
     - 空调是否开启 / 当前温度 / 设定温度;
     - 车门是否关闭;
     - 有无故障 / 急停 / 接管提醒;
     - 胎压是否正常.

6. **Perception voice queries**
   - Add query intents and replies for:
     - 当前定位 / 航向;
     - 前方是否有障碍物;
     - 最近主目标距离/类型;
     - 当前红绿灯状态;
     - 是否检测到车道线.

7. **Phone debug panel extension**
   - Add a real vehicle data section:
     - Redis connection state;
     - last update time per key;
     - decoded speed/SOC/AC/door/fault/location/main-obstacle summary;
     - decode/timeout errors.

8. **Unity JSON extension**
   - Keep existing voice action JSON.
   - Add separate state/perception event JSON examples for Unity:
     - `vehicle_state_snapshot`;
     - `perception_snapshot`;
     - `vehicle_warning`.

### P2 — Control integration, gated by missing/safety-critical information

9. **Do not implement real AC/window control yet**
   - The provided documents define AC/door/window-like **status** fields but not AC/window **control** write channels.
   - Need an additional BCM/HVAC/window control interface before mapping `打开空调` / `关闭空调` / `打开车窗` / `关闭车窗` to real Redis writes.

10. **Remote driving command publisher only as a disabled/safety-gated prototype**
    - If needed later, implement `CLOUD2VEH_REMOTECTL_1` encoder and Redis publisher as a disabled-by-default module.
    - Require explicit config flag, UI/manual confirmation, rate limiting, command ID handling, and safety constraints.
    - Do not bind voice commands directly to steering/throttle/brake/gear.

## 7. Recommended first implementation slice

The first safe and useful slice should be **read-only real vehicle state integration**:

1. Add `.proto` schemas for a small subset:
   - `BC_Veh_Spd`
   - `DCU_INFO_1`
   - `DCU_Battery_St`
   - `DCU_INFO`
   - `ACM_INF2`
   - `ACM_INF4`
   - `BC_AutoD_Veh_St`
   - `Sensor_Location`
   - `Sensor_Trafficlightlist`
   - `Sensor_Mmobstacles` / `MMobstacles`
2. Add typed snapshot mapper.
3. Add mock Redis binary fixtures.
4. Add voice replies for speed, battery, range, AC state, temperature, door state, location, obstacle, and traffic light.
5. Add debug panel display for decoded values.
6. Keep real Redis connection behind an explicit runtime mode and config gate.

## 8. Information still needed from vehicle side

Before writing real control features, request:

1. Actual Redis connection details for test environment: host, port, auth, db index, network route.
2. Actual Redis key list dump to confirm spellings, especially `Sensor_Mmobstacles`.
3. Sample binary values or a small Redis dump for selected keys.
4. Protobuf package names/options if they exist in source `.proto` files.
5. Update frequency / TTL for each key.
6. Control interfaces for HVAC, windows, doors, horn, lights, defrosting, hazard light if voice control is expected.
7. Safety rules for any remote driving command path.

## 9. Immediate conclusion

Develop first:

- protobuf schema + decoding;
- optional Redis read adapter;
- vehicle/perception snapshot model;
- read-only voice query intents;
- phone debug panel for decoded real data;
- Unity state/perception JSON export.

Do not develop first:

- real AC/window write control;
- direct voice-triggered steering/throttle/brake/gear control.

Those require additional control-interface documents and explicit safety approval.
