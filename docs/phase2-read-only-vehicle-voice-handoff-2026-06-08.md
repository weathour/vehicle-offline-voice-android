# Phase 2 交接文档：只读车辆状态语音接入与端到端模拟验证

日期：2026-06-08  
仓库建议名：`vehicle-offline-voice-android`  
Android 包名：`com.company.vehiclevoice.debug`  
当前阶段状态：**已完成，可进入真实车辆 / 数据隔舱 Redis 端到端联调准备**

## 1. 本阶段目标

本阶段围绕“只读车辆状态接入”完成 Android APK 的软件侧与测试条件闭环：

1. 手机端离线语音链路：唤醒词 → VAD → Vosk 离线 ASR → 规则 NLU → 本地 TTS。
2. 电脑侧模拟 Redis：可写入 protobuf 二进制车辆状态与协作信息。
3. 手机通过 Wi-Fi 读取电脑 Redis：验证 APK 能直接读取远程 Redis key 并解析 protobuf。
4. 车辆状态问答：覆盖基础状态、告警解释、L2/自动驾驶状态、协作驾驶 Sam 信息。
5. 语音鲁棒性适配：把 ASR 常见误识别收敛到本应用意图。

## 2. 已完成能力

### 2.1 只读车辆状态数据源

APK 当前支持两种数据源：

- APK 内置模拟数据：不依赖网络。
- 电脑 / 车辆侧 Redis：手机与电脑在同一网络时，通过 `host:port/db` 读取。

当前 UI 中可勾选：`读取电脑 Redis（用于手机读取本电脑模拟数据库）`。

已验证电脑 Redis：

- Host：`10.85.145.111`
- Port：`6379`
- Key 数量：15
- 主要 key：`BC_Veh_Spd`、`DCU_Battery_St`、`DCU_INFO_2`、`DCU_L2_St`、`TPMS_INFO`、`PFC_Main_Obstacle_INF`、`Sam` 等。

### 2.2 Protobuf / Redis 模拟环境

新增脚本：

- `scripts/sim_redis_server.py`：内存 Redis 子集服务，支持测试所需 GET/SET/KEYS/DEL。
- `scripts/start_sim_redis.sh`：启动模拟 Redis。
- `scripts/sim_vehicle_redis.py`：写入默认车辆 protobuf 数据、修改车速/电量/胎压/告警/L2/Sam 等字段。
- `scripts/smoke_sim_redis.sh`：一键 smoke，验证模拟 Redis 与 protobuf 写入/损坏/删除场景。

常用命令：

```bash
# 启动电脑侧 Redis 模拟器
SIM_REDIS_HOST=0.0.0.0 SIM_REDIS_PORT=6379 bash scripts/start_sim_redis.sh

# 写入默认车辆状态
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 defaults

# 修改车速
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-speed --value 36.8

# 修改 Sam 协作场景为 V2I 动态车速限制，协作车 3 辆
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-sam --scene 11 --event start --count 3
```

### 2.3 语音问答范围

当前规则 NLU 已覆盖以下只读查询：

- 车速、档位、电量、续航里程。
- 空调状态、温度、车门、位置 / 航向。
- 前方障碍物、交通灯、胎压。
- 智能驾驶 / 自动驾驶状态、ACC、LKA。
- 不能进入 / 退出自动驾驶原因。
- 接管提醒、故障 / 告警摘要。
- Sam 协作场景、协作事件、协作车数量、协作决策 / 反馈 / 行为。
- 车辆状态 / 车况总体查询。

### 2.4 语音鲁棒性适配

已根据真机测试补充领域归一和模糊匹配，例如：

- `写作 / 协同 / 合作 / 协做 / 协坐 / 协助` → 收敛为“协作”。
- `常见 / 场见 / 长景 / 场境` → 收敛为“场景”。
- `车素 / 车数 / 测速` → 收敛为“车速”。
- `胎呀 / 胎牙 / 台压` → 收敛为“胎压”。
- `告井 / 告紧` → 收敛为“告警”。
- `接官 / 借管` → 收敛为“接管”。

真机已验证：

- ASR：`当前 协作 常见 是 什么` → NLU：`vehicle_cooperation_scene_query`。
- ASR：`当前 写作 车辆 有 多少 辆` → NLU：`vehicle_cooperation_count_query`。

### 2.5 TTS 读法

新增 `TtsPronunciationFormatter`，真实 Android TTS 朗读前会把工程文本：

- `V2V` → `V突V`
- `V2I` → `V突I`
- `V2X` → `V突X`

UI 与 logcat 仍保留标准工程文本 `V2I/V2V/V2X`，只影响实际朗读。

## 3. 真机验证摘要

详细记录见：`docs/phase2-e2e-test-run-2026-06-08.md`。

已通过的关键 E2E：

1. 电脑 Redis 改车速 `36.8`，手机语音问“当前车速多少”，回复 `当前车速：36.8公里每小时`。
2. 电脑 Redis 改电量 `64%`，手机语音问“电量多少”，回复 `当前电量：64.0%`。
3. 电脑 Redis 改胎压为低压告警，手机语音问“胎压正常吗”，回复低胎压告警。
4. 电脑 Redis 写入自动驾驶 / L2 / Sam 协作综合场景，手机语音问“当前有什么告警”，成功读取告警摘要。
5. 手机语音问“当前协作场景是什么”，即使 ASR 识别成“协作常见”，仍返回 `V2I动态车速限制`。
6. 手机语音问协作车辆数量，即使 ASR 识别成“写作车辆”，仍返回 `3辆`。

## 4. 验证命令

当前已运行并通过：

```bash
./gradlew :app:testDebugUnitTest --rerun-tasks
./gradlew :app:assembleDebug
scripts/check_apk_permissions.sh
scripts/smoke_sim_redis.sh
git diff --check
```

## 5. 主要变更文件

核心 Android 代码：

- `app/src/main/java/com/company/vehiclevoice/core/VoicePipeline.kt`
- `app/src/main/java/com/company/vehiclevoice/core/VoicePipelineFactory.kt`
- `app/src/main/java/com/company/vehiclevoice/VoiceForegroundService.kt`
- `app/src/main/java/com/company/vehiclevoice/MainActivity.kt`
- `app/src/main/java/com/company/vehiclevoice/nlu/RuleIntentParser.kt`
- `app/src/main/java/com/company/vehiclevoice/template/ReplyTemplateEngine.kt`
- `app/src/main/java/com/company/vehiclevoice/data/readonly/*`
- `app/src/main/java/com/company/vehiclevoice/tts/TtsPronunciationFormatter.kt`

测试与模拟：

- `app/src/test/java/com/company/vehiclevoice/nlu/ReadOnlyVehicleQueryIntentTest.kt`
- `app/src/test/java/com/company/vehiclevoice/template/ReadOnlyVehicleReplyTemplateTest.kt`
- `app/src/test/java/com/company/vehiclevoice/tts/TtsPronunciationFormatterTest.kt`
- `app/src/test/java/com/company/vehiclevoice/data/readonly/*`
- `scripts/sim_redis_server.py`
- `scripts/sim_vehicle_redis.py`
- `scripts/smoke_sim_redis.sh`

接口文档入库：

- `docs/interface-ingest/2026-06-08/`
- `docs/two-phase-read-only-vehicle-state-plan-2026-06-08.md`
- `docs/phase2-e2e-test-method-2026-06-08.md`
- `docs/phase2-e2e-test-run-2026-06-08.md`

## 6. 已知边界与注意事项

1. 当前阶段仅做只读状态问答，不发真实车辆控制命令。
2. `INTERNET` 权限是有意加入，用于手机读取电脑 / 车辆侧 Redis。
3. 真实麦克风 ASR 继续使用 Vosk 开放模型，不启用 Vosk restricted grammar。此前验证过中文整句 grammar 会导致大量 OOV 并输出 `[unk]`。
4. 当前鲁棒性策略在 NLU 层完成，适合继续按真机日志扩充领域纠错词表。
5. 真车阶段仍需确认真实 Redis：host、port、db、key 名、是否鉴权、protobuf 是否与当前文档一致。

## 7. 下一阶段建议

进入真实车辆 / 数据隔舱 Redis 联调前，需要向车辆侧确认：

1. 真实 Redis 地址、端口、db、是否需要密码。
2. 真实 key 名是否与当前模拟 key 一致，尤其是 `Sam`。
3. 每个 key 的二进制样本或脱敏 Redis dump。
4. 手机与车辆 Redis 是否同网、是否存在防火墙 / 白名单。
5. 真车端字段枚举是否与当前 PDF / proto 文档一致。

推荐下一阶段测试顺序：

1. 手机连接真实 Redis，但先只读基础 key。
2. 对比真实 Redis payload 与当前 protobuf decoder 的解析结果。
3. 执行车速、电量、胎压、告警、Sam 场景五类最小闭环。
4. 收集 ASR 误识别日志，继续扩展 `RuleIntentParser` 领域归一。

## 8. GitHub 推送

计划创建 GitHub private 仓库：`vehicle-offline-voice-android`。

推送后请以 GitHub private 仓库作为后续多人协作入口。
