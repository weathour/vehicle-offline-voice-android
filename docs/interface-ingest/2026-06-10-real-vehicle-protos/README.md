# Interface proto ingest: 2026-06-10 real vehicle protos

Source archive copied from local WeChat/OneDrive cache on 2026-06-10:

- `source/protos.zip`

Extracted protobuf sources:

- `source/protos/location.proto`
- `source/protos/mmobstacles.proto`
- `source/protos/lanelist.proto`
- `source/protos/trafficlightlist.proto`
- `source/protos/sam.proto`

Purpose: preserve the post-real-vehicle-test protobuf source package and compare it with the earlier 2026-06-08 interface-document ingest.

## Difference from 2026-06-08 ingest

The 2026-06-08 batch contained PDF interface documents plus a standalone `sam.proto`. This batch contains clean `.proto` source files for the perception messages that were previously only available through PDF text extraction.

Important differences:

- `sam.proto` keeps the same message names and field numbers as `docs/interface-ingest/2026-06-08/source/sam.proto`. Only comments changed: `scene_id` now explicitly says `场景ID`; `vehicle_num` changed from `车牌号` to `请求车ID`; the scene comment includes a spacing artifact in `v2v合作式    借道`.
- `location.proto` adds `int32 rtkflag = 25`; the earlier extracted PDF text ended at `utm_position_z = 24`.
- `mmobstacles.proto` adds `double lon = 23` and `double lat = 24`; the earlier extracted PDF text ended at `lane_index = 22`.
- `trafficlightlist.proto` adds `intersectionId = 7`, `phase_id = 8`, `remaining_time = 9`, `lon = 10`, and `lat = 11`; the earlier extracted PDF text ended at `width = 6`.
- `lanelist.proto` is effectively the same field surface as the earlier extracted PDF text.
- No new chassis/control protobuf schema is included in this archive.

## Redis key impact

The archive itself contains protobuf files, not an explicit Redis key list. It does not supersede the need for a live Redis key dump.

Compared with the 2026-06-08 PDF extraction, this batch clarifies the perception message names that were affected by PDF line wrapping:

- `Sensor_Mmobstacl` + `es` should continue to be treated as `Sensor_Mmobstacles`.
- `Sensor_Trafficlightli` + `st` should continue to be treated as `Sensor_Trafficlightlist`.

The current code already uses these full key spellings in `VehicleRedisKeys`.

## Current app impact

The current lightweight decoder already reads a safe subset of these schemas:

- `Location`: timestamp, lon, lat, heading, linear velocity.
- `MMobstacles`: first obstacle id, vehicle X/Y, vehicle velocity, type, confidence.
- `TrafficLightlist`: count, first light color, first light confidence.
- `Sam`: scene, event, vehicle id/number, auto level, speed, cooperation count, intention/reason/decision/feedback/behavior.

The newly surfaced fields are backward-compatible for the current decoder because protobuf unknown fields are ignored by the app's lightweight reader. Useful follow-up extensions are:

- show `Location.rtkflag`;
- expose obstacle `lon`/`lat`;
- expose traffic-light `intersectionId`, `phase_id`, `remaining_time`, `lon`, and `lat`;
- confirm actual Redis key spellings and sample payloads from a real vehicle Redis dump before changing runtime key names.
