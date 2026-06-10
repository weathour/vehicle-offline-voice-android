# 实车 Redis 语音问答矩阵（2026-06-10）

依据：

- `docs/interface-ingest/2026-06-10-live-redis-inspection/README.md`
- `docs/interface-ingest/2026-06-10-live-redis-capture/INTERPRETATION.md`
- `docs/interface-ingest/2026-06-10-real-vehicle-protos/ANALYSIS.md`
- `docs/interface-ingest/2026-06-10-real-vehicle-protos/source/protos/*.proto`

## 总原则

1. 本 APK 当前只做**只读车辆状态问答与接口诊断**，不向实车 Redis 写入控制命令。
2. 语音回复必须区分：业务值可读、key 存在但只有时间戳、key 缺失、schema 尚未确认、数据可能是缓存。
3. 实车证据优先于模拟器旧假设。模拟器兼容保留，但不能让旧字段覆盖实车字段。
4. 对红绿灯、车道线、SAM 协作场景等不完整样本，必须说明“暂不能判断”，不能误报为“没有”。

## 新增问答

| 分组 | 可以问 | 期望回答 | 主要 Redis / 字段 | 注意事项 |
| --- | --- | --- | --- | --- |
| 数据健康 | 实车数据正常吗 / Redis 数据正常吗 | 汇总连接、decoded/missing/error、timestamp-only、关键可读值 | 全部默认 key + alias | 面向上车联调第一问 |
| 数据新鲜度 | 数据是实时的吗 / 定位是实时的吗 | 说明 snapshot 更新时间、payload timestamp、是否疑似缓存 | 各 key `updatedAtMs` + payload timestamp | `Sensor_Location` 实测 payload 可能较旧 |
| 电池详情 | 电池状态怎么样 / 电池电压多少 / 当前电流多少 | SOC、电压、电流 | `DCU_Battery_St`: 2/3/4 | SOC 是字段 4，不是字段 3 |
| RTK | RTK 状态怎么样 | RTK 字段值和需车端确认的含义 | `Sensor_Location`: 25 | 不擅自解释 1 的业务含义 |
| 姿态 | 姿态怎么样 / 俯仰横滚多少 | 高程、pitch、roll、heading | `Sensor_Location`: 4/5/6/7 | heading 可继续用于位置回答 |
| 障碍物数量 | 有几个障碍物 / 周围有多少目标 | obstacle_num 和列表数量 | `Sensor_Mmobstacles`: 2/3 | 实测为 19 个 |
| 最近障碍物 | 最近障碍物多远 / 最近目标在哪里 | 最近目标距离、坐标、置信度、类型 | `Sensor_Mmobstacles` repeated obstacle | 方向定义需车端确认，优先说车体坐标 |
| 规划轨迹 | 当前有规划轨迹吗 / 轨迹有多少点 | 是否可读、点数、首尾点、长度候选 | `planned_trajectory` UTF-8 文本数组 | 不是 protobuf |
| 车道线 | 检测到车道线了吗 | key 存在但仅 timestamp 时说暂无业务数据 | `Sensor_Lanelist` | 不等价于“无车道线” |
| SAM 状态 | SAM 状态怎么样 / 协同状态怎么样 | auto level、驾驶/档位/方向盘/速度等已上报字段 | `Sensor_SAM` | scene/event/count 缺失时不猜 V2I/V2V |
| key 诊断 | 哪些 key 异常 / 哪些数据没读到 | missing、decodeError、timestamp-only 摘要 | key status | 面向现场截图和 logcat 复盘 |

## 修改问答

| 原问答 | 原行为风险 | 新行为 |
| --- | --- | --- |
| 当前车速多少 | 旧逻辑读 `BC_Veh_Spd` 字段 1，实车字段 1 是 timestamp | 优先 `Sensor_Location.linear_velocity` 字段 8，换算 km/h；`BC_Veh_Spd` 仅作可信 fallback |
| 电量多少 | 旧逻辑把 `DCU_Battery_St` 字段 3 当 SOC，实车字段 3 是电流 | SOC 改读字段 4，同时可说电压字段 2、电流字段 3 |
| 还能跑多远 / 续航多少 | 旧 key `DCU_INFO` 实车缺失 | 兼容/改用 `DCU_INFO_St` 字段 2 |
| 当前位置在哪 | 旧回复只有经纬度/航向 | 增加高程、RTK、姿态和新鲜度提示 |
| 前方有没有障碍物 | 旧逻辑只读第一个 obstacle，速度只看字段 8 | 支持 count、nearest、字段 9 速度、尺寸、经纬度；用距离而非简单“前方”过度解释 |
| 红绿灯什么颜色 | 旧 key 大小写不匹配；实车样本只有 timestamp | 改为 `Sensor_TrafficLightlist` alias；只有 timestamp 时说暂无颜色/相位/剩余时间 |
| 当前协作场景是什么 | 旧 key `Sam` 实车缺失，且实车样本 scene_id 可能缺失 | 改为 `Sensor_SAM`；scene_id 缺失时说无法判断具体 V2I/V2V 场景 |
| 当前车辆状态 | 旧摘要可能混入错误底盘字段 | 摘要优先稳定字段：连接、速度、电池、续航、定位、障碍物、SAM partial、timestamp-only 提示 |

## 暂缓或降级问答

这些问答在 App 中可保留“暂未可靠读取 / schema 待确认”的回复，但不应作为本轮稳定能力宣传：

| 问答 | 暂缓原因 |
| --- | --- |
| ACC 状态 / LKA 状态 | `DCU_L2_St` 实车字段含 timestamp，旧 enum 映射不可靠 |
| 为什么不能进入自动驾驶 / 为什么退出自动驾驶 | `DCU_INFO_2` 实车字段与旧解析不一致 |
| 是否需要接管 | 同上，字段需车端 schema 确认 |
| 胎压正常吗 | 本次实车快照缺少 `TPMS_INFO` |
| 空调状态 / 温度多少 | 本次实车快照缺少 `ACM_INF2`、`ACM_INF4` |
| 车门状态 / 喇叭 / 雨刮 | `BC_AutoD_Veh_St` 样本缺少旧逻辑读取字段 |

## 不启用的真实控制问答

以下语音指令不得接入实车 Redis 写入：

- 打开/关闭空调
- 打开/关闭车窗
- 控制方向盘、油门、刹车、档位
- 任何远程驾驶控制命令

原因：本轮实车更新只提供状态读取和部分控制/request-like key 观测，没有经过安全设计、确认、授权和限流的真实车控写入接口。开发/预览模式的 mock action 可保留，但必须与实车只读模式隔离。

## 推荐现场测试顺序

1. “小车小车，实车数据正常吗”
2. “小车小车，当前车速多少”
3. “小车小车，电量多少”
4. “小车小车，还能跑多远”
5. “小车小车，当前位置在哪”
6. “小车小车，RTK 状态怎么样”
7. “小车小车，有几个障碍物”
8. “小车小车，最近障碍物多远”
9. “小车小车，当前有规划轨迹吗”
10. “小车小车，红绿灯什么颜色”
11. “小车小车，检测到车道线了吗”
12. “小车小车，SAM 状态怎么样”
13. “小车小车，哪些 key 异常”
