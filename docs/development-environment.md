# 开发与联调环境配置

本文档用于从 GitHub 新 clone 本仓库后，在 Windows、Linux/WSL、Android Studio、手机真机和车辆/电脑 Redis 环境中完成开发、构建、安装和联调。

## 1. 项目基本信息

- GitHub 仓库：`vehicle-offline-voice-android`
- Android 包名：`com.company.vehiclevoice.debug`（debug 构建）
- Gradle Wrapper：Gradle `8.9`
- Android Gradle Plugin：`8.7.3`
- Kotlin Android 插件：`2.0.21`
- JDK：`17`
- Android SDK：`compileSdk 35`，`minSdk 26`，`targetSdk 33`
- 当前阶段：只读车辆状态，不发真实车辆控制命令

## 2. 通用前置要求

所有开发环境都需要：

1. Git。
2. JDK 17。
3. Android Studio 或 Android SDK Command-line Tools。
4. Android SDK Platform 35。
5. Android SDK Build-Tools，由 Android Studio/Gradle 自动安装或手动安装。
6. 可访问 `google()`、`mavenCentral()` 和 `gradlePluginPortal()` 的网络。
7. 若使用 SSH clone，需要当前机器的 GitHub SSH key 已授权访问 private 仓库。

推荐先 clone：

```bash
git clone git@github.com:weathour/vehicle-offline-voice-android.git
cd vehicle-offline-voice-android
```

如果没有配置 GitHub SSH key，也可以在有权限的账号下使用 HTTPS：

```bash
git clone https://github.com/weathour/vehicle-offline-voice-android.git
cd vehicle-offline-voice-android
```

## 3. Windows 开发环境

### 3.1 推荐安装

1. 安装 Git for Windows。
2. 安装 Android Studio。
3. 在 Android Studio 中安装：
   - Android SDK Platform 35
   - Android SDK Platform-Tools
   - Android SDK Build-Tools
4. 安装 JDK 17，或使用 Android Studio 自带的 JBR/JDK 17。

### 3.2 导入 Android Studio

1. `File -> Open`。
2. 选择仓库根目录。
3. 等待 Gradle Sync 完成。
4. 如果 Android Studio 提示 SDK 缺失，按提示安装 SDK 35。

Android Studio 通常会自动生成本机专用的 `local.properties`，例如：

```properties
sdk.dir=C\:\\Users\\<你的用户名>\\AppData\\Local\\Android\\Sdk
```

不要把 `local.properties` 提交到 Git。该文件已在 `.gitignore` 中忽略。

### 3.3 Windows 命令行构建

在 PowerShell 或 CMD 中执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Debug APK 输出位置：

```text
app\build\outputs\apk\debug\app-debug.apk
```

如果命令行提示找不到 Android SDK，请确认：

1. Android Studio 已安装 SDK。
2. 仓库根目录存在正确的 `local.properties`。
3. 或设置环境变量：

```powershell
$env:ANDROID_HOME="C:\Users\<你的用户名>\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
```

### 3.4 Windows 上运行 Redis 模拟器

仓库自带的模拟 Redis 是 Python 脚本，不依赖真实 Redis 服务。

需要 Python 3。启动方式：

```powershell
$env:SIM_REDIS_HOST="0.0.0.0"
$env:SIM_REDIS_PORT="6379"
python scripts\sim_redis_server.py --host 0.0.0.0 --port 6379
```

另开一个终端写入默认车辆状态：

```powershell
python scripts\sim_vehicle_redis.py --host 127.0.0.1 --port 6379 defaults
python scripts\sim_vehicle_redis.py --host 127.0.0.1 --port 6379 status
```

手机连接电脑 Redis 时，APK 中的 Host 要填 Windows 电脑在同一 Wi-Fi/局域网中的 IPv4 地址，而不是 `127.0.0.1`。

常见排查：

- Windows 防火墙需要允许 Python/6379 端口被手机访问。
- 手机和电脑必须在同一网络，或网络路由可达。
- 公司/车辆网络可能有客户端隔离，需要让网络管理员放通。

## 4. Linux / WSL 开发环境

### 4.1 当前仓库脚本约定

仓库提供：

- `scripts/env.sh`
- `scripts/check_android_env.sh`
- `scripts/build_debug.sh`
- `scripts/test_unit.sh`
- `scripts/smoke_sim_redis.sh`

当前本机 Codex/Linux 环境使用 `.codex/android-env.sh` 设置：

```bash
export JAVA_HOME="$HOME/.local/opt/jdk-17"
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

如果你的 Linux/WSL 路径不同，请按本机实际安装位置调整环境变量或使用 Android Studio 自动生成的 `local.properties`。

### 4.2 检查环境

```bash
source scripts/env.sh
scripts/check_android_env.sh
```

### 4.3 构建与测试

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
scripts/check_apk_permissions.sh
scripts/smoke_sim_redis.sh
git diff --check
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 4.4 WSL 注意事项

- WSL 可以用于构建 APK，但真机 USB 调试通常更适合在 Windows Android Studio/adb 中操作。
- 如果手机要访问 WSL 中启动的 Redis 模拟器，需要确认 Windows 防火墙、WSL 网络转发和绑定地址均允许外部访问。
- 为减少网络复杂度，现场测试时更推荐在原生 Windows 或 Linux 主机上启动模拟 Redis。

## 5. Android Studio 调试

1. 打开项目根目录。
2. 等待 Gradle Sync 完成。
3. 选择 `app` 配置。
4. 使用 debug 构建安装到手机或车载 Android 环境。
5. 首次启动时允许麦克风、通知等权限。

如果 Gradle Sync 失败，优先检查：

- JDK 是否为 17。
- Android SDK 35 是否安装。
- 网络是否能访问 Gradle、Google Maven、Maven Central。
- `local.properties` 中 `sdk.dir` 是否为当前机器路径。

## 6. 手机真机调试环境

### 6.1 ADB 检查

Linux/macOS：

```bash
adb devices -l
```

Windows PowerShell：

```powershell
adb devices -l
```

如果使用网络 ADB，先确保手机和电脑在同一网络，并按设备实际地址连接：

```bash
adb connect <手机IP>:<端口>
adb devices -l
```

### 6.2 安装 Debug APK

Linux/macOS：

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Windows：

```powershell
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 6.3 App 内配置

在 APK 首页填写：

- Redis Host：电脑或车辆 Redis 的 IP 地址
- Redis Port：默认 `6379`
- Redis DB：默认 `0`
- Password：无密码时留空

推荐流程：

1. 点击 **测试 Redis 连接 / 解码**。
2. 确认 `connected=true`。
3. 确认 `decoded` 数量正常。
4. 确认 `missing` 和 `decodeError` 可接受或为 0。
5. 再点击 **启动车上语音测试**。

## 7. 电脑 Redis 模拟环境

启动模拟 Redis：

```bash
SIM_REDIS_HOST=0.0.0.0 SIM_REDIS_PORT=6379 bash scripts/start_sim_redis.sh
```

写入默认 protobuf 数据：

```bash
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 defaults
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 status
```

常用修改命令：

```bash
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-speed --value 36.8
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-battery --soc 64
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-tire --alarm low --pressure 650
python3 scripts/sim_vehicle_redis.py --host 127.0.0.1 --port 6379 set-sam --scene 11 --event start --count 3
```

一键 smoke：

```bash
scripts/smoke_sim_redis.sh
```

手机侧 Host 应填写电脑局域网 IP，例如 `192.168.x.x`、`10.x.x.x`，不能填写电脑自身使用的 `127.0.0.1`。

## 8. 真实车辆 / 车载屏幕联调环境

当前 App 只读 Redis/protobuf 车辆状态，不发送真实控制命令。

真实车辆或数据隔舱联调前，需要确认：

1. Redis Host、Port、DB。
2. 是否需要密码。
3. 手机/车载屏幕与 Redis 是否同网或路由可达。
4. 是否存在防火墙、白名单或客户端隔离。
5. 真实 key 名是否与当前模拟 key 一致，尤其是 `Sam`。
6. 每个 key 的二进制样本或脱敏 Redis dump。
7. 真实 protobuf 字段枚举是否与当前文档/decoder 一致。

推荐最小联调顺序：

1. 手机或车载屏幕连接车辆网络。
2. 打开 APK，填 Redis IP、端口、DB、密码。
3. 点击 **测试 Redis 连接 / 解码**。
4. 只验证基础状态 key，例如车速、电量、胎压。
5. 再验证告警、ACC/LKA、Sam 协作场景。
6. 收集 ASR 误识别日志，必要时扩充 `RuleIntentParser` 领域归一词表。

## 9. 常见问题排查

### 9.1 Windows clone 后 `gradlew.bat` 下载 Gradle 失败

检查网络是否能访问 Gradle distribution。也可以在 Android Studio 中打开项目，让 IDE 代为下载依赖。

### 9.2 `SDK location not found`

生成或修正仓库根目录下的 `local.properties`：

```properties
sdk.dir=<你的 Android SDK 路径>
```

Windows 示例：

```properties
sdk.dir=C\:\\Users\\<你的用户名>\\AppData\\Local\\Android\\Sdk
```

Linux 示例：

```properties
sdk.dir=/home/<你的用户名>/Android/Sdk
```

### 9.3 手机连不上电脑 Redis

按顺序检查：

1. 电脑 Redis 模拟器是否绑定 `0.0.0.0:6379`。
2. 手机填写的 Host 是否是电脑局域网 IP。
3. 手机和电脑是否在同一网络。
4. 防火墙是否放通 6379。
5. 车辆/公司网络是否启用了客户端隔离。

### 9.4 App 显示 `decodeError`

说明 Redis 可读，但 payload 不能按当前 protobuf decoder 解析。需要确认：

1. key 名是否正确。
2. value 是否为预期的 protobuf 二进制。
3. 真实 protobuf 版本是否与当前 decoder 一致。
4. 是否误连到了空库或其他测试库。

### 9.5 语音识别不稳定

当前使用 Vosk 离线开放模型，不使用云 ASR/TTS。现场请尽量：

1. 先用固定问法完成联调。
2. 查看 App debug 面板或 logcat 中的 ASR 文本。
3. 把高频误识别补充到 NLU 领域归一逻辑中。

## 10. 相关文档

- 阶段二交接：`docs/phase2-read-only-vehicle-voice-handoff-2026-06-08.md`
- 阶段二 E2E 方法：`docs/phase2-e2e-test-method-2026-06-08.md`
- 阶段二测试记录：`docs/phase2-e2e-test-run-2026-06-08.md`
- 上车 UI 整理：`docs/stage3-vehicle-test-ui-plan-2026-06-08.md`
- 架构说明：`docs/architecture.md`
- 语音链路：`docs/voice-pipeline.md`
