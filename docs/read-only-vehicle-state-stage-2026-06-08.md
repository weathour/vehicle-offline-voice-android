# Read-only vehicle state integration stage

Date: 2026-06-08
Project: `VehicleOfflineVoice`

## 1. Stage decision

This stage only implements **read-only vehicle state access**. Because no real vehicle Redis is available yet, the APK uses a switchable in-memory Redis-like protobuf fixture by default.

Safety boundary:

- no real vehicle write/control path is added;
- no `INTERNET` permission is added;
- existing mock/scripted voice paths remain intact;
- current AC/window voice actions still mutate only local mock state, not real vehicle Redis;
- steering/throttle/brake/gear channel `CLOUD2VEH_REMOTECTL_1` is not connected to voice commands.

## 2. Implemented switch point

New package:

```text
app/src/main/java/com/company/vehiclevoice/data/readonly/
```

Main abstractions:

- `BinaryVehicleDataSource` — small read-only binary key/value source, matching the Redis cache shape.
- `VehicleReadOnlySnapshotProvider` — converts binary Redis/protobuf values into a typed `VehicleReadOnlySnapshot`.
- `RedisVehicleSnapshotProvider` — current provider implementation over any `BinaryVehicleDataSource`.
- `SimulatedRedisBinaryDataSource` — in-memory Redis-like store for local/phone simulation.
- `SimulatedVehicleRedisFixtures` — default protobuf binary payloads.
- `VehicleSnapshotStateMapper` — mirrors typed snapshot values into the existing `VehicleStateStore` string map for voice replies/debug.

Current default wiring:

```kotlin
VoicePipelineFactory.createServicePipeline(...)
  -> readOnlySnapshotProviderFactory defaults to RedisVehicleSnapshotProvider.simulated()
```

Future real-vehicle wiring should replace only the provider factory/source, for example:

```kotlin
readOnlySnapshotProviderFactory = {
    RedisVehicleSnapshotProvider(RealRedisBinaryDataSource(config))
}
```

The rest of the voice/NLU/reply pipeline should not need to change.

## 3. Protobuf simulation

A small local protobuf wire codec was added instead of adding protobuf/Redis network dependencies immediately:

- supports varint fields for int32/uint32/enum;
- supports fixed32 for float;
- supports fixed64 for double;
- supports length-delimited nested messages for repeated perception objects.

This is enough for the first read-only subset from the two interface PDFs.

Default simulated Redis keys:

- `BC_Veh_Spd`
- `DCU_INFO_1`
- `DCU_Battery_St`
- `DCU_INFO`
- `ACM_INF2`
- `ACM_INF4`
- `BC_AutoD_Veh_St`
- `Sensor_Location`
- `Sensor_Mmobstacles`
- `Sensor_Trafficlightlist`
- `PFC_Main_Obstacle_INF`

Default fixture values are intentionally realistic but synthetic: speed 12.5 km/h, gear D, battery SOC 76%, range 128 km, AC on/cooling, doors closed, Chongqing-like lon/lat, one green traffic light, one vehicle obstacle.

## 4. Voice queries now backed by read-only snapshot

New non-mutating query intents include:

- `vehicle_speed_query` — “速度怎样 / 当前车速 / 车速多少”
- `vehicle_gear_query` — “当前档位”
- `vehicle_battery_query` — “电量多少 / SOC”
- `vehicle_range_query` — “还能跑多远 / 剩余里程”
- `vehicle_ac_query` — “空调开了吗 / 空调状态”
- `vehicle_temperature_query` — “当前温度 / 车内温度 / 设定温度”
- `vehicle_door_query` — “车门关了吗”
- `vehicle_location_query` — “当前位置 / 航向多少”
- `vehicle_obstacle_query` — “前方有没有障碍物”
- `vehicle_traffic_light_query` — “红绿灯 / 交通灯”
- existing `status_query` also refreshes the read-only snapshot.

When a query is recognized, `VoicePipeline` refreshes the snapshot, mirrors values into `VehicleStateStore`, then renders the reply. Query intents are `mutatesVehicleState=false`, so they do not emit Unity control action JSON.

## 5. Debug visibility

The Android debug panel now includes:

```text
只读车况：source=... decodedKeys=... connected=...
```

The full log also records:

```text
Vehicle read-only snapshot source=simulated-redis-protobuf decodedKeys=11 connected=true
```

This lets a phone tester confirm whether the app is reading the simulated protobuf Redis source or a future real source.

## 6. Tests added

New tests cover:

- simulated protobuf Redis decoding;
- snapshot-to-string state mapping;
- malformed protobuf payload isolation;
- read-only query NLU coverage;
- read-only replies from snapshot mirror;
- end-to-end pipeline query refresh without control action JSON.

Representative command:

```bash
bash scripts/test_unit.sh
```

## 7. What remains before real Redis switch

Need from vehicle side:

1. actual Redis host/port/auth/db/network path;
2. actual key dump, especially confirming `Sensor_Mmobstacles` spelling;
3. sample binary values or Redis dump;
4. official `.proto` files if available;
5. update frequency/TTL expectations.

When real Redis is approved, implement `RealRedisBinaryDataSource` behind `BinaryVehicleDataSource`, add explicit runtime config, and then decide whether the APK should add `INTERNET` permission for real network access.
