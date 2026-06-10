# 当前 Redis 可读有用信息总览

采样时间：2026-06-10
Redis：`192.168.2.112:6379`

本次只使用只读命令读取：`PING`、`SCAN`、`GET`。

## 1. 总体

- Redis 连接正常：`PING=PONG`
- 当前可见 key 数：`30`
- 全部 key：
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

## 2. 可以直接转成 App 业务信息的内容

### 2.1 速度、定位、姿态、RTK

来源：`Sensor_Location`

当前速度应从 `Sensor_Location` 字段 `8` 读取，而不是优先从 `BC_Veh_Spd` 读取。

| 信息 | 字段 | 当前样本 |
| --- | --- | --- |
| 经度 | `2` | `106.2904477` |
| 纬度 | `3` | `29.5213987` |
| 高度 | `4` | `302.78 m` |
| pitch | `5` | `-0.023397` |
| roll | `6` | `0.009399` |
| heading | `7` | `-1.345985` |
| 速度 | `8` | `0.001637 m/s`，约 `0.005892 km/h` |
| 速度 x | `9` | `0.001157` |
| 速度 y | `10` | `0.001157` |
| 速度 z | `11` | `0.001894` |
| 线加速度 | `12` | `0.288469` |
| 加速度 x/y/z | `13/14/15` | `0.272939 / 0.093374 / 9.825804` |
| 角速度 | `16` | `0.002745` |
| 角速度 x/y/z | `17/18/19` | `0.002609 / 0.000853 / 0.001386` |
| 原点经纬度 | `20/21` | `106.3493290 / 29.5155890` |
| UTM 坐标 | `22/23/24` | `-5714.27 / 579.02 / 302.78` |
| RTK 状态 | `25` | `1` |

### 2.2 电池

来源：`DCU_Battery_St`

| 信息 | 字段 | 当前样本 |
| --- | --- | --- |
| 电压 | `2` | `636.00 V` |
| 电流 | `3` | `5.00 A` |
| SOC | `4` | `93.60 %` |

注意：当前 App 旧逻辑把字段 `3` 当 SOC，这是错的；实车当前 SOC 在字段 `4`。

### 2.3 剩余里程

来源：`DCU_INFO_St`

| 信息 | 字段 | 当前样本 |
| --- | --- | --- |
| 剩余里程/续航 | `2` | `500.00` |

注意：当前 App 旧 key 是 `DCU_INFO`，实车当前是 `DCU_INFO_St`。

### 2.4 累计/分段里程

来源：`BC_Veh_Mileage`

| 字段 | 当前样本 |
| --- | --- |
| `2` | `4001.25` |
| `3` | `4001.25` |

字段 `2/3` 对应短里程/总里程的具体命名需要用最新实车 schema 确认。

### 2.5 障碍物列表

来源：`Sensor_Mmobstacles`

- 障碍物数量字段：`19`
- repeated 障碍物条目：`19`
- 可读信息：id、车体系 x/y/z、世界坐标、尺寸、类型、置信度、经纬度等。

最近障碍物样本：

| id | XY 距离 | x | y | z | type | confidence | size | lon | lat |
| --- | ---: | ---: | ---: | ---: | --- | ---: | --- | ---: | ---: |
| `296` | `3.88 m` | `-1.78` | `3.44` | `0.45` | `10` | `0.851` | `0.70x0.68x1.77` | `106.2903939` | `29.5214115` |
| `275` | `4.42 m` | `-1.24` | `4.25` | `0.49` | `10` | `0.736` | `0.71x0.70x1.77` | `106.2904024` | `29.5214071` |
| `309` | `6.64 m` | `6.58` | `-0.89` | `0.13` | `10` | `0.802` | `0.76x0.62x1.61` | `106.2903634` | `29.5213306` |
| `251` | `7.42 m` | `2.47` | `7.00` | `0.66` | `5` | `0.676` | `6.30x2.49x2.70` | `106.2904367` | `29.5213785` |
| `250` | `17.27 m` | `-17.15` | `-2.08` | `0.11` | `3` | `0.525` | `4.38x1.75x1.74` | `106.2903128` | `29.5215406` |

注意：当前 App 只读第一个障碍物，且速度优先读字段 `8`；实车样本主要有字段 `9`，App 需要兼容。

### 2.6 规划轨迹

来源：`planned_trajectory`

这个 key 不是普通 protobuf，当前内容是文本数组。

- payload 长度：`840 bytes`
- 轨迹点数量：`20`
- 第一个点：`[-2709.7264740731334, 467.3150316257961]`
- 最后一个点：`[-2690.2067872263724, 464.0119207398966]`
- x 范围：`-2709.7264740731334` 到 `-2690.2067872263724`
- y 范围：`464.0119207398966` 到 `467.3204954545945`
- 折线长度约：`19.91`

## 3. 能读到字段，但语义需要最新实车 schema 确认

这些 key 的 protobuf 字段能解析出来，但字段含义需要与实车最新 `.proto` 对齐后，才能可靠地转成 UI/语音文本。

### 3.1 底盘/域控状态

`DCU_Drive_St`：

```text
1=timestamp
4=11.600000381469727
5=4.79296875
6=1
```

`DCU_INFO_1`：

```text
1=timestamp
2=3
3=2
5=1
```

`DCU_INFO_2`：

```text
1=timestamp
2=9
6=2
7=8
11=1
13=1
```

`DCU_L2_St`：

```text
1=timestamp
2=1
4=3
6=1
9=1
11=2
```

这些 key 都显示字段 `1` 是 timestamp，与当前 App 旧字段映射不一致。

### 3.2 车身/按键/灯光状态

`BC_AutoD_Veh_St`：

```text
1=timestamp
2=1
6=1
15=1
16=1
```

`BC_Intelligent_Ctrl_St`：

```text
1=timestamp
4=1
8=3
11=1
13=3
14=3
15=3
```

### 3.3 转向/轮速

`Strg_StrgSys_St`：

```text
1=timestamp
2=-1.100000023841858
3=0.010999999940395355
4=-2.799999952316284
6=4
7=12
```

`Rel_SpeedSteer`：

```text
1=timestamp
2=1.94140625
3=-0.0625
4=0.0625
5=-0.0625
7=8.125
8=8.125
```

### 3.4 VIN/编号片段

`VIN_INFO`：

```text
1=timestamp
2=17
3=1
4=255
5=255
6=255
7=255
8=255
9=255
```

### 3.5 SAM/协作状态

来源：`Sensor_SAM`

```text
1=timestamp-like-ms
3=1
4=0
5=level_4
6=1
7=1
8=-18.800000000000168
10=4.48322
```

这个 key 可读，但它与此前 `sam.proto` 的完整字段集不完全一致，需要最新实车 SAM proto 确认语义。

### 3.6 AS 控制/请求类 key

这些 key 可读，但看起来偏控制/请求链路，不能直接绑定语音控制：

`AS_Drive_and_Brk_Sys_Ctrl`：

```text
1=timestamp
4=1
6=2
8=10.0
9=70
10=13
```

`AS_State_FB_Req`：

```text
1=timestamp
```

`AS_State_FB_Req2`：

```text
1=timestamp
```

`AS_Strg_Ctrl`：

```text
1=timestamp
2=1.6653345369377348e-13
3=4
6=100
7=2
```

## 4. V2X / Day2 信息

以下 key 都有 payload，可以作为后续 V2X 展示或联动输入，但需要对应 Day2 protobuf/ASN schema 才能完整解释：

- `Day2_BSM_Msg`
- `Day2_MAP_Msg`
- `Day2_RSI_Msg`
- `Day2_RSM_Msg`
- `Day2_SPAT_Msg`
- `Day2_SSM_Msg`
- `Day2_VIR_Msg`

当前能确认的是 payload 存在且可按 protobuf wire 解析出顶层字段。例如：

```text
Day2_MAP_Msg: 2815 bytes
Day2_SPAT_Msg: 962 bytes
Day2_RSI_Msg: 773 bytes
Day2_SSM_Msg: 533 bytes
Day2_RSM_Msg: 249 bytes
Day2_BSM_Msg: 120 bytes
Day2_VIR_Msg: 57 bytes
```

其中 `Day2_SPAT_Msg`、`Day2_MAP_Msg` 对红绿灯/地图路口信息可能比 `Sensor_TrafficLightlist` 更有价值。

## 5. 当前只有 timestamp 或业务字段暂缺

这些 key 当前存在，但本次采样没有读到可用于 App 业务回答的核心值：

- `BC_Veh_Spd`：有时出现字段 `2`，但速度应以 `Sensor_Location` 字段 `8` 为准。
- `Sensor_TrafficLightlist`：当前只有 timestamp，没有灯色、相位、剩余时间。
- `Sensor_Lanelist`：当前只有 timestamp，没有车道线列表。

## 6. 当前缺失的 App 旧预期 key

当前 Redis 中没有：

- `ACM_INF2`
- `ACM_INF4`
- `TPMS_INFO`
- `PFC_Main_Obstacle_INF`
- `DCU_INFO`
- `Sam`
- `Sensor_Trafficlightlist`

对应地，当前无法从这些旧 key 读取空调、胎压、主目标故障、旧 SAM、旧红绿灯 key 等信息。

## 7. 对 App 最有价值的读取顺序

1. `Sensor_Location`：速度、定位、姿态、RTK。
2. `DCU_Battery_St`：电压、电流、SOC。
3. `DCU_INFO_St`：剩余里程。
4. `Sensor_Mmobstacles`：障碍物数量、最近障碍物、障碍物列表。
5. `planned_trajectory`：规划轨迹点。
6. `BC_Veh_Mileage`：里程。
7. `Strg_StrgSys_St` / `Rel_SpeedSteer`：转向、轮速相关，待 schema 确认。
8. `Day2_SPAT_Msg` / `Day2_MAP_Msg` / 其他 Day2 key：V2X 信息，待 schema 确认。
