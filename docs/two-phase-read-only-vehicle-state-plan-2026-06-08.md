# Two-phase plan: read-only vehicle state + simulated Redis/protobuf + end-to-end test

Date: 2026-06-08
Project: `VehicleOfflineVoice`

## 1. Newly ingested `sam.proto`

Source ingested into:

- `docs/interface-ingest/2026-06-08/source/sam.proto`
- `docs/interface-ingest/2026-06-08/extracted/sam.proto.txt`

SHA256:

```text
e8fe678fbc3ea7a0b18f7e797f1027a5129af7edfafc07e474a86cedc21c5251
```

## 2. `sam.proto` analysis

`sam.proto` defines vehicle collaboration / cooperative driving event state.

Messages:

### `CollaborativeVehicle`

| Field | Type | Meaning |
| --- | --- | --- |
| `vid` | `int32` | collaborative vehicle id |
| `suggested_driving_behavior` | `int32` | suggested driving behavior for that collaborative vehicle |

### `Sam`

| Field | Type | Meaning for voice module |
| --- | --- | --- |
| `timestamp` | `double` | message timestamp |
| `scene_id` | `int32` | cooperation scene id |
| `vid` | `int32` | own/subject vehicle id |
| `vehicle_num` | `string` | plate/vehicle number |
| `auto_level` | `string` | autonomous-driving capability level |
| `driving_mode_fd` | `int32` | driving mode feedback |
| `gear_location_fd` | `int32` | gear feedback |
| `steering_value_fd` | `double` | steering-wheel feedback |
| `accelaration_cmd` | `double` | acceleration command |
| `speed_mps` | `double` | speed in m/s |
| `collaborative_vehicle_count` | `int32` | number of collaborating vehicles |
| `collaborative_vehicle_list` | repeated `CollaborativeVehicle` | collaboration participants / suggested behaviors |
| `driving_intention` | `int32` | request-side planned driving behavior |
| `intent_reason` | `int32` | reason for request-side intention |
| `guide_decision` | `int32` | guide-side decision result |
| `feedback_result` | `int32` | collaborator feedback result |
| `coordinate_behavior` | `int32` | current cooperation behavior |
| `event_type` | enum | event unknown/start/ongoing/end |
| `start_time` | `double` | scene start time in milliseconds |
| `end_time` | `double` | scene end time in milliseconds |
| `check_count` | `int32` | unique current event marker |

Scene id comments in the proto:

| ID | Scene |
| --- | --- |
| 0 | no scene |
| 1 | V2V cooperative lane change |
| 2 | V2V cooperative adaptive cruise |
| 3 | V2V cooperative lane merge |
| 4 | V2V platooning |
| 5 | V2V cooperative borrowing lane |
| 6 | V2V unsignalized ramp turning / alternate passing |
| 7 | V2V unprotected left turn for opposing vehicles at intersection |
| 8 | V2I cooperative lane change |
| 9 | V2I cooperative adaptive cruise |
| 10 | V2I cooperative lane merge |
| 11 | V2I dynamic speed limit |
| 12 | V2I platooning |
| 13 | V2I remote-control driving |

`EventType`:

| Value | Meaning |
| --- | --- |
| 0 | unknown |
| 1 | start |
| 2 | ongoing |
| 3 | end |

## 3. How `Sam` fits the voice module

`Sam` is not a low-level vehicle-control interface. It should be treated as **read-only cooperative driving context** for voice Q&A and explanations.

Useful voice questions:

- “当前协作场景是什么？”
- “协作事件开始了吗？”
- “现在有几辆协作车？”
- “引导决策是什么？”
- “协作反馈结果是什么？”
- “当前协作行为是什么？”
- “现在是 V2V 还是 V2I 场景？”

Do not use `Sam` to issue control commands in this stage. Fields such as `accelaration_cmd` and `coordinate_behavior` are status/context fields for voice explanation unless a separate safety-approved control path is provided.

## 4. Functional target scope: first two priorities only

The voice module should target these two functional groups.

### Group A — Basic operational/status Q&A

Read and answer:

- speed;
- gear;
- battery SOC;
- remaining range;
- AC power/mode/fan/set temperature;
- in-car/out-car temperature;
- door state;
- tire pressure/temperature normality;
- nearest/main obstacle summary;
- traffic-light color.

### Group B — Explanation, warnings, and intelligent/cooperative-driving context

Read and answer:

- ACC status;
- LKA status;
- auto-driving limit-in reason;
- auto-driving exit reason;
- takeover request;
- high/low-voltage fault;
- brake fault;
- emergency stop reason;
- camera/radar/fusion fault;
- `Sam` cooperative scene;
- `Sam` event type;
- `Sam` collaborative vehicle count;
- `Sam` driving intention / guide decision / feedback result / coordinate behavior.

Debug-only / not voice-first:

- raw lon/lat/UTM unless explicitly asked;
- complete obstacle list;
- lane-line point arrays;
- traffic-light bounding boxes;
- CRC/message count/life signal;
- raw VIN fragments.

## 5. Two major project phases

## Phase 1 — Complete software and test-condition development

Goal: before real vehicle access, make the full software path and local test conditions complete on this computer + phone.

### 1.1 Interface/schema coverage

Deliverables:

- Convert selected PDF messages and `sam.proto` into app-side decoders/schemas.
- Extend current simulated protobuf codec or replace with generated protobuf classes if needed.
- Include at minimum:
  - basic status keys: speed, gear, SOC, range, AC, temperature, doors, tire pressure, obstacle, traffic light;
  - explanation keys: `DCU_INFO_2`, `DCU_L2_St`, `PFC_Main_Obstacle_INF` fault fields;
  - collaboration key/message for `Sam`.

Acceptance:

- Unit tests decode synthetic protobuf payloads for every target voice field.
- Malformed/partial payload tests do not crash the app.

### 1.2 Desktop simulated Redis environment

Deliverables:

- Local Redis startup script, for example:
  - `scripts/start_sim_redis.sh`
- Vehicle data writer script, for example:
  - `scripts/sim_vehicle_redis.py`

Script capabilities:

- write default protobuf values;
- change speed;
- change battery SOC;
- change AC state/temperature;
- change door state;
- change tire warning;
- change traffic light;
- change main obstacle;
- change ACC/LKA/auto-driving warning reason;
- change `Sam` scene/event/collaboration count;
- corrupt/delete selected key for negative tests.

Acceptance:

- Redis on the computer stores binary protobuf payloads under realistic keys.
- `redis-cli` can verify keys exist.
- A scripted command can update one field and preserve the rest.

### 1.3 Android remote Redis read source

Deliverables:

- Implement a real network `BinaryVehicleDataSource` for Redis reads.
- Keep current `SimulatedRedisBinaryDataSource` for no-network local tests.
- Add explicit app/debug config for:
  - host;
  - port;
  - auth optional;
  - db index;
  - timeout;
  - local simulated vs remote Redis mode.

Acceptance:

- Phone can read Redis from computer IP `10.85.145.111` when both are on the same network.
- Debug panel shows source, connection state, decoded key count, and errors.
- If remote Redis is enabled, Android `INTERNET` permission change is explicit and documented.

### 1.4 Voice/NLU/reply coverage

Deliverables:

- Voice intents for all Group A and Group B target questions.
- Replies are short, summary-first, and avoid reading raw debug arrays.
- For warnings/explanations, replies translate enum values into Chinese descriptions.

Acceptance examples:

- Change Redis speed to `36.8`; ask “当前车速多少”; phone replies with `36.8`.
- Change traffic light to red; ask “现在是红灯还是绿灯”; phone replies red.
- Change `Sam.scene_id` to V2V cooperative lane change; ask “当前协作场景是什么”; phone explains the scene.
- Change ACC/LKA failure reason; ask “为什么不能进入自动驾驶”; phone explains the reason.

### 1.5 Phone debug UX and manual test harness

Deliverables:

- Debug panel shows:
  - mode: local simulated / remote Redis;
  - Redis endpoint;
  - connected/error;
  - decoded key count;
  - last basic status summary;
  - last warning/cooperation summary.
- Desktop test checklist for phone validation.

Acceptance:

- Tester can diagnose whether failure is network, Redis missing key, protobuf decode, or voice/NLU.

## Phase 2 — End-to-end test

Goal: prove that computer-side Redis changes propagate to the phone APK and voice responses/debug panel update correctly.

### 2.1 Network and connectivity test

Initial known network:

```text
computer Wi-Fi IP: 10.85.145.111
phone wireless ADB IP: 10.85.250.222
```

Acceptance:

- Phone APK connects to Redis at `10.85.145.111:6379`.
- Debug panel shows `connected=true` and decoded keys.

### 2.2 Basic status E2E matrix

For each item, update Redis on computer, query by voice on phone, and verify reply/debug panel:

- speed;
- gear;
- battery SOC;
- remaining range;
- AC state and temperature;
- door state;
- tire pressure warning;
- main obstacle;
- traffic light.

Acceptance:

- Phone response changes after the computer-side Redis value changes.
- No APK reinstall is needed between value changes.

### 2.3 Explanation/warning/cooperation E2E matrix

For each item, update Redis on computer and query by voice:

- ACC status/fail reason;
- LKA status/fail reason;
- auto-driving limit/out reason;
- takeover request;
- high/low-voltage fault;
- brake fault;
- camera/radar/fusion fault;
- `Sam.scene_id`;
- `Sam.event_type`;
- `Sam.collaborative_vehicle_count`;
- `Sam.guide_decision` / `feedback_result`.

Acceptance:

- Phone gives a human-readable explanation, not raw numeric enum only.

### 2.4 Negative E2E matrix

Test:

- Redis stopped;
- wrong host/port;
- key missing;
- protobuf payload corrupted;
- partial keys valid, partial keys invalid;
- network timeout.

Acceptance:

- app does not crash;
- debug panel identifies the failure class;
- voice replies degrade gracefully, for example “车辆数据连接不可用” or “暂未读取到车速”.

### 2.5 Completion boundary

Phase 2 is complete when:

1. all Group A and Group B fields can be simulated from the computer;
2. phone reads the computer Redis over the local network;
3. changing Redis values changes phone voice replies/debug display;
4. negative cases are stable;
5. no real control write path is implemented.

## 6. Immediate next implementation recommendation

Next coding slice should be:

1. add `Sam` decoding and simulated fixture;
2. add basic explanation fields from `DCU_INFO_2`, `DCU_L2_St`, `PFC_Main_Obstacle_INF`, and tire status;
3. implement desktop Redis simulator scripts;
4. add Android remote Redis source behind `BinaryVehicleDataSource`;
5. add a simple debug config path for `10.85.145.111:6379`.

## 7. Phase 1 implementation status (2026-06-08)

Implemented in the APK and scripts:

- Group A/B protobuf fixtures and decoders including `DCU_INFO_2`, `DCU_L2_St`, `TPMS_INFO`, `PFC_Main_Obstacle_INF`, and `Sam`.
- Non-mutating voice queries/replies for basic status, tire status, warnings/faults, ACC/LKA, auto-driving reasons, takeover, and cooperative context.
- Optional remote Redis read source behind `BinaryVehicleDataSource` using a minimal socket RESP client.
- Phone debug UI for local simulated vs computer Redis source, host/port, and read-only status summaries.
- Desktop scripts: `scripts/start_sim_redis.sh`, `scripts/sim_redis_server.py`, `scripts/sim_vehicle_redis.py`, `scripts/smoke_sim_redis.sh`.
- Phase 2 handoff: `docs/phase1-readiness-handoff-2026-06-08.md`.

Boundary remains read-only; no real control write path was added.
