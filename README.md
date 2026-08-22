# 渝行智声车载离线语音交互软件

“渝行智声”v1.0 是面向手机和车载 Android 终端的车载离线语音交互软件。设备连接车辆只读 Redis 数据源后，可通过离线语音查询车速、电量、告警和协作场景等车辆状态。

著作权人：长安大学。主要开发人员：杨兴杰、陈婷、徐志刚、王嘉鑫、申丹丹。正式包名：`cn.edu.chd.yuxingvoice`。

## 当前状态

已完成：

- Android 真机离线语音链路：唤醒词、VAD、Vosk ASR、规则 NLU、Android TTS。
- 只读 Redis/protobuf 车辆状态读取。
- 手机读取电脑 Redis 模拟器并完成端到端测试。
- 车速、电量、定位、感知、轨迹和 Sam 协作场景等只读问答。
- ASR 常见误识别收敛，例如“写作/协同/合作”收敛为“协作”，“常见/场见”收敛为“场景”。
- TTS 朗读前把 `V2I/V2V/V2X` 转为 `V突I/V突V/V突X`。
- 正式使用 UI：填写 Redis IP/端口，检测数据连接后启动离线语音服务。

## 使用流程

1. 手机连接车辆网络。
2. 打开 APK。
3. 填 Redis IP、端口、DB，密码可留空。
4. 点击 **检测车辆数据连接**。
5. 确认面板显示：
   - `connected=true`
   - `decoded` 数量正常
   - `missing` 和 `decodeError` 可接受或为 0
6. 点击 **启动离线语音服务**。
7. 使用语音查询：
   - 小车小车，当前车速多少
   - 小车小车，电量多少
   - 小车小车，最近障碍物
   - 小车小车，当前协作场景是什么
   - 小车小车，现在有几辆协作车
   - 小车小车，Redis 状态
8. 在目标车载 Android 设备上按同样方式配置车辆数据连接。

## 本地模拟 Redis

电脑侧启动模拟 Redis：

```bash
SIM_REDIS_HOST=0.0.0.0 SIM_REDIS_PORT=6379 bash scripts/start_sim_redis.sh
```

写入默认 protobuf 数据：

```bash
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 defaults
```

设置协作场景：

```bash
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-sam --scene 11 --event start --count 3
```

## 开发验证命令

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
scripts/check_apk_permissions.sh
scripts/smoke_sim_redis.sh
git diff --check
```

Debug APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 重要说明

- 当前阶段只读车辆状态，不发真实车辆控制命令。
- `INTERNET` 权限是有意加入，用于手机 / 车载屏幕读取车辆或电脑 Redis。
- 不使用云 ASR/TTS。
- Vosk restricted grammar 暂不启用；中文整句 grammar 已验证会导致 `[unk]`，当前采用开放 ASR + NLU 领域纠错。

## 文档入口

- 开发与联调环境配置：`docs/development-environment.md`
- 阶段二交接：`docs/phase2-read-only-vehicle-voice-handoff-2026-06-08.md`
- 阶段二测试记录：`docs/phase2-e2e-test-run-2026-06-08.md`
- 上车 UI 整理：`docs/stage3-vehicle-test-ui-plan-2026-06-08.md`
- 接口文档入库：`docs/interface-ingest/2026-06-08/`
- 架构说明：`docs/architecture.md`
- 语音链路：`docs/voice-pipeline.md`
