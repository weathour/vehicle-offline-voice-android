# Live Redis Capture (Real Vehicle)

Redis source: `192.168.2.112:6379`.

This folder keeps live-vehicle Redis observations for later development and parser regression checks.

## Primary Archive

- `redis-capture-60s-1hz.jsonl` - primary reliable archive. It contains 9 complete snapshots, each with all 30 observed Redis keys, raw payload bytes as base64, and field-number-level protobuf wire decoding where possible.
- `redis-capture-summary.json` - summary of the primary reliable archive.
- `INTERPRETATION.md` - development interpretation of the valid samples.

The primary archive is the best source to use for implementation work because every sample completed without read errors.

## Traceability Files

- `redis-capture-60samples-1hz-values.jsonl` - later 60-sample attempt. Only the first few samples are useful; sample 5 onward hit timeouts or connection reset errors.
- `redis-capture-60samples-values-summary.json` - summary of the later 60-sample attempt.
- `redis-capture-60samples-1hz-full.jsonl` - interrupted heavy capture attempt with Redis `DUMP`; kept only for traceability.

Do not treat the 60-sample attempts as complete captures.

## Primary Capture Result

- Capture window: `2026-06-10T06:18:37Z` to `2026-06-10T06:19:41Z`
- Complete samples: `9`
- Observed key count: `30`
- Errors: `0`

## Observed Keys

- `AS_Drive_and_Brk_Sys_Ctrl`
- `AS_State_FB_Req`
- `AS_State_FB_Req2`
- `AS_Strg_Ctrl`
- `BC_AutoD_Veh_St`
- `BC_Intelligent_Ctrl_St`
- `BC_Veh_Mileage`
- `BC_Veh_Spd`
- `DCU_Battery_St`
- `DCU_Drive_St`
- `DCU_INFO_1`
- `DCU_INFO_2`
- `DCU_INFO_St`
- `DCU_L2_St`
- `Day2_BSM_Msg`
- `Day2_MAP_Msg`
- `Day2_RSI_Msg`
- `Day2_RSM_Msg`
- `Day2_SPAT_Msg`
- `Day2_SSM_Msg`
- `Day2_VIR_Msg`
- `Rel_SpeedSteer`
- `Sensor_Lanelist`
- `Sensor_Location`
- `Sensor_Mmobstacles`
- `Sensor_SAM`
- `Sensor_TrafficLightlist`
- `Strg_StrgSys_St`
- `VIN_INFO`
- `planned_trajectory`

## Data Freshness Note

During the 9 complete samples, several perception/localization payloads did not change:

- `Sensor_Location`
- `Sensor_Mmobstacles`
- `Sensor_Lanelist`
- `Sensor_TrafficLightlist`
- `Sensor_SAM`

Chassis, battery, steering, and status keys did change. Treat the static perception/localization payloads as readable but not proven real-time until vehicle-side freshness is confirmed.
