# Phase 2 端到端测试记录（2026-06-08）

环境：
- 手机：10.85.250.222:38901
- APK：com.company.vehiclevoice.debug
- 电脑 Redis 模拟器：10.85.145.111:6379

## 测试结果

### A1 车速读取
- Redis 操作：`BC_Veh_Spd speed=36.8`
- 手机语音：小车小车，当前车速多少
- 结果：通过
- 证据：UI 回复 `当前车速：36.8公里每小时`；logcat `decodedKeys=15 connected=true`。

### A2 电量读取
- Redis 操作：`DCU_Battery_St soc=64.0`
- 手机语音：小车小车，电量多少
- 结果：通过
- 证据：UI 回复 `当前电量：64.0%`；logcat `decodedKeys=15 connected=true`。

### A6 胎压告警读取
- Redis 操作：`set-tire --alarm low --pressure 650`
- 手机语音：小车小车，胎压正常吗
- 结果：通过
- 证据：logcat `vehicle_tire_query`、`decodedKeys=15 connected=true`、TTS `胎压存在告警：压力过低，压力650千帕，温度36.0度`。

### B1/B2 综合告警读取
- Redis 操作：`set-warning --auto-limit 32 --takeover 1 --low-fault 2`、`set-l2 --acc-status 7 --acc-fail 9 --lka-status 5 --lka-fail 12`、`set-sam --scene 11 --event start --count 3`
- 手机语音：小车小车，当前有什么告警
- 结果：通过
- 证据：UI 回复 `车辆告警：门未关闭，低压二级故障，激活提醒；胎压：正常；感知：无故障`；logcat `decodedKeys=15 connected=true`，snapshot 含 `L2：ACC故障，LKA故障` 与 `cooperation=V2I动态车速限制，开始，协作车3辆`。

### 语音鲁棒性适配验证
- 真机 ASR：`当前 协作 常见 是 什么`
- NLU 结果：`vehicle_cooperation_scene_query`
- Redis 读取：`remote-redis://10.85.145.111:6379/db0 decodedKeys=15 connected=true`
- TTS 回复文本：`当前协作场景：V2I动态车速限制，类型V2I`
- 结果：通过。说明“协作场景”被识别为“协作常见”时，仍可收敛到协作场景查询。

### 协作车数量鲁棒性验证
- 真机 ASR：`当前 写作 车辆 有 多少 辆`
- NLU 结果：`vehicle_cooperation_count_query`
- Redis 读取：`remote-redis://10.85.145.111:6379/db0 decodedKeys=15 connected=true`
- TTS 回复文本：`当前协作车数量：3辆`
- 结果：通过。说明“协作”被识别为“写作”时，仍可收敛到协作车数量查询。

### 追加修复
- 发现真机短语 `检查 当前 车辆 状态` 曾落入 fallback。
- 已追加为 `status_query` 口令族：`检查/查看/确认车辆状态`、`车况`、`整车状态`。
- 已重新运行单元测试、构建 APK 并覆盖安装到手机。
