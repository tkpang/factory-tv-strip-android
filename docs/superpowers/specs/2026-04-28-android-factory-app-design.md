# Android 工厂测试 App 设计文档

## 目标

开发一个仅用于工厂测试的 Android App，用于 TV 灯带产品产测。App 维护在新的独立 GitHub 仓库中，并通过 GitHub Actions 编译 APK，不依赖本地 Android 编译环境。

App 通过 BLE 连接附近设备，允许工厂操作员选择产品类型、连接设备数量、BLE 信号过滤阈值和最高功率测试颜色，然后批量下发工厂灯效指令，测试完成后支持一键解绑。

## 仓库策略

新建独立仓库：`factory-tv-strip-android`。

该仓库与 T23 固件仓库平级放置：

```text
/home/jenkins_home/workspace/
├── t23_tv_project/
└── factory-tv-strip-android/
```

这样可以把 Android 工具链、GitHub Actions、APK 产物和 App 发布流程从 T23 固件仓库中拆开。当前 T23/ESP32 仓库只作为协议参考，不直接承载 Android 工程。

GitHub personal access token 位于 `/home/pangtiankai/code/paper_paper/key`。该 token 只在需要创建 GitHub 远程仓库或推送代码时用于认证，不能打印、写入源码、写入文档或提交到 git。

## 现有协议参考

Android App 不依赖 `App/le_algo/le_vision.c`。该文件属于 T23 视觉算法模块，不是 App 入口，也不是 BLE 协议入口。

需要参考的现有代码：

- ESP32 BLE 实现：`/home/pangtiankai/code/le_light_app_new_stv2/le_light_app/le_light_sdk/le_light_sdk/wireless/le_ble.c`
- ESP32 BLE 常量：`/home/pangtiankai/code/le_light_app_new_stv2/le_light_app/le_light_sdk/le_light_sdk/wireless/le_ble.h`
- 消息命令和结构体：`/home/pangtiankai/code/le_light_app_new_stv2/le_light_app/le_light_libs/le_msg.h`
- TV 灯带 DP 处理：`/home/pangtiankai/code/le_light_app_new_stv2/le_light_app/app/tv_strip/app_tv_strip_dp.c`
- ESP32 产品类型表：`/home/pangtiankai/code/le_light_app_new_stv2/le_light_app/app/dev_table.cmake`
- T23 产品映射：`/home/jenkins_home/workspace/t23_tv_project/App/le_tv_light/le_product_config.h`
- T23 DP 处理：`/home/jenkins_home/workspace/t23_tv_project/App/le_tv_light/le_gen_msg.c`

BLE 关键值：

- BLE 广播名：`LP`
- BLE service UUID：`1e2aa501-7292-4263-a8f1-be907f039a1f`
- BLE write characteristic UUID：`1e2aa502-7292-4263-a8f1-be907f039a1f`
- BLE notify/indicate characteristic UUID：`1e2aa503-7292-4263-a8f1-be907f039a1f`

关键命令和 DP：

- `LE_CMD_DEV_INFO_GET = 0x1000`：查询设备信息
- `LE_CMD_DEV_INFO_GETR = 0x1001`：设备信息返回
- `LE_CMD_BOND = 0x1002`：绑定
- `LE_CMD_BONDR = 0x1003`：绑定结果
- `LE_CMD_UNBOND = 0x1004`：解绑
- `LE_CMD_UNBONDR = 0x1005`：解绑结果
- `LE_CMD_DP_PRP_SET = 0x1100`：下发 DP JSON
- 产品 PID DP：`d158`
- Groove 指令 DP：`d160`
- Groove 开关 DP：`d161`
- 最大亮度 DP：`d162`

## 技术方案

使用原生 Android：Kotlin + Jetpack Compose。

选择原因：

- Android BLE 原生 API 可控性更好，适合工厂批量连接和调试。
- GitHub Actions 可以稳定执行 Gradle 编译并产出 APK。
- Kotlin 协议层可以保持小而清晰，便于写单元测试。
- Compose 足够支撑工厂工具界面，减少 XML 布局维护成本。

最低 Android 版本建议为 Android 8.0。如果工厂测试手机存在更低版本，再单独调整。target SDK 使用当前稳定 Android Gradle Plugin 默认推荐版本。

## 工程结构

```text
factory-tv-strip-android/
├── app/
│   ├── src/main/java/.../ui/          # Compose 页面和 UI 状态
│   ├── src/main/java/.../ble/         # BLE 扫描、连接、GATT、通知队列
│   ├── src/main/java/.../protocol/    # le_msg 封包、CRC、AES-CBC、DP JSON
│   ├── src/main/java/.../factory/     # 工厂测试流程编排
│   └── src/main/java/.../model/       # 产品、设备、测试配置模型
├── .github/workflows/android-build.yml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

包名：`com.tkpang.tvstriptest`。

App 名称：`TV Strip Factory Test`。

## 用户流程

主界面包含测试参数区、操作按钮区、设备列表和统计状态。

操作员可配置：

- 产品类型：来自 ESP32 编译环境 `app/dev_table.cmake` 里的 `DEV_NAME`，例如 `STV1`、`S2`、`SW1`、`S1_V2` 等。App 不能把 `111/112/143/144/145/158` 当作产品类型，因为这些是 STV1 这个产品类型下面的 PID/SKU。
- 产品 PID/SKU：当所选产品类型存在多个 PID 时显示，例如 `STV1` 下可以再选择 `111/112/143/144/145/158`。是否需要下发 PID 取决于该产品类型是否支持对应 DP；STV1 可通过 `d158` 设置。
- 目标连接设备数量，例如 `1` 到 `20`。
- BLE RSSI 阈值，默认 `-65 dBm`。
- 最高功率颜色，默认 `#FFFFFF`。
- 最大亮度值，对应 `d162`，默认 `1000`。

主操作：

- 扫描附近设备。
- 自动勾选 RSSI 高于阈值的设备。
- 点击连接，批量连接已勾选设备。
- 必要时设置产品 PID/SKU。
- 下发 EMC 模板灯效。
- 下发最高功率颜色。
- 一键解绑所有已连接设备。
- 停止当前流程并清空结果。

每个设备显示：

- MAC 地址。
- DID，设备返回时显示。
- PID，设备返回时显示。
- 固件版本，设备返回时显示。
- RSSI。
- 连接状态。
- 绑定状态。
- 最后一条命令结果。
- 最后一条错误信息。

整体统计显示：

- 已发现设备数量。
- 已连接设备数量。
- 命令成功数量。
- 命令失败数量。
- 已解绑设备数量。

## BLE 扫描和连接设计

扫描条件：匹配 service UUID 或广播名 `LP`。

扫描结果按 RSSI 从强到弱排序展示。RSSI 高于用户阈值的设备自动打勾，低于阈值的设备默认不勾选但仍可手动选择。操作员确认后点击连接，App 才批量连接当前已勾选设备。

如果已勾选数量超过目标设备数量，默认只自动勾选 RSSI 最强的前 N 个设备，其中 N 为目标连接设备数量。操作员可以手动调整勾选状态。

为了降低 Android BLE 栈不稳定风险，连接和初始化流程需要排队控制。默认最多同时执行 3 个连接/初始化任务。设备完成连接、发现服务、订阅通知和握手后，再进入可下发命令状态。

Android 权限处理：

- Android 12 及以上：需要 `BLUETOOTH_SCAN` 和 `BLUETOOTH_CONNECT`。
- Android 11 及以下：BLE 扫描需要定位权限。
- 当权限不足或蓝牙关闭时，界面必须明确提示阻塞原因，并提供重试入口。

## 协议设计

Kotlin 协议层复刻固件协议，不直接导入 C 代码。

协议层职责：

- 构造和解析 `le_msg_hdr_t` 帧。
- 按固件实现兼容 CRC16。
- 按固件实现兼容 AES-128-CBC 加解密。
- 第一版优先支持非分包消息；如果命令 payload 超过 MTU，再补充分包。
- 维护命令序号，并按序号匹配响应。
- 严格按固件现有大小端规则编码和解码字段。

BLE 握手流程：

1. 连接 GATT 设备。
2. 发现 service 和 characteristic。
3. 订阅 notify/indicate characteristic。
4. 发送 `LE_CMD_DEV_INFO_GET`，payload 包含 mark `0x5a5aa5a5`、手机 MTU、random 和当前 UTC 秒。
5. 解析 `LE_CMD_DEV_INFO_GETR`。
6. 用 random 更新第二阶段 AES key。
7. 发送 `LE_CMD_BOND`。
8. 进入 DP 指令下发流程。

第一条查询设备信息命令使用第一阶段 BLE AES key/IV。后续命令使用第二阶段 BLE AES key/IV，并用查询设备信息时的 random 更新 key，行为与 ESP32 代码保持一致。

## 产品类型和 PID/SKU

产品类型以 ESP32 编译环境为准，来源是 `app/dev_table.cmake` 中的 `DEV_NAME` 字段。示例包括 `STV1`、`S2`、`SW1`、`S1_V2` 等。

`DEV_NAME` 决定 ESP32 工程编译时的产品类型，例如编译 STV1 固件时使用 `DEV_NAME=STV1`。App 的“产品类型”选择也应使用同一套命名，避免把 PID/SKU 误当成产品类型。

PID/SKU 是产品类型下面的具体型号编号。例如 `STV1` 在当前代码里对应 `111_112`，而 T23/TV 灯带代码里还扩展了 `111/112/143/144/145/158` 这些工厂和线下型号。App 需要把它们展示为 `STV1` 下的 PID/SKU 选项，而不是独立产品类型。

App 内部维护一份产品目录，第一版可以手工从 `dev_table.cmake` 和相关产品配置中整理；后续可以增加脚本从 ESP32 编译表生成 Kotlin 配置，减少人工同步错误。

## 工厂指令

设置 STV1 PID/SKU：

```json
{"d158":111}
```

`d158` 只表示设置 PID/SKU，不表示设置 App 里的产品类型。对非 STV1 产品是否存在等价设置接口，需要根据 ESP32 对应产品代码确认；没有确认前不强行下发。

打开 Groove 灯效：

```json
{"d161":1}
```

设置最大亮度：

```json
{"d162":1000}
```

普通设灯：

操作员点击“设灯”后选择目标颜色，App 对已连接设备下发对应颜色指令。第一版支持整灯同色，颜色由用户选择。

最高亮度颜色：

App 根据用户选择的 RGB 颜色和产品 LED 数量生成整条灯带纯色 groove 指令。第一版先支持全灯带同色。

STV1 PID/SKU 的 LED 数量来自当前固件产品表：

- `111`：36 颗 LED
- `112`：50 颗 LED
- `143`：48 颗 LED
- `144`：68 颗 LED
- `145`：72 颗 LED
- `158`：24 颗 LED

EMC 模板指令：

第一版内置红、绿、蓝、白、黑和自定义最高功率颜色模板。如果工厂需要完全复刻按键 EMC 模式里的固定切换序列，需要再确认最终序列后作为命名模板加入。

一键解绑：

发送 `LE_CMD_UNBOND`，payload mark 为 `0x5a5aa5a5`，等待 `LE_CMD_UNBONDR` 或超时。每个设备独立标记为解绑成功、失败或超时。

## 指令下发策略

第一版优先使用逐设备 BLE 下发。实现方式最直接，失败隔离清晰，适合工厂测试 App：每个设备独立连接、独立发送、独立等待响应、独立显示结果。

现有固件中存在 group 相关方案，可用于设备间群组数据或广播类效果，但它需要额外的群组创建、角色配置、同步状态和异常恢复逻辑。第一版不把 group 作为主路径，避免增加产测不确定性。

如果后续发现逐设备下发速度不能满足工厂节拍，再增加 group 模式作为可选优化。App 层保留 `CommandDispatcher` 抽象，默认实现为 `PerDeviceBleDispatcher`，后续可增加 `GroupDispatcher`。

## 错误处理

每个设备的测试动作互不阻塞。单个设备失败不应中断整个批次，除非操作员主动点击停止。

需要处理的错误：

- BLE 扫描权限被拒绝。
- 手机蓝牙关闭。
- 设备扫描后在连接前消失。
- 找不到目标 GATT service 或 characteristic。
- 订阅 notify 失败。
- 查询设备信息握手超时。
- AES 或 CRC 解析失败。
- 绑定失败。
- DP 指令失败或超时。
- 解绑失败或超时。

界面需要为每个设备显示简短错误信息。完整错误字符串后续可以支持复制或导出，便于工厂定位问题。

## GitHub Actions 编译

新仓库必须包含 GitHub Actions workflow，由 GitHub 负责 APK 编译。

workflow 行为：

- push 触发。
- manual dispatch 触发。
- 使用 `ubuntu-latest`。
- 安装 JDK 17。
- 启用 Gradle cache。
- 执行 `./gradlew test assembleDebug`。
- 上传 debug APK artifact。

第一版使用 debug 签名即可满足工厂测试安装。如果后续需要正式签名，再通过 GitHub Secrets 配置 keystore。

## 测试策略

单元测试：

- CRC16 固定向量。
- AES-CBC 加解密，使用与固件兼容的测试向量。
- `le_msg` 帧编码和解析。
- DP JSON 生成。
- 各产品 LED 数量对应的 groove 字符串生成。

人工/真机测试：

- Android 12 及以上权限流程。
- RSSI 阈值过滤。
- 单设备连接、绑定、设置 PID、下发颜色、解绑。
- 至少 3 台设备的多设备排队连接。
- 测试中途关闭设备，验证超时和失败状态。

## 待确认项

EMC 模板的精确序列需要工厂确认。在未确认前，第一版只内置红、绿、蓝、白、黑和自定义最高功率颜色。

第一版 APK 使用 debug 签名。如果工厂分发要求 release 签名，再增加 GitHub Secrets 签名流程。

## 不在本期范围

本 App 不实现普通用户配网、Wi-Fi 配置、OTA、传图、云账号或正式消费端 App 功能。

本 App 不修改 T23 固件或 ESP32 固件。只有在联调发现现有协议无法满足需求时，才另行评估固件改动。
