# TV Strip Factory Test Android App

工厂专用 Android 测试 App。App 通过 BLE 扫描和连接 TV Strip 设备，支持按 RSSI 自动勾选、批量连接、设置 PID、红/绿/蓝/白/黑设灯、最高亮度颜色测试和一键解绑删除。

## 使用流程

1. 安装 Debug APK 到支持 BLE 的 Android 设备。
2. 打开系统蓝牙，并在 App 首次启动时授予 BLE 扫描/连接权限。Android 12 及以上需要 `BLUETOOTH_SCAN` 和 `BLUETOOTH_CONNECT`，Android 11 及以下需要定位权限用于 BLE 扫描。
3. 在 `Product Type / PID` 区域选择产品类型和目标 PID。目前 STV1 已配置常用 PID：Online 2M/3M/5M、Offline 3M/5M、Offline 3M High Density。
4. 按现场距离调整 `RSSI threshold`，并设置 `Target count`。扫描结果会按 RSSI 从高到低排列，达到阈值且在目标数量内的设备会自动勾选，也可以手动勾选/取消。
5. 点击 `Scan` 开始扫描设备，确认目标设备被选中后点击 `Batch connect` 批量连接。
6. 连接成功后按测试需求执行操作：
   - `设置 PID`：写入当前选择的 PID。
   - `设灯红/绿/蓝/白/黑`：按当前产品/PID 对应灯珠数量下发普通设灯颜色测试。
   - `最高亮度红/绿/蓝/白/黑`：选择最高亮度测试颜色，默认颜色为白色。
   - `Highest brightness color`：按当前产品/PID 对应灯珠数量下发最高亮度颜色测试，亮度为 1000。
   - `一键解绑删除`：对已连接或 Ready 状态设备发送解绑删除命令，并关闭对应 BLE 会话。
7. 观察列表中的 `state`、`Last` 和 `Error` 字段确认每台设备的执行结果。如需重新选择设备，可停止扫描后再次扫描。

## 当前限制

- 当前主要面向 STV1 工厂测试流程；S2、SW1、S1_V2 已在界面预留产品类型，但没有 PID/灯珠数量配置，涉及设灯或最高亮度颜色测试时会失败。
- App 仅通过 BLE 与设备通信，不包含云端账号、MQTT、OTA 或产测记录上传能力。
- 批量连接/初始化和批量命令最多 3 台设备并发执行，避免严格串行带来的等待过长，同时限制 BLE 并发压力。
- 设备筛选依赖 BLE 广播名称或服务 UUID，以及当前环境下的 RSSI；强干扰、多设备近距离重叠时需要人工复核选择结果。
- 目前没有在 App 内持久化产品/PID、RSSI 阈值、目标数量或测试结果；重启后恢复默认设置。
- 本仓库不要求提交 Gradle Wrapper；GitHub Actions 会先使用 `setup-gradle` 安装 Gradle 8.10.2，再生成 wrapper 并执行 `./gradlew`。

## GitHub Actions 构建

项目的 CI 配置位于 `.github/workflows/android-build.yml`。触发方式：

- 推送代码到 GitHub。
- 在 GitHub Actions 页面手动执行 `workflow_dispatch`。

Workflow 会在 `ubuntu-latest` 上执行以下步骤：

1. Checkout 仓库。
2. 使用 Temurin JDK 17。
3. 通过 `gradle/actions/setup-gradle@v4` 安装 Gradle 8.10.2。
4. 运行 `gradle wrapper --gradle-version 8.10.2`。
5. 运行 `./gradlew test assembleDebug`。
6. 上传 Debug APK artifact，名称为 `factory-tv-strip-debug-apk`，路径匹配 `app/build/outputs/apk/debug/*.apk`。

注意：workflow 不要求本地提交 wrapper 文件，而是在 GitHub runner 上生成后使用 `./gradlew` 完成测试和 Debug APK 构建。

## 本地验证

如果本机已安装 Android SDK、JDK 17 和 Gradle，可在仓库根目录运行：

```bash
gradle wrapper --gradle-version 8.10.2
./gradlew test assembleDebug
```

如果本机没有 Gradle 或 Android SDK，请以 GitHub Actions 构建结果为准。
