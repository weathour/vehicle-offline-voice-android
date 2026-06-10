# Readable live Redis data snapshot

Date: 2026-06-10
Redis: `192.168.2.112:6379`

This is a read-only sampling of fields that can currently be decoded from live Redis protobuf payloads.

## Confirmed speed source

Current speed should be read from `Sensor_Location`, not `BC_Veh_Spd`.

Observed `Sensor_Location` fields:

| Field | Meaning | Sample |
| --- | --- | --- |
| `1` | timestamp | `1718656206.461` |
| `2` | lon | `106.2904477` |
| `3` | lat | `29.5213987` |
| `4` | height | `302.780 m` |
| `5` | pitch | `-0.023397` |
| `6` | roll | `0.009399` |
| `7` | heading | `-1.345985` |
| `8` | linear_velocity / speed | `0.001637 m/s`, about `0.005892 km/h` |
| `9` | velocity_x | `0.001157` |
| `10` | velocity_y | `0.001157` |
| `11` | velocity_z | `0.001894` |
| `12` | linear_acceleration | `0.288469` |
| `13` | acceleration_x | `0.272939` |
| `14` | acceleration_y | `0.093374` |
| `15` | acceleration_z | `9.825804` |
| `16` | angular_velocity | `0.002745` |
| `17` | angular_velocity_x | `0.002609` |
| `18` | angular_velocity_y | `0.000853` |
| `19` | angular_velocity_z | `0.001386` |
| `20` | origin_lon | `106.3493290` |
| `21` | origin_lat | `29.5155890` |
| `22` | utm_position_x | `-5714.273` |
| `23` | utm_position_y | `579.017` |
| `24` | utm_position_z | `302.780` |
| `25` | rtkflag | `1` |

Repeated samples showed the same `Sensor_Location` timestamp and speed, so this payload looked static during the inspection window.

## Other currently readable data

### Battery and range

`DCU_Battery_St`:

| Field | Likely meaning | Sample |
| --- | --- | --- |
| `1` | timestamp | `1781071831.082` |
| `2` | voltage | `636.6 V` |
| `3` | current | `1.2 A` |
| `4` | SOC | `94.0 %` |

`DCU_INFO_St`:

| Field | Likely meaning | Sample |
| --- | --- | --- |
| `1` | timestamp | `1781071843.089` |
| `2` | remaining range | `500.0` |

### Mileage

`BC_Veh_Mileage`:

| Field | Sample |
| --- | --- |
| `1` | timestamp `1781071839.100` |
| `2` | `4000.5` |
| `3` | `4000.5` |

### Obstacles

`Sensor_Mmobstacles` is rich and matches the real-vehicle proto shape.

- timestamp: `1780999385.652`
- obstacle count: `19`
- repeated obstacle entries: `19`

Nearest sampled obstacles by vehicle-plane distance:

| id | distance XY m | x | y | z | type | confidence | lon | lat |
| --- | ---: | ---: | ---: | ---: | --- | ---: | ---: | ---: |
| `296` | `3.88` | `-1.78` | `3.44` | `0.45` | `10` | `0.851` | `106.2903939` | `29.5214115` |
| `275` | `4.42` | `-1.24` | `4.25` | `0.49` | `10` | `0.736` | `106.2904024` | `29.5214071` |
| `309` | `6.64` | `6.58` | `-0.89` | `0.13` | `10` | `0.802` | `106.2903634` | `29.5213306` |
| `251` | `7.42` | `2.47` | `7.00` | `0.66` | `5` | `0.676` | `106.2904367` | `29.5213785` |
| `250` | `17.27` | `-17.15` | `-2.08` | `0.11` | `3` | `0.525` | `106.2903128` | `29.5215406` |

Note: the sampled obstacles mainly had velocity in field `9` (`velocity_abs`), while the current app reads field `8` (`velocity_vehicle`). App-side obstacle velocity should be revised or fall back to field `9` when field `8` is absent.

### Steering / wheel speed / drive state

`Strg_StrgSys_St` sample:

```text
1 timestamp
2 -1.2000000477
3 0.0390000008
4 -15.8999996185
6 4
7 14
```

`Rel_SpeedSteer` sample:

```text
1 timestamp
7 8.125
8 8.125
```

`DCU_Drive_St` sample:

```text
1 timestamp
6 4
```

These are readable as protobuf fields, but require authoritative live schema names before UI/voice wording should be finalized.

### DCU / body / L2 status enums

Readable but schema names need confirmation:

- `DCU_INFO_1`: fields `1,2,3,4,5`
- `DCU_INFO_2`: fields `1,2,6,7,13`
- `DCU_L2_St`: fields `1,2,4,6,9,11`
- `BC_AutoD_Veh_St`: fields `1,2,15,16`
- `BC_Intelligent_Ctrl_St`: fields `1,4,8,11,13,14,15`

In all these, field `1` appears to be timestamp, so the current app's old field mapping is not safe.

### SAM

Live key is `Sensor_SAM`, not `Sam`.

Sample:

```text
1 timestamp_ms-like 1781071602269.0
3 1
4 "2"
5 "level_4"
7 1
8 4.899999999999833
10 7.13062
```

The key is readable, but its currently observed field set is sparse compared with `sam.proto`; confirm live SAM schema before presenting scene/event/cooperation semantics.

## Keys present but only timestamp during sampling

- `BC_Veh_Spd`: only field `1` timestamp. Do not use this for speed in the current live setup.
- `Sensor_TrafficLightlist`: only field `1` timestamp. No traffic light color/phase/remaining time in the sampled payload.
- `Sensor_Lanelist`: only field `1` timestamp. No lane-line list in the sampled payload.

## Not present in current live Redis snapshot

- `ACM_INF2`
- `ACM_INF4`
- `TPMS_INFO`
- `PFC_Main_Obstacle_INF`
- `DCU_INFO`
- `Sam`
- `Sensor_Trafficlightlist`

## Immediate app mapping changes

1. Read vehicle speed from `Sensor_Location` field `8`.
2. Read battery SOC from `DCU_Battery_St` field `4`.
3. Read remaining range from `DCU_INFO_St` field `2`.
4. Use live key names:
   - `Sensor_SAM`
   - `Sensor_TrafficLightlist`
   - `DCU_INFO_St`
5. Treat `BC_Veh_Spd`, `Sensor_TrafficLightlist`, and `Sensor_Lanelist` timestamp-only payloads as "key present, business value absent".
6. Update obstacle decoding to handle all repeated obstacles and use field `9` velocity when field `8` is absent.
