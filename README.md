# TV Strip Factory Test Android App

工厂专用 Android 测试 App，仅识中文工人可用。**主功能**：扫描 TV Strip 设备 → 自动配对 → 颜色测试 → 一键解绑。
**辅助工具**：写 PID（出厂偶尔补救）。

## 启动页

进入 App 即看到二选一：

- **测试设备**（主功能）— 4 步向导
- **写 PID（工厂出厂用）**（辅助）— 单台扫描连接 → 写入指定 PID

## 测试流程（4 步）

### Step 1：选择测试条件

- 产品类型：目前仅 STV1 可用
- PID 筛选：6 种 PID 中选 1（线上 2/3/5 米、线下 3/5 米、线下 3 米高密）；或选「不筛选 PID（任意）」
- 「只显示未绑定的设备」开关：默认开（过滤掉已被别人配过的灯带）

PID 与绑定状态从 BLE 广播包 manufacturer-specific data 解析（不需要连接），见 `AdvDataParser`。

### Step 2：脉冲雷达自动配对

- 把设备一台一台凑近手机
- 进入虚线蓝圈（自动配对中心区） + 稳定 1.5 秒 → 自动连接 + 验 PID
- 顶部计数器跳动 + 30ms 震动反馈
- 右上角 ⚙ 调节配对灵敏度（5 档：紧贴 / 很近 / 近 / 中 / 较远）

### Step 3：颜色测试

- 6 个大按钮（红 / 绿 / 蓝 / 白 / 黑 / 关），点一下所有已配设备同时变色
- 「最高亮度」Tab：单按钮触发最大功率（用于 EMC 测试）
- 颜色测试不依赖设备 PID / LED 数（固件 `P10001<rgb>` 自适应）

### Step 4：解绑

- 大红按钮「全部解绑并删除」+ 二次确认
- 进度条显示 N/M
- 完成后回到 Step 1

## 写 PID 工具

- 进入后选目标 PID
- 点「开始写入」→ 自动扫描 5 秒 → 取 RSSI 最强 1 台 → 连接 → 写入 → 校验
- 成功后「再写一台」继续

## 权限

- Android 12+ 需要 `BLUETOOTH_SCAN` 和 `BLUETOOTH_CONNECT`
- Android 11- 需要 `ACCESS_FINE_LOCATION`（BLE 扫描要求）
- 蓝牙必须打开

## 当前限制

- 仅 STV1 产品类型
- 不支持 MQTT / 云端 / OTA / 账号系统
- BLE 连接队列上限 1（避免压力）；颜色 / 解绑批量并发 3
- 无持久化用户偏好

## CI 构建

`.github/workflows/android-build.yml`：

- 触发：push 或手动 `workflow_dispatch`
- 步骤：Temurin JDK 17 → Gradle 8.10.2 → 生成 wrapper → `./gradlew test assembleDebug`
- APK 发布到 GitHub Release `debug-latest`

## 本地验证

```bash
./gradlew test            # 单元测试
./gradlew assembleDebug   # 出 debug APK
```

## 技术发现（保留备忘）

1. **广播 manufacturer data 含 PID + 绑定状态**：固件 `le_ble.c` 在 manufacturer-specific data 段塞了 `factory_data`（'L''P' 头 + 绑定字节 + MAC + PID uint32）。本 App 解析这段而不是连后再读。
2. **颜色指令不需 LED 数**：`N01:P10001<rgb>;` 是固件「整条灯带都设这个 RGB」的指令，自适应实际灯珠数。
3. **最大功率指令是 hardcoded 字符串**：`N01:B21001200E5018003E80064;`，不需选色。
