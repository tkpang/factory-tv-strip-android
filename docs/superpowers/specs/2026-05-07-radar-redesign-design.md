# 工厂测试 App 重设计 · 雷达交互 + 中文化 + 广播过滤

**日期**：2026-05-07
**状态**：设计稿（待用户最终审核）
**前置文档**：`2026-04-28-android-factory-app-design.md`（旧版批量列表）

---

## 1. 背景与动机

当前 `factory-tv-strip-android` 主界面 `FactoryScreen.kt` 把扫描、连接、设灯、解绑全部堆在一个 530 行的 `LazyColumn` 里，工厂工人（仅识中文，学历低）使用时存在以下痛点：

1. **页面信息过载**：所有功能堆在一页，看不出操作顺序
2. **RSSI 数字晦涩**：用户看不懂 `-65 dBm` 是远是近
3. **流程心智不匹配**：用户实际是「把设备一台一台靠近手机」，而不是一次扫一堆勾选
4. **PID 概念混乱**：PID 选择被表达为「写入 PID」（实际是测试主功能不需要的辅助），用户误以为是「扫描过滤 PID」
5. **英文残留**：界面 / 反馈 / 错误提示混着英文（`Ready` / `state` / `Last`）
6. **`solidColorGroove` 必填的 `ledCount` 是死参数**：`require(ledCount in 1..255)` 校验后并未写入命令，是历史遗留

本次重设计的目标：**让仅识中文的工厂工人能在不培训的情况下，按「靠近 → 自动配 → 一键测 → 一键解绑」完成测试**。

---

## 2. 用户场景

### 2.1 主流程：测试设备

> 工人甲拿到一批 STV1 线下 5 米灯带（PID=144），需要批量做颜色测试 + 解绑：
>
> 1. 启动 App → 选「测试设备」
> 2. Step 1：勾「STV1 + 线下 5 米」+ 默认「只看未绑定」
> 3. Step 2：依次把 N 台灯带凑到手机附近，每台进入雷达蓝圈后自动配对，计数器跳动 + 震动反馈
> 4. 全部配完后点「下一步」
> 5. Step 3：依次点「红 / 绿 / 蓝 / 白 / 黑 / 关灯」按钮，眼看每台灯带是否变色正常
> 6. Step 3 「最高亮度」Tab：点「触发最大功率」按钮做 EMC 测试
> 7. Step 4：点「全部解绑并删除」红按钮，二次确认后批量解绑
> 8. 完成 → 自动重置回 Step 1，继续下一批

### 2.2 辅助流程：写 PID（偶尔补救用）

> 出厂前通常已在产线写好 PID。极少数情况下需要现场补写：
>
> 1. 启动 App → 选「写 PID」
> 2. 选目标 PID（如「线上 5 米」）
> 3. 把 1 台设备靠近手机，自动扫描并连接 RSSI 最强的 1 台
> 4. 写入 PID + 校验，成功后显示绿勾
> 5. 点「再写一台」继续

---

## 3. 关键技术发现

### 3.1 BLE 广播包的 manufacturer-specific data 含 PID

ESP32 固件 `le_ble.c:359-376` 在广播 manufacturer data 中放入 `factory_data`：

| 字节 | 长度 | 内容 |
|---|---|---|
| 0-1 | 2 | `'L' 'P'`（魔术头）|
| 2 | 1 | 绑定状态：`0x20` 未绑 / `0xA0` 已绑 |
| 3 | 1 | 广播版本 |
| 4 | 1 | 加密类型 |
| 5-10 | 6 | BT MAC |
| 11-14 | 4 | **PID（uint32）** |

**结论**：扫描时不需要连接就能拿到 PID 和绑定状态。当前 `BleScanner.kt:111-128` 仅看 service UUID + advertising name，未利用这块数据，是本次改造的核心扩展点。

### 3.2 颜色 / 最大功率指令不依赖 LED 个数

TV 灯带固件 `app_fact_test_tv_strip.c:84-139` 中产测指令全部硬编码：

```
N01:P10001ff0000;             // 全红
N01:P1000100ff00;             // 全绿
N01:P100010000ff;             // 全蓝
N01:P10001ffffff;             // 全白
N01:P10001000000;             // 全黑（即关灯效果）
N01:B21001200E5018003E80064;  // 最大功率
```

`P10001<rgb>` 是「整条灯带都设这个 RGB」的指令，固件自适应实际灯珠数。**因此颜色测试与 PID / LED 数完全解耦**，无论选何种 PID 筛选条件，颜色测试都能直接下发。

---

## 4. 整体架构

### 4.1 应用入口

```
LaunchActivity (NavHost)
  ├── route "launch"   → LaunchScreen        模式选择（测试 / 写 PID）
  ├── route "test"     → FactoryWizardScreen 4 步测试主流程
  └── route "writepid" → WritePidScreen      单台 PID 写入工具
```

启动页两个大卡片：
- **测试设备**（蓝色高亮，主功能）
- **写 PID（工厂出厂用）**（黄色辅助色，附「⚠ 仅供出厂线使用」副标）

### 4.2 测试主流程：4 步向导

```
FactoryWizardScreen
  ├── 顶部：4 段进度条（已完成绿、当前蓝、未来灰）+ 步骤名
  ├── 中部：Step 容器（按 currentStep 切换）
  │     ├── Step1ProductScreen   选产品类型 + PID 筛选 + 只看未绑定
  │     ├── Step2ScanScreen      脉冲雷达自动配对
  │     ├── Step3ColorScreen     颜色测试（普通 / 最高亮度 tab）
  │     └── Step4UnbindScreen    一键解绑
  └── 底部：上一步 / 下一步固定导航条
```

允许点击已完成的进度段回退；不能跳到未来步。返回键在 Step 2-4 时弹「会断开所有连接，确定退出？」二次确认。

### 4.3 文件结构

```
app/src/main/java/com/tkpang/tvstriptest/
├── MainActivity.kt                          NavHost 入口
├── ui/
│   ├── launch/LaunchScreen.kt               启动页
│   ├── wizard/
│   │   ├── FactoryWizardScreen.kt           Wizard 容器（≈80 行）
│   │   ├── components/
│   │   │   ├── StepIndicator.kt             进度条
│   │   │   └── BottomNavBar.kt              底部导航
│   │   ├── step1/Step1ProductScreen.kt      ≈120 行
│   │   ├── step2/
│   │   │   ├── Step2ScanScreen.kt           ≈100 行
│   │   │   ├── PulseRadar.kt                雷达 Composable（≈150 行）
│   │   │   └── SensitivitySheet.kt          底部 sheet
│   │   ├── step3/Step3ColorScreen.kt        ≈100 行
│   │   └── step4/Step4UnbindScreen.kt       ≈80 行
│   └── writepid/WritePidScreen.kt           写 PID 工具（≈120 行）
├── factory/
│   ├── FactoryViewModel.kt                  Wizard step 切换 + 全局状态
│   ├── ScanFilterUseCase.kt                 [新] PID + 未绑定过滤
│   ├── RadarStateMachine.kt                 [新] 设备配对状态机
│   ├── PairingExecutor.kt                   [新] 顺序排队连接 / 读 PID / 验证 / bond
│   ├── ColorTestUseCase.kt                  [新] 6 色 + 最大功率
│   ├── UnbindUseCase.kt                     [新] 批量解绑
│   ├── WritePidViewModel.kt                 [新] 写 PID 工具状态
│   └── CommandDispatcher.kt                 简化（不再依赖 LED 数）
├── ble/
│   ├── BleScanner.kt                        增加 manufacturer data 解析 + 过滤参数
│   └── BleDeviceSession.kt                  几乎不改
├── protocol/
│   ├── AdvDataParser.kt                     [新] 解析 manufacturer data byte[]
│   ├── DpCommands.kt                        简化（去掉 ledCount 参数）
│   ├── LeMessageCodec.kt                    不改
│   └── ...
└── model/
    ├── ProductCatalog.kt                    PID displayName 全改中文
    ├── FactoryModels.kt                     ScanDevice 扩展字段
    └── ...
```

### 4.4 数据流

```
BleScanner (增加 mfg parsing) ─stream─▶  ScanDevice (含 pid, isBonded)
                                              │
ScanFilterUseCase (按 step1 配置过滤) ◀──────┘
                                              │
                                              ▼
RadarStateMachine (维护每台设备 PairingState + 1.5s 计时器)
                                              │
                                              ▼
PairingExecutor (顺序队列：connect → readDevInfo → 验证 → bond)
                                              │
                                              ▼
FactoryViewModel.uiState (StateFlow<FactoryUiState>)
                                              │
                                              ▼
                                         UI Composables
```

---

## 5. 详细设计

### 5.1 启动页 LaunchScreen

| 元素 | 内容 |
|---|---|
| 主标题 | `工厂测试 App  v<version>` |
| 副标题 | `请选择要进入的功能` |
| 测试卡片 | 图标 📡（蓝底）+ `测试设备` + `主功能 tag` + 副标 `扫描 → 配对 → 颜色测试 → 解绑` |
| 写 PID 卡片 | 图标 ✏️（黄底）+ `写 PID（工厂出厂用）` + 副标 `单台扫描连接 → 写入指定 PID` |

### 5.2 Step 1 · 选测试条件

| 元素 | 内容 |
|---|---|
| 标题 | `选择测试条件` / 副 `只扫描符合条件的设备` |
| 产品类型 | 2×2 网格，4 张卡片（仅 STV1 可选，其他显示`暂未支持`灰态）|
| PID 筛选 | Radio 列表，顶部多一项 `不筛选 PID（任意）` |
| PID 行内容 | radio + 迷你灯带图标（线上 🌐 / 线下 🔌、长度 2/3/5 米示意、高密带颗粒纹理）+ 中文名 + PID 数字 |
| PID 列表 | 不筛选 / 线上 2 米 (158) / 线上 3 米 (111) / 线上 5 米 (112) / 线下 3 米 (143) / 线下 5 米 (144) / 线下 3 米 高密 (145) |
| 其他条件 | 开关「只显示未绑定的设备」+ 副 `过滤掉已被别人配过的灯带`，默认开 |
| 下一步 | 选了 PID（含「不筛选」也算选了）后高亮可点 |

### 5.3 Step 2 · 脉冲雷达扫描配对

#### 5.3.1 视觉元素

- **脉冲背景**：220×220dp 圆形区域，3 圈蓝色脉冲依次扩散（每圈 0.8s 错开，2.4s 周期）
- **中心点**：56×56dp 蓝色圆，含 📱 图标
- **自动配对中心区**：虚线浅蓝圆，半径由灵敏度档位决定
- **设备 chip**：白底圆角胶囊，含状态色点 + 设备名（MAC 后 4 位）
- **顶部计数器**：`已配 N` chip，配对成功时跳动放大动画
- **右上角 ⚙**：点击弹底部 sheet
- **底部提示文字**：动态根据状态变化

#### 5.3.2 设备坐标映射

- **径向距离**：`distance = clamp((-30 - rssi) / (-30 - -90), 0, 1) * radius`
  - rssi -30 dBm（极近）→ 中心
  - rssi -90 dBm（极远）→ 边缘
- **角度**：每台设备按 MAC 哈希分配固定角度（避免位置乱跳）
- **平滑动画**：RSSI 变化时 chip 用 200ms ease 动画移动到新位置

#### 5.3.3 配对状态机

```
广播接收
   │
   ▼
DETECTED         (白 chip，正常)
   │ 进入中心圈
   ▼
PREPARING        (绿描边 chip，脉冲动画，"准备配对")
   │ 1.5s 持续在圈内
   │
   ├─ 队列空 ──────────────────────┐
   │                              ▼
   │                          CONNECTING (灰 chip "连接中")
   │                              │
   │                          ┌───┴───┐
   │                          ▼       ▼
   │                       连接OK   连接失败
   │                          │       │
   │                       读PID       ▼
   │                          │   FAILED (红 chip "连接失败" 2s)
   │                          ▼       │
   │                     ┌────┴─┐     ▼ 回退 DETECTED 允许重试
   │                     ▼      ▼   ┌── 失败 3 次 → 永久跳过
   │                  PID符合 PID不符│   从雷达移除
   │                     │      │
   │                     ▼      ▼
   │                   bond  PID_MISMATCH (红 "PID 不符 · 跳过")
   │                     │      │ 2s
   │                  ┌──┴──┐   ▼
   │                  ▼     ▼   从雷达移除（永不再现）
   │              bond OK bond失败
   │                  │     │
   │                  ▼     ▼
   │              PAIRED  FAILED (回 DETECTED)
   │                  │
   │                  │ 飞向计数器动画 + 计数 +1 + 30ms 震动
   │                  ▼
   │              从雷达移除（已加入测试队列）
   │
   └─ 队列非空 ─▶ QUEUED (灰 chip "队列中")
                    │ 等队列空
                    ▼ 转 CONNECTING
```

#### 5.3.4 灵敏度抽屉（点 ⚙）

- 标题：`配对灵敏度` / 副 `越严格 = 设备要凑得越近才会自动配上`
- 5 档离散滑块：

  | 档位 | RSSI 阈值 | 中心区半径占雷达比 |
  |---|---|---|
  | 紧贴 | -45 dBm | 18% |
  | 很近 | -55 dBm | 24% |
  | 近 | -65 dBm | 33% |
  | 中 | -75 dBm | 45% |
  | 较远 | -85 dBm | 60% |

- 默认档：「近」（-65 dBm，与现有 `BleScanner.DEFAULT_RSSI_THRESHOLD` 一致）
- 改档时雷达虚线圆实时缩放
- 「完成」关闭 sheet 即生效，不打断已 in-flight 的连接

#### 5.3.5 错误状态

| 触发 | UI |
|---|---|
| 蓝牙关闭 | 雷达置灰 + 顶部红条「📵 蓝牙未打开」+ 红色「打开蓝牙」按钮（深链系统设置）|
| 缺权限 | 顶部红条「需要蓝牙权限」+ 「去授权」按钮 |
| 5 秒未扫到任何设备 | 雷达内显示 🔍 + 「还没扫到设备 · 确认设备已通电、距离 ≤ 5 米」|
| 10 秒未扫到 | 上述提示 + 「重新扫描」按钮 |

#### 5.3.6 「全部配完」按钮

- 已配 0 台 → disabled 灰
- 已配 ≥1 台 → 蓝色可点
- 点击直接进 Step 3，不需要二次确认（可回退）

### 5.4 Step 3 · 颜色测试

#### 5.4.1 布局

```
顶部状态条：已配 N 台 · 全部 Ready ✓
Tab 切换：[ 普通设灯 ] [ 最高亮度 ]
─────────────────────────
普通设灯 Tab：
  6 个大圆按钮（2×3 网格）
  ┌────┬────┬────┐
  │ 红 │ 绿 │ 蓝 │
  ├────┼────┼────┤
  │ 白 │ 黑 │ 关 │
  └────┴────┴────┘
  反馈条：✓ 已下发：所有 N 台 → 红色 / 失败 X 台
─────────────────────────
最高亮度 Tab：
  大按钮：[ 触发最大功率 ]
  说明：用于 EMC 最大功耗测试
  反馈条同上
```

#### 5.4.2 命令映射

| 按钮 | 下发指令 |
|---|---|
| 红 | `grooveState(true)` + `setMaxBrightness(1000)` + `grooveHandle("N01:P10001ff0000;")` |
| 绿 | `grooveState(true)` + `setMaxBrightness(1000)` + `grooveHandle("N01:P1000100ff00;")` |
| 蓝 | `grooveState(true)` + `setMaxBrightness(1000)` + `grooveHandle("N01:P100010000ff;")` |
| 白 | `grooveState(true)` + `setMaxBrightness(1000)` + `grooveHandle("N01:P10001ffffff;")` |
| 黑 | `grooveState(true)` + `grooveHandle("N01:P10001000000;")` |
| 关 | `grooveState(false)` |
| 触发最大功率 | `grooveState(true)` + `grooveHandle("N01:B21001200E5018003E80064;")` |

> 每个有色按钮（红/绿/蓝/白）都先发 `setMaxBrightness(1000)`，确保亮度被拉满（避免历史亮度残留导致显示偏暗）。固件 `app_fact_test_tv_strip.c:test_3_show_red` 即此做法，其余颜色因紧接红色后执行靠惯性沿用 1000 亮度，APK 端为求每个按钮独立可点不依赖前序状态，统一显式下发。

#### 5.4.3 并发与反馈

- 点按钮后并发对所有已连设备下发（沿用 `CommandDispatcher` 现有机制，**颜色/解绑阶段并发限 3**；与 Step 2 配对阶段的「单线连接队列上限 1」是两个独立的并发策略）
- 反馈条实时更新「成功 N 台 / 失败 M 台」
- 点失败计数 → 弹设备明细 dialog（地址 + 错误原因）

### 5.5 Step 4 · 解绑

#### 5.5.1 静止态

- 大 🗑 图标
- 文案：`测试完成，把这 N 台设备从 App 上一键解绑删除`
- 大红按钮：`全部解绑并删除`
- 副提示：`解绑后设备会回到出厂未配状态，下次扫描可以重新配`

#### 5.5.2 点击 → 二次确认 dialog

> 标题：确认解绑？
> 内容：将对 N 台设备发送解绑命令，无法撤销。
> 按钮：[取消] [确认解绑]

#### 5.5.3 进行中

- 大按钮变进度条：`已解绑 X/N`
- 进度条下显示当前正在解绑哪台

#### 5.5.4 完成

- 按钮变绿勾「✓ 全部解绑成功」+ 「完成 · 重新开始测试」
- 点完成 → ViewModel 重置 → 回 Step 1
- 若有失败：失败设备列表 + 「重试失败的」按钮

### 5.6 写 PID 工具（独立流程）

```
WritePidScreen 单页面：
├── 顶部黄色警示条「⚠ 仅供工厂出厂线使用 · 一次只配一台」
├── PID 单选列表（同 Step 1 PID 项，无「不筛选」）
├── 写入流程说明（3 步）
└── 底部「开始写入」按钮

点击开始 →
  ├── 进入「扫描中」状态（5 秒超时）
  ├── 拿到 RSSI 最强的 1 台 → 自动连接
  ├── 连接成功 → 下发 setPid(d158=PID)
  ├── 重读 DEV_INFO 校验 PID 已变为目标
  └── 成功 → 大绿勾「✓ PID 已写入：线上 5 米 (112)」
            按钮变 [再写一台] [返回首页]

错误处理：
  ├── 5 秒未扫到 → 红提示「请把 1 台设备靠近后重试」
  ├── 连接失败 → 「重试」/「取消」
  ├── 写入失败 → 显示错误码 + 「重试」
  └── 校验失败（写入了但读出来不对） → 「重试」
```

---

## 6. 数据模型

### 6.1 `ScanDevice`（扩展）

```kotlin
data class ScanDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    // 新增字段
    val pid: Int?,                       // null = 解析失败 / 旧固件
    val isBonded: Boolean,               // 0xA0 已绑 / 0x20 未绑（默认 false）
    val pairingState: PairingState,
)

enum class PairingState {
    DETECTED,
    PREPARING,
    QUEUED,
    CONNECTING,
    PID_MISMATCH,
    FAILED,
    PAIRED,
}
```

### 6.2 `FactorySettings`（精简）

```kotlin
data class FactorySettings(
    val productDevName: String,           // "STV1"
    val pidFilter: PidFilter,             // [新] sealed: ANY | Specific(Int)
    val onlyUnbonded: Boolean = true,     // [新]
    val sensitivity: SensitivityLevel,    // [新] 5 档枚举（取代裸 Int）
    // 删除字段：pid: Int        — 测试主流程不再写 PID
    //          targetCount: Int — 不再用目标数量
    //          maxPowerColor   — 最大功率不需选色
    //          maxBrightness   — 同上
)

sealed interface PidFilter {
    data object Any : PidFilter
    data class Specific(val pid: Int) : PidFilter
}

enum class SensitivityLevel(val rssiThreshold: Int) {
    VERY_CLOSE(-45), CLOSE(-55), NEAR(-65), MEDIUM(-75), FAR(-85);
}
```

### 6.3 `WritePidSettings`（新）

```kotlin
data class WritePidSettings(
    val targetPid: Int,    // 必填，无「不筛选」
)
```

---

## 7. 模块边界与单一职责

### 7.1 `AdvDataParser`（新，纯函数）

```kotlin
object AdvDataParser {
    data class Parsed(val pid: Int, val isBonded: Boolean)
    fun parse(mfg: ByteArray): Parsed?  // null = 不是 LP 设备 / 长度不足
}
```

### 7.2 `BleScanner`（改）

- 新增 `start(filter: ScanFilterCriteria)` 参数
- 新增 manufacturer data 解析逻辑
- 删除 `manualSelections` / `targetCount` / `autoSelected` 等字段（不再支持手动勾选）

### 7.3 `ScanFilterUseCase`（新）

输入：原始 `Flow<List<ScanDevice>>` + `FactorySettings.pidFilter` + `onlyUnbonded`
输出：过滤后的 `Flow<List<ScanDevice>>`
单测：覆盖 4 种组合（PID Any/Specific × Bonded true/false）的全笛卡尔积。

### 7.4 `RadarStateMachine`（新）

输入：过滤后的设备 flow + 灵敏度阈值
输出：每台设备的 `PairingState` flow + 「准备配对」事件触发
单测：所有状态迁移、1.5s 稳定计时、设备退出中心区取消计时。

### 7.5 `PairingExecutor`（新）

- 单线连接队列（in-flight 上限 1）
- 流程：connect → readDevInfo → 验证 PID → bond
- 失败重试（连接失败重试 3 次）
- 单测覆盖：队列顺序、PID 不符路径、bond 失败路径。

### 7.6 `ColorTestUseCase`（新）

```kotlin
class ColorTestUseCase(private val dispatcher: CommandDispatcher) {
    suspend fun setColor(devices: List<FactoryDevice>, command: ColorCommand): Result
    enum class ColorCommand { RED, GREEN, BLUE, WHITE, BLACK, OFF, MAX_POWER }
}
```

不依赖 PID / LED 数。

### 7.7 `UnbindUseCase`（新）

并发对 `devices` 下发 `LE_CMD_UNBOND`，进度通过 `Flow<UnbindProgress>` 暴露。

### 7.8 `DpCommands`（简化）

```kotlin
object DpCommands {
    fun setPid(pid: Int): String = "{\"d158\":$pid}"
    fun setMaxBrightness(value: Int): String = "{\"d162\":${value.coerceIn(0,1000)}}"
    fun grooveState(enabled: Boolean): String = "{\"d161\":${if (enabled) 1 else 0}}"
    fun grooveHandle(groove: String): String = "{\"d160\":\"${escapeJson(groove)}\"}"

    fun solidColorGroove(rgb: Int): String =        // 删除 ledCount 参数
        "N01:P10001${rgbHex(rgb)};"

    const val MAX_POWER_COMMAND = "N01:B21001200E5018003E80064;"
}
```

### 7.9 `ProductCatalog`（中文化）

```kotlin
PidOption(111, "线上 3 米", null)
PidOption(112, "线上 5 米", null)
PidOption(143, "线下 3 米", null)
PidOption(144, "线下 5 米", null)
PidOption(145, "线下 3 米 高密", null)
PidOption(158, "线上 2 米", null)
```

`ledCount` 字段保留但全设为 null（不再使用）。`requireLedCount()` 函数删除。

---

## 8. 错误处理总表

| 场景 | 处理 |
|---|---|
| 蓝牙关闭 | 雷达置灰，红条 + 「打开蓝牙」按钮深链 |
| 缺 BLE 权限 | 红条 + 「去授权」按钮 |
| 广播 mfg data 解析失败（旧固件 / 第三方设备）| 设备 chip 显示 `?` PID；筛选 PID 时被过滤；不筛选时仍可被试连 |
| 连接超时（默认 10s）| 标记 FAILED，2s 后回 DETECTED 允许重试 |
| 同一台连接失败 ≥3 次 | 永久跳过，从雷达移除 |
| Bond 失败 | 标记 FAILED，回 DETECTED |
| PID 不匹配 | 标记 PID_MISMATCH，2s 后从雷达永久移除 |
| 颜色测试单台失败 | 反馈条「失败 M 台」，点开看明细 |
| 解绑单台失败 | 进度条结束后列出失败设备，「重试失败的」按钮 |
| 写 PID 校验失败 | 显示错误码 + 「重试」/「取消」|

---

## 9. 测试策略

### 9.1 单元测试（无设备依赖）

| 模块 | 测试点 |
|---|---|
| `AdvDataParser` | 正常 / 长度不足 / 头不是 LP / 各种 PID / 0x20 vs 0xA0 vs 其他绑定字节 |
| `ScanFilterUseCase` | PID Any/Specific × onlyUnbonded true/false 的笛卡尔积 |
| `RadarStateMachine` | 状态迁移图全覆盖、1.5s 计时、退圈取消计时、多设备并存 |
| `PairingExecutor` | 队列顺序、并发限 1、连接失败 3 次重试、PID 不符跳过、bond 失败回退 |
| `ColorTestUseCase` | 6 个 ColorCommand 各自下发的指令字串 |
| `UnbindUseCase` | 部分成功 / 全部失败 / 全部成功 |
| `DpCommands` | 简化后 `solidColorGroove` 输出格式 |
| `LeMessageCodec` / `BleCrypto` | 沿用现有测试 |

### 9.2 集成测试（mock BLE）

- `BleScanner` 喂 fake `ScanResult`（含 mfg data），验证 ScanDevice 流字段正确
- `CommandDispatcher`（沿用现有架构）扩展覆盖 6 色 + 最大功率 + 写 PID 命令序列

### 9.3 CI

- `./gradlew test assembleDebug`（已存在）
- 新增模块自动纳入测试

### 9.4 现场验证（人工，无 instrumented test）

- 5 台 STV1 灯带（线下 5m）
- 1 台 STV1 线下 3m（验证 PID 不符跳过）
- 蓝牙关闭场景
- 缺权限场景
- 灵敏度 5 档手测

---

## 10. 现有代码迁移影响

| 现有文件 | 处置 |
|---|---|
| `FactoryScreen.kt` (530 行) | 删除，拆成 `FactoryWizardScreen` + 4 个 step screen |
| `FactoryViewModel.kt` (255 行) | 大改：拆出 ScanFilterUseCase / RadarStateMachine / PairingExecutor，VM 只负责 step 切换 |
| `BleScanner.kt` (164 行) | 增加 mfg data 解析 + 过滤参数；删除 manualSelection 相关字段 |
| `BleDeviceSession.kt` | 几乎不改 |
| `ProductCatalog.kt` | `displayName` 全改中文，`ledCount` 全设 null，删除 `requireLedCount()` |
| `CommandDispatcher.kt` | `setColor` / `setHighestPowerColor` 不再依赖 PID 与 LED 数 |
| `DpCommands.kt` | `solidColorGroove` 去掉 ledCount 参数；新增 `MAX_POWER_COMMAND` 常量 |
| `MainActivity.kt` (161 行) | 改成 NavHost，三个 route：launch / test / writepid |
| `FactoryModels.kt` | `ScanDevice` 增加 pid / isBonded / pairingState；`FactoryDevice` 几乎不改；`FactorySettings` 重新精简 |
| `AndroidManifest.xml` | 不变（已有 BLE 权限） |
| `README.md` | 改写：新流程说明、广播过滤原理、灵敏度档位含义 |

---

## 11. 范围与非范围

### 在本 spec 范围内

- 启动页 + 4 步测试 wizard + 写 PID 工具页
- 雷达脉冲 UI + 灵敏度抽屉
- 广播 manufacturer data 解析 + PID/未绑定过滤
- 颜色测试简化（去 LED 数依赖）+ 加关灯按钮 + 最高亮度独立按钮
- 全 app 中文化
- 单元/集成测试
- README 重写

### 不在本 spec 范围内

- S2 / SW1 / S1_V2 产品类型实现（仍灰态）
- MQTT / 云端 / 账号系统
- OTA 升级
- 持久化用户偏好（启动恢复 step1 选择）
- Instrumented BLE 测试（依赖真机）
- 多语言切换（仅中文）

---

## 12. 风险与开放问题

| 风险 | 缓解 |
|---|---|
| 旧固件没广播 manufacturer data | 解析失败时 PID=null；筛选 PID 时被过滤；不筛选时仍可试连 |
| RSSI 抖动导致雷达点位置乱跳 | 角度按 MAC 哈希固定；径向 200ms ease 平滑动画 |
| 多台设备同时进入中心区 | 单线队列处理，按 RSSI 强度排队 |
| 用户改灵敏度时正在配对 | 不打断 in-flight 连接，只影响后续 |
| 设备靠近又拿走（未配上）| 退出中心区即取消 1.5s 计时器，回 DETECTED |
| 工人误进「写 PID」模式 | 启动页明确警示 + 黄色辅助色，文案强调「工厂出厂用」 |

### 开放问题（待实施时确认）

1. 拨盘默认档（当前定「近 -65 dBm」）是否合理？现场需验证
2. 「写 PID」工具是否需要密码 / 二次确认避免误用？
3. 是否需要在 Step 3 反馈条记录历史「红 → 绿 → 蓝 …」操作日志？

---

## 13. 验收标准

设计完成后由用户审核本 spec。批准后通过 `superpowers:writing-plans` skill 转入实施计划阶段。

**Definition of Done（实施阶段）**：

1. 启动 App → 选「测试设备」→ 4 步全跑通 → 完成回 Step1
2. 选「线下 5 米 (144)」+ 凑近 1 台 3 米灯带 → 看到「PID 不符 · 跳过」
3. 选「不筛选」+ 凑近 5 台不同 PID 灯带 → 全部能配上
4. 关蓝牙 → 红条提示 + 「打开蓝牙」按钮工作
5. 灵敏度调到「紧贴」→ 中心圈最小，必须贴脸才配上
6. 颜色测试 6 个按钮 + 最高亮度按钮全部下发成功
7. 解绑后所有设备回到未绑定状态（重新扫描可见）
8. 「写 PID」模式：选「线上 5m」→ 凑近 1 台 → 写入成功 → DEV_INFO 读出来 PID=112
9. 全 app 无英文残留（README 不算）
10. 单元测试覆盖 ≥80% 新模块代码
11. `./gradlew test assembleDebug` CI 通过

---

## 附录 · Mockup 索引

完整可点击的 mockup 在 `docs/mockups/`：

- `navigation.html` — 导航结构对比（A 全屏向导胜出）
- `radar-style.html` — 雷达风格对比（A 脉冲胜出）
- `steps.html` — Step 1/3/4 早期版本（已被 step1-revised 替代）
- `step1-revised.html` — 启动页 + Step 1 改造版 + 写 PID 入口
- `radar-page.html` — Step 2 雷达页 6 种状态详图
