# Real vehicle proto format and app-read gap analysis

Date: 2026-06-10

## 1. Format and engineering issues

The archive is useful as source protobuf material, but it is not a complete runnable interface contract by itself.

### 1.1 Missing contract metadata

- No explicit Redis key list is included in the archive.
- No Redis host, port, db index, auth mode, TTL, update frequency, or sample payload is included.
- No protobuf package names or language generation options are included.
- No schema version, source system version, or compatibility note is included.

Impact: the app can preserve and hand-decode these fields, but generated Java/Kotlin protobuf integration would need a wrapper package/options layer or upstream schema cleanup.

### 1.2 Protobuf style issues

The files are syntactically close to valid `proto3`, but have style and maintainability issues:

| File | Issue | Impact |
| --- | --- | --- |
| all `.proto` files | no `package` | all messages live in the global protobuf namespace; easy to collide later |
| all `.proto` files | no `option java_package`, `java_multiple_files`, `java_outer_classname` | generated Android classes would use default package/class naming, which is awkward and fragile |
| `trafficlightlist.proto` | `Traffic_Light` message uses underscore | unusual for message types; generated naming is less clean |
| `trafficlightlist.proto` | `TrafficLightlist` casing is inconsistent | likely should be `TrafficLightList`, but changing it would affect generated API names |
| `trafficlightlist.proto` | `intersectionId` is camelCase | proto field style should be snake_case, e.g. `intersection_id`; wire field number is still valid |
| `sam.proto` | `accelaration_cmd` appears misspelled | likely intended `acceleration_cmd`; field number 9 is the compatibility anchor |
| `sam.proto` | scene comment has spacing artifact `合作式    借道` | comment-only issue |
| `location.proto`, `MMobstacles`, `Location{` | inconsistent spacing before `{` | harmless style issue |

### 1.3 Redis key ambiguity still exists

The 2026-06-08 PDF extraction showed wrapped keys:

- `Sensor_Mmobstacl` + `es`
- `Sensor_Trafficlightli` + `st`

The new archive names messages/files as `MMobstacles` and `trafficlightlist.proto`, so the current app's `Sensor_Mmobstacles` and `Sensor_Trafficlightlist` remain the best interpretation. Still, only a real Redis key dump can fully confirm runtime key names.

## 2. Current app read coverage

Current app key list is in `VehicleRedisKeys`:

- chassis/body/HVAC/status: `BC_Veh_Spd`, `DCU_INFO_1`, `DCU_INFO_2`, `DCU_Battery_St`, `DCU_INFO`, `DCU_L2_St`, `ACM_INF2`, `ACM_INF4`, `BC_AutoD_Veh_St`, `TPMS_INFO`, `PFC_Main_Obstacle_INF`
- perception/SAM: `Sensor_Location`, `Sensor_Mmobstacles`, `Sensor_Trafficlightlist`, `Sam`

This new archive only covers:

- `Location`
- `MMobstacles`
- `LaneList`
- `TrafficLightlist`
- `Sam`

So most chassis/body/HVAC/status keys currently read by the app are not represented in this new archive; they still depend on the earlier 2026-06-08 chassis PDF ingest.

## 3. Field-by-field gaps against current app

### 3.1 `Location`

New proto fields:

- read now: `timestamp = 1`, `lon = 2`, `lat = 3`, `heading = 7`, `linear_velocity = 8`
- not read now: `height = 4`, `pitch = 5`, `roll = 6`, `velocity_x = 9`, `velocity_y = 10`, `velocity_z = 11`, `linear_acceleration = 12`, `acceleration_x = 13`, `acceleration_y = 14`, `acceleration_z = 15`, `angular_velocity = 16`, `angular_velocity_x = 17`, `angular_velocity_y = 18`, `angular_velocity_z = 19`, `origin_lon = 20`, `origin_lat = 21`, `utm_position_x = 22`, `utm_position_y = 23`, `utm_position_z = 24`, `rtkflag = 25`

Potential product gap: the debug panel and voice replies cannot currently tell RTK status, elevation, pitch/roll, UTM position, acceleration, or angular velocity.

### 3.2 `MMobstacles`

New proto fields:

- read now from the first obstacle only: `id = 1`, `center_pos_vehicle_x = 2`, `center_pos_vehicle_y = 3`, `velocity_vehicle = 8`, `type = 15`, `confidence = 16`
- not read now from top-level message: `timestamp = 1`, `obstacle_num = 2`
- not read now from each obstacle: `center_pos_vehicle_z = 4`, `center_pos_abs_x = 5`, `center_pos_abs_y = 6`, `center_pos_abs_z = 7`, `velocity_abs = 9`, `theta_vehicle = 10`, `theta_abs = 11`, `length = 12`, `width = 13`, `height = 14`, `lane_position = 17`, `fusion_type = 18`, `cross_id = 19`, `src_type = 20`, `lane_id = 21`, `lane_index = 22`, `lon = 23`, `lat = 24`

Current behavior gap:

- only the first obstacle is used;
- obstacle count is not shown;
- obstacle lon/lat and size are ignored;
- lane/cross/source metadata is ignored;
- absolute/world coordinates are ignored.

### 3.3 `TrafficLightlist`

New proto fields:

- read now: top-level `traffic_light_num = 2`; first traffic light `color = 1`, `confidence = 2`
- not read now: top-level `timestamp = 1`; first traffic light `light_pt_x = 3`, `light_pt_y = 4`, `height = 5`, `width = 6`, `intersectionId = 7`, `phase_id = 8`, `remaining_time = 9`, `lon = 10`, `lat = 11`

Current behavior gap:

- the app can say red/yellow/green and confidence/count;
- it cannot say which intersection/phase, remaining signal time, traffic light location, or image box details.

### 3.4 `LaneList`

New proto fields:

- not read at all: `LaneList.timestamp = 1`, `lane_num = 2`, repeated `laneline = 3`
- not read at all inside `Line`: `lane_type = 1`, `pts_vehicle_num = 2`, `pts_vehicle = 3`, `pts_abs_num = 4`, `pts_abs = 5`, `confidence = 6`

Current behavior gap: although earlier docs mentioned lane-line queries, the app has no `Sensor_Lanelist` Redis key, no lane decoder, no snapshot model, no debug row, and no voice reply for lane-line status.

### 3.5 `Sam`

New proto fields:

- read now: `timestamp = 1`, `scene_id = 2`, `vid = 3`, `vehicle_num = 4`, `auto_level = 5`, `speed_mps = 10`, `collaborative_vehicle_count = 11`, `driving_intention = 13`, `intent_reason = 14`, `guide_decision = 15`, `feedback_result = 16`, `coordinate_behavior = 17`, `event_type = 18`, `check_count = 21`
- not read now: `driving_mode_fd = 6`, `gear_location_fd = 7`, `steering_value_fd = 8`, `accelaration_cmd = 9`, repeated `collaborative_vehicle_list = 12`, `start_time = 19`, `end_time = 20`

Potential inconsistency:

- the app data model currently calls field 4 `vehicleNumber`, but the new comment says `请求车ID`, not plate number. UI/debug text should avoid treating it as a license plate unless the vehicle side confirms that meaning.

## 4. Current app content not covered by this archive

The current app also reads these schemas/keys, but this new `protos.zip` does not include their proto definitions:

- `BC_Veh_Spd`
- `DCU_INFO_1`
- `DCU_INFO_2`
- `DCU_Battery_St`
- `DCU_INFO`
- `DCU_L2_St`
- `ACM_INF2`
- `ACM_INF4`
- `BC_AutoD_Veh_St`
- `TPMS_INFO`
- `PFC_Main_Obstacle_INF`

These remain dependent on the 2026-06-08 chassis interface document extraction.

## 5. Recommended next changes

1. Ask vehicle side for a real Redis key dump and one sample binary payload per key.
2. Add `Sensor_Lanelist` to `VehicleRedisKeys` and implement a minimal lane decoder.
3. Extend `VehicleLocation` with `height`, `pitch`, `roll`, and `rtkflag`.
4. Extend obstacle snapshot with obstacle count, lon/lat, size, and lane metadata.
5. Extend traffic-light snapshot with `intersectionId`, `phase_id`, `remaining_time`, lon/lat.
6. Rename UI/debug wording for `Sam.vehicle_num` from plate-like wording to request vehicle ID unless confirmed otherwise.
