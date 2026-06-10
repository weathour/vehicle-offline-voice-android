# Live Redis inspection: 192.168.2.112:6379

Date: 2026-06-10

This inspection used read-only Redis commands: `PING`, `SCAN`, `TYPE`, `TTL`, and `GET`.

## Connection result

- Host: `192.168.2.112`
- Port: `6379`
- TCP connection: ok
- Redis `PING`: `PONG`
- Observed key count: `30`
- Observed TTL: inspected string keys returned `TTL=-1`, so they are persistent keys at the time of sampling.

## Observed keys

```text
AS_Drive_and_Brk_Sys_Ctrl
AS_State_FB_Req
AS_State_FB_Req2
AS_Strg_Ctrl
BC_AutoD_Veh_St
BC_Intelligent_Ctrl_St
BC_Veh_Mileage
BC_Veh_Spd
DCU_Battery_St
DCU_Drive_St
DCU_INFO_1
DCU_INFO_2
DCU_INFO_St
DCU_L2_St
Day2_BSM_Msg
Day2_MAP_Msg
Day2_RSI_Msg
Day2_RSM_Msg
Day2_SPAT_Msg
Day2_SSM_Msg
Day2_VIR_Msg
Rel_SpeedSteer
Sensor_Lanelist
Sensor_Location
Sensor_Mmobstacles
Sensor_SAM
Sensor_TrafficLightlist
Strg_StrgSys_St
VIN_INFO
planned_trajectory
```

## Current app key match

| Current app key | Live Redis status | Impact |
| --- | --- | --- |
| `BC_Veh_Spd` | present | schema mismatch; see below |
| `DCU_INFO_1` | present | schema mismatch; field 1 is timestamp |
| `DCU_INFO_2` | present | schema mismatch; field 1 is timestamp |
| `DCU_Battery_St` | present | schema mismatch; app reads current as SOC |
| `DCU_INFO` | missing | live key appears to be `DCU_INFO_St` |
| `DCU_L2_St` | present | schema mismatch; field 1 is timestamp |
| `ACM_INF2` | missing | AC temperature cannot be read from this Redis snapshot |
| `ACM_INF4` | missing | AC state cannot be read from this Redis snapshot |
| `BC_AutoD_Veh_St` | present | current sampled payload lacks app-read door/horn/wiper fields |
| `TPMS_INFO` | missing | tire pressure cannot be read from this Redis snapshot |
| `Sensor_Location` | present | current app reads a valid subset |
| `Sensor_Mmobstacles` | present | current app reads a partial first-obstacle subset |
| `Sensor_Trafficlightlist` | missing | live key is `Sensor_TrafficLightlist` with uppercase `L` |
| `PFC_Main_Obstacle_INF` | missing | main-obstacle fault key cannot be read from this Redis snapshot |
| `Sam` | missing | live key is `Sensor_SAM` |

## Field structure checks

### `BC_Veh_Spd`

Sampled payload:

```text
1=double timestamp
```

Earlier sampling briefly showed `2=float speed`, but repeated samples only contained timestamp.

Current app behavior: `decodeSpeed` reads field `1` as `float`, so it will not read live speed correctly.

Required change: read speed from the confirmed live field, likely field `2` when present, and handle timestamp-only payloads.

### `DCU_Battery_St`

Sampled payload:

```text
1=double timestamp
2=float voltage, observed around 636 V
3=float current, observed around 5.2 A
4=float SOC, observed around 94.4%
```

Current app behavior: reads field `3` as SOC, so it would report current as battery percentage.

Required change: read SOC from field `4`; optionally expose voltage/current separately.

### `DCU_INFO_St`

Live Redis has:

```text
DCU_INFO_St: 1=double timestamp, 2=float remaining range
```

Current app behavior: reads missing key `DCU_INFO`, so remaining range is missing.

Required change: change or alias range key from `DCU_INFO` to `DCU_INFO_St`, then read field `2` instead of field `1`.

### `DCU_INFO_1`

Sampled payload:

```text
1=double timestamp
2=varint 3
3=varint 2
5=varint 1
```

Current app behavior: reads field `1` as gear and field `3` as parking. Field `1` is actually timestamp, so gear is not read; field `3` may be a shifted/changed status and should not be trusted as parking until confirmed.

Required change: refresh the schema from live/vehicle-side source before mapping these enum fields.

### `DCU_INFO_2`

Sampled payload:

```text
1=double timestamp
2=varint 9 or 10
6=varint 2
7=varint 17
11=varint 1
13=varint 1
```

Current app behavior: expects field `1` to be an enum. It will miss or mislabel warning fields because field `1` is timestamp.

Required change: refresh the live schema and remap enum fields.

### `DCU_L2_St`

Sampled payload:

```text
1=double timestamp
2=varint 1
4=varint 9
6=varint 1
9=varint 1
11=varint 2
```

Current app behavior: expects ACC status at field `1`, ACC mode at field `2`, LKA status at field `5`, and L2 mode at field `9`. Because field `1` is timestamp and several expected fields are missing, the displayed ACC/LKA summary is unreliable.

Required change: refresh the live `DCU_L2_St` schema.

### `BC_AutoD_Veh_St`

Sampled payload:

```text
1=double timestamp
2=varint 1
```

Current app behavior: expects front door at field `3`, mid door at field `4`, horn at field `8`, wiper at field `23`; those fields were absent in repeated samples.

Required change: handle missing fields and confirm whether the live key is sparse, mode-dependent, or uses a different schema.

### `Sensor_Location`

Sampled payload matches the new `location.proto` well:

```text
1 timestamp
2 lon
3 lat
4 height
5 pitch
6 roll
7 heading
8 linear_velocity
...
24 utm_position_z
25 rtkflag
```

Current app behavior: reads only timestamp, lon, lat, heading, and linear velocity.

Missing app fields: height, pitch, roll, velocity components, acceleration, angular velocity, origin, UTM position, and `rtkflag`.

### `Sensor_Mmobstacles`

Sampled payload:

```text
1=double timestamp
2=varint obstacle_num, observed 19
3=repeated Obstacle, observed 19 entries
```

First obstacle included:

```text
1 id
2 center_pos_vehicle_x
3 center_pos_vehicle_y
4 center_pos_vehicle_z
5 center_pos_abs_x
6 center_pos_abs_y
7 center_pos_abs_z
9 velocity_abs
10 theta_vehicle
11 theta_abs
12 length
13 width
14 height
15 type
16 confidence
17 lane_position, observed as signed -3 encoded as uint64
22 lane_index
23 lon
24 lat
```

Current app behavior: reads only the first obstacle id, vehicle x/y, `velocity_vehicle` field 8, type, and confidence.

Mismatch: sampled obstacles did not include field `8`; they included field `9` (`velocity_abs`). Current app velocity will therefore be missing.

Missing app fields: obstacle count, all obstacles beyond the first, z position, world position, heading, size, lane/source metadata, lon, lat.

### `Sensor_TrafficLightlist`

Live Redis key is capitalized as:

```text
Sensor_TrafficLightlist
```

Sampled payload:

```text
1=double timestamp
```

Current app behavior: looks for missing `Sensor_Trafficlightlist`. Even if key casing is fixed, current sampled data has no `traffic_light_num` or `trafficlights` entries, so color/remaining time cannot be read.

Required change: change key casing, but also handle timestamp-only payloads.

### `Sensor_Lanelist`

Sampled payload:

```text
1=double timestamp
```

Current app behavior: no key, decoder, snapshot model, debug row, or voice reply.

Required change: add `Sensor_Lanelist`, then handle timestamp-only data and later parse lane lines when present.

### `Sensor_SAM`

Live Redis key is:

```text
Sensor_SAM
```

Sampled payload:

```text
1=double timestamp, appears millisecond-scale
3=varint 1
4=string "0"
5=string "level_4"
7=varint 1
8=double, observed -3.1 or 28.2 across samples
10=double, observed 0.68355 in one sample
```

Current app behavior: looks for missing `Sam`. If switched to `Sensor_SAM`, the field surface still appears only partially aligned with `sam.proto`: field `2` scene id, field `11` collaborative count, field `18` event type, and field `21` check count were absent in sampled payloads.

Required change: use `Sensor_SAM` key and confirm the live SAM schema with vehicle-side source or sample set before final semantic mapping.

## Keys currently outside app scope

The live Redis also contains data not currently read by the app:

- control/request-like keys: `AS_Drive_and_Brk_Sys_Ctrl`, `AS_State_FB_Req`, `AS_State_FB_Req2`, `AS_Strg_Ctrl`
- V2X Day2 messages: `Day2_BSM_Msg`, `Day2_MAP_Msg`, `Day2_RSI_Msg`, `Day2_RSM_Msg`, `Day2_SPAT_Msg`, `Day2_SSM_Msg`, `Day2_VIR_Msg`
- trajectory: `planned_trajectory`
- additional state keys from earlier docs or nearby domains: `BC_Intelligent_Ctrl_St`, `BC_Veh_Mileage`, `DCU_Drive_St`, `Rel_SpeedSteer`, `Strg_StrgSys_St`, `VIN_INFO`

## Immediate software implications

The current app will connect to this Redis, but the decoded real-vehicle answer quality will be poor until the key/schema mismatches are fixed.

P0 fixes:

1. Add live key aliases:
   - `DCU_INFO_St` for range
   - `Sensor_TrafficLightlist` for traffic lights
   - `Sensor_SAM` for SAM
   - `Sensor_Lanelist` for lanes
2. Remap fields with timestamp-aware schemas:
   - speed likely not field `1`;
   - battery SOC is field `4`;
   - range is `DCU_INFO_St` field `2`;
   - many DCU enum schemas need vehicle-side confirmation.
3. Treat timestamp-only payloads as present-but-not-decodable for business values.
4. Extend perception decoders for `rtkflag`, obstacle count/all obstacles/lon/lat, traffic phase/remaining time when present, and lane data.
5. Request from vehicle side the authoritative live `.proto` set for chassis keys, because observed field numbers differ from the 2026-06-08 extracted chassis document.
