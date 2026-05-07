# 雷达重设计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把当前 530 行单页面的 factory-tv-strip-android 重构成「启动页二选一 + 4 步向导 + 写 PID 工具」的结构，雷达页用脉冲风 UI、广播 manufacturer data 解析做 PID + 未绑定过滤、颜色测试去掉 LED 数依赖、全 app 中文化。

**Architecture:** Compose UI + Kotlin coroutines + StateFlow。NavHost 三个 route：launch / test / writepid。测试主流程是 4 步 stateful wizard（FactoryViewModel 持有 currentStep）。`AdvDataParser` 解析广播 byte[] → PID + 绑定状态；`ScanFilterUseCase` 过滤；`RadarStateMachine` 管设备 PairingState + 1.5s 计时；`PairingExecutor` 单线连接队列。颜色/解绑沿用现有 `CommandDispatcher`（并发 3）。

**Tech Stack:** Kotlin, Jetpack Compose, Material3, kotlinx-coroutines, JUnit 4, Android BLE。Min SDK 26、Target 35、Compile 35。`./gradlew test assembleDebug` 在 GitHub Actions 跑。

**Spec reference:** `docs/superpowers/specs/2026-05-07-radar-redesign-design.md`
**Mockups:** `docs/mockups/{navigation,radar-style,step1-revised,radar-page}.html`

---

## File Map

**Create（新增文件）：**

```
app/src/main/java/com/tkpang/tvstriptest/
  protocol/AdvDataParser.kt
  model/PidFilter.kt
  model/SensitivityLevel.kt
  model/PairingState.kt
  ui/launch/LaunchScreen.kt
  ui/wizard/FactoryWizardScreen.kt
  ui/wizard/components/StepIndicator.kt
  ui/wizard/components/BottomNavBar.kt
  ui/wizard/step1/Step1ProductScreen.kt
  ui/wizard/step2/Step2ScanScreen.kt
  ui/wizard/step2/PulseRadar.kt
  ui/wizard/step2/SensitivitySheet.kt
  ui/wizard/step3/Step3ColorScreen.kt
  ui/wizard/step4/Step4UnbindScreen.kt
  ui/writepid/WritePidScreen.kt
  factory/ScanFilterUseCase.kt
  factory/RadarStateMachine.kt
  factory/PairingExecutor.kt
  factory/ColorTestUseCase.kt
  factory/UnbindUseCase.kt
  factory/WritePidViewModel.kt

app/src/test/java/com/tkpang/tvstriptest/
  protocol/AdvDataParserTest.kt
  factory/ScanFilterUseCaseTest.kt
  factory/RadarStateMachineTest.kt
  factory/PairingExecutorTest.kt
  factory/ColorTestUseCaseTest.kt
  factory/UnbindUseCaseTest.kt
```

**Modify（重写或大改文件）：**

```
app/src/main/java/com/tkpang/tvstriptest/
  MainActivity.kt                 — 改成 NavHost
  ble/BleScanner.kt               — 加 manufacturer data 解析 + 过滤参数
  protocol/DpCommands.kt          — solidColorGroove 去 ledCount + 加 MAX_POWER_COMMAND
  factory/CommandDispatcher.kt    — setColor/setHighestPowerColor 不再依赖 LED 数
  factory/FactoryViewModel.kt     — 大改：只管 step 切换 + 全局状态聚合
  model/ProductCatalog.kt         — 中文化 + 删除 requireLedCount
  model/FactoryModels.kt          — ScanDevice 加字段、FactorySettings 精简
  ui/FactoryScreen.kt             — 删除（拆到各 step）
README.md                         — 重写
app/src/test/java/com/tkpang/tvstriptest/
  protocol/DpCommandsTest.kt      — 改测新签名
  factory/CommandDispatcherTest.kt— 适配新 setColor 签名
  model/ProductCatalogTest.kt     — 适配中文 displayName + 移除 requireLedCount 测试
```

---

## Task Order

按依赖从底到顶：foundation → data flow → use cases → UI 组件 → 屏幕 → 接线 → 清理 → 文档。每个 task 完成后跑 `./gradlew test` 确认绿，并 commit。

---

## Task 1: AdvDataParser（广播包解析）

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/protocol/AdvDataParser.kt`
- Create: `app/src/test/java/com/tkpang/tvstriptest/protocol/AdvDataParserTest.kt`

**Spec ref:** §3.1 广播 manufacturer data 布局。

- [ ] **Step 1: Write the failing test**

```kotlin
// AdvDataParserTest.kt
package com.tkpang.tvstriptest.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdvDataParserTest {
    @Test
    fun parsesValidUnbondedAdv() {
        val bytes = buildAdv(
            magic = "LP",
            bondByte = 0x20.toByte(),
            pid = 144,
        )
        val parsed = AdvDataParser.parse(bytes)
        assertEquals(144, parsed?.pid)
        assertEquals(false, parsed?.isBonded)
    }

    @Test
    fun parsesValidBondedAdv() {
        val bytes = buildAdv(magic = "LP", bondByte = 0xA0.toByte(), pid = 111)
        val parsed = AdvDataParser.parse(bytes)
        assertEquals(111, parsed?.pid)
        assertEquals(true, parsed?.isBonded)
    }

    @Test
    fun returnsNullWhenLengthTooShort() {
        assertNull(AdvDataParser.parse(byteArrayOf(0x4C, 0x50, 0x20)))
    }

    @Test
    fun returnsNullWhenMagicHeaderWrong() {
        val bytes = buildAdv(magic = "XX", bondByte = 0x20, pid = 144)
        assertNull(AdvDataParser.parse(bytes))
    }

    @Test
    fun treatsUnknownBondByteAsUnbonded() {
        val bytes = buildAdv(magic = "LP", bondByte = 0x00.toByte(), pid = 144)
        val parsed = AdvDataParser.parse(bytes)
        assertEquals(false, parsed?.isBonded)
    }

    @Test
    fun parsesPidZero() {
        val bytes = buildAdv(magic = "LP", bondByte = 0x20, pid = 0)
        assertEquals(0, AdvDataParser.parse(bytes)?.pid)
    }

    @Test
    fun parsesAllStvPids() {
        listOf(111, 112, 143, 144, 145, 158).forEach { pid ->
            val bytes = buildAdv(magic = "LP", bondByte = 0x20, pid = pid)
            assertEquals(pid, AdvDataParser.parse(bytes)?.pid)
        }
    }

    private fun buildAdv(magic: String, bondByte: Byte, pid: Int): ByteArray {
        val data = ByteArray(15)
        data[0] = magic[0].code.toByte()
        data[1] = magic[1].code.toByte()
        data[2] = bondByte
        data[3] = 0x01  // version
        data[4] = 0x00  // encrypt
        // bytes 5..10 MAC (zero ok for tests)
        // bytes 11..14 PID little-endian
        data[11] = (pid and 0xFF).toByte()
        data[12] = ((pid ushr 8) and 0xFF).toByte()
        data[13] = ((pid ushr 16) and 0xFF).toByte()
        data[14] = ((pid ushr 24) and 0xFF).toByte()
        return data
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests AdvDataParserTest
```
Expected: FAIL with "Unresolved reference: AdvDataParser".

- [ ] **Step 3: Implement AdvDataParser**

```kotlin
// AdvDataParser.kt
package com.tkpang.tvstriptest.protocol

object AdvDataParser {
    private const val MIN_LEN = 15
    private const val MAGIC_L = 'L'.code.toByte()
    private const val MAGIC_P = 'P'.code.toByte()
    private const val BOND_BIT = 0x80.toByte()

    data class Parsed(val pid: Int, val isBonded: Boolean)

    fun parse(bytes: ByteArray): Parsed? {
        if (bytes.size < MIN_LEN) return null
        if (bytes[0] != MAGIC_L || bytes[1] != MAGIC_P) return null

        val isBonded = (bytes[2].toInt() and 0x80) != 0  // 0xA0 高位 1 = 已绑
        val pid = (bytes[11].toInt() and 0xFF) or
            ((bytes[12].toInt() and 0xFF) shl 8) or
            ((bytes[13].toInt() and 0xFF) shl 16) or
            ((bytes[14].toInt() and 0xFF) shl 24)
        return Parsed(pid = pid, isBonded = isBonded)
    }
}
```

> **Endianness 说明**：固件 `le_ble.c:375` 用 `memcpy(&factory_data[11], &pid_num, 4)`，ESP32 是小端，所以广播包里也是小端。Android 这边手动按小端拼回 Int。

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew test --tests AdvDataParserTest
```
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tkpang/tvstriptest/protocol/AdvDataParser.kt \
        app/src/test/java/com/tkpang/tvstriptest/protocol/AdvDataParserTest.kt
git commit -m "feat: add AdvDataParser for BLE manufacturer data"
```

---

## Task 2: 新增基础类型 PidFilter / SensitivityLevel / PairingState

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/model/PidFilter.kt`
- Create: `app/src/main/java/com/tkpang/tvstriptest/model/SensitivityLevel.kt`
- Create: `app/src/main/java/com/tkpang/tvstriptest/model/PairingState.kt`

**Spec ref:** §6.1, §6.2, §5.3.4。

- [ ] **Step 1: Create PidFilter.kt**

```kotlin
package com.tkpang.tvstriptest.model

sealed interface PidFilter {
    data object Any : PidFilter
    data class Specific(val pid: Int) : PidFilter
}
```

- [ ] **Step 2: Create SensitivityLevel.kt**

```kotlin
package com.tkpang.tvstriptest.model

enum class SensitivityLevel(
    val displayName: String,
    val rssiThreshold: Int,
    val zoneRadiusFraction: Float,
) {
    VERY_CLOSE("紧贴", -45, 0.18f),
    CLOSE     ("很近", -55, 0.24f),
    NEAR      ("近",   -65, 0.33f),
    MEDIUM    ("中",   -75, 0.45f),
    FAR       ("较远", -85, 0.60f);

    companion object {
        val DEFAULT = NEAR
    }
}
```

- [ ] **Step 3: Create PairingState.kt**

```kotlin
package com.tkpang.tvstriptest.model

enum class PairingState {
    DETECTED,        // 白 chip — 检测到，未进圈
    PREPARING,       // 绿描边 — 进圈，1.5s 计时中
    QUEUED,          // 灰 chip — 等待队列
    CONNECTING,      // 灰 chip — 正在连接
    PID_MISMATCH,    // 红 chip — PID 不符（永久跳过）
    FAILED,          // 红 chip — 连接/bond 失败（可重试）
    PAIRED,          // 已配对（飞向计数器后从列表移除）
}
```

- [ ] **Step 4: Verify build**

```bash
./gradlew compileDebugKotlin
```
Expected: SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tkpang/tvstriptest/model/PidFilter.kt \
        app/src/main/java/com/tkpang/tvstriptest/model/SensitivityLevel.kt \
        app/src/main/java/com/tkpang/tvstriptest/model/PairingState.kt
git commit -m "feat: add PidFilter, SensitivityLevel, PairingState types"
```

---

## Task 3: ProductCatalog 中文化 + 删除 requireLedCount

**Files:**
- Modify: `app/src/main/java/com/tkpang/tvstriptest/model/ProductCatalog.kt`
- Modify: `app/src/test/java/com/tkpang/tvstriptest/model/ProductCatalogTest.kt`

**Spec ref:** §7.9。

- [ ] **Step 1: Update ProductCatalogTest.kt**

```kotlin
package com.tkpang.tvstriptest.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProductCatalogTest {
    @Test
    fun stv1HasAllSixPidOptionsInChinese() {
        val stv1 = ProductCatalog.productTypes.first { it.devName == "STV1" }
        val map = stv1.pidOptions.associateBy { it.pid }
        assertEquals("线上 3 米", map[111]?.displayName)
        assertEquals("线上 5 米", map[112]?.displayName)
        assertEquals("线上 2 米", map[158]?.displayName)
        assertEquals("线下 3 米", map[143]?.displayName)
        assertEquals("线下 5 米", map[144]?.displayName)
        assertEquals("线下 3 米 高密", map[145]?.displayName)
    }

    @Test
    fun otherProductTypesArePresentButEmpty() {
        listOf("S2", "SW1", "S1_V2").forEach { devName ->
            val product = ProductCatalog.productTypes.firstOrNull { it.devName == devName }
            assertNotNull("$devName missing", product)
            assertEquals(emptyList<PidOption>(), product?.pidOptions)
        }
    }

    @Test
    fun stv1DisplayNameIsChinese() {
        val stv1 = ProductCatalog.productTypes.first { it.devName == "STV1" }
        assertEquals("STV1 摄像头灯带", stv1.displayName)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests ProductCatalogTest
```
Expected: FAIL on display name asserts.

- [ ] **Step 3: Update ProductCatalog.kt**

```kotlin
package com.tkpang.tvstriptest.model

data class PidOption(
    val pid: Int,
    val displayName: String,
    val ledCount: Int? = null,  // 保留字段但全 null，未来可能用
)

data class ProductType(
    val devName: String,
    val displayName: String,
    val pidOptions: List<PidOption>,
)

object ProductCatalog {
    val productTypes: List<ProductType> = listOf(
        ProductType(
            devName = "STV1",
            displayName = "STV1 摄像头灯带",
            pidOptions = listOf(
                PidOption(158, "线上 2 米"),
                PidOption(111, "线上 3 米"),
                PidOption(112, "线上 5 米"),
                PidOption(143, "线下 3 米"),
                PidOption(144, "线下 5 米"),
                PidOption(145, "线下 3 米 高密"),
            ),
        ),
        ProductType("S2",    "S2 RGBCW 灯带", emptyList()),
        ProductType("SW1",   "SW1 防水灯带",   emptyList()),
        ProductType("S1_V2", "S1-V2 灯带",     emptyList()),
    )

    // 删除 requireLedCount() — 颜色测试不再需要
}
```

- [ ] **Step 4: Verify build (现有 CommandDispatcher 调用 requireLedCount 会编译失败，先注释/暂保留兼容包装)**

加临时兼容方法到 `ProductCatalog`：

```kotlin
// 临时：让 CommandDispatcher 仍编译，下个 task 删
fun requireLedCount(devName: String, pid: Int): Int = 1
```

```bash
./gradlew compileDebugKotlin compileDebugUnitTestKotlin
./gradlew test --tests ProductCatalogTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tkpang/tvstriptest/model/ProductCatalog.kt \
        app/src/test/java/com/tkpang/tvstriptest/model/ProductCatalogTest.kt
git commit -m "feat: chinese-ize ProductCatalog PID display names"
```

---

## Task 4: DpCommands 简化（去 ledCount + MAX_POWER_COMMAND）

**Files:**
- Modify: `app/src/main/java/com/tkpang/tvstriptest/protocol/DpCommands.kt`
- Modify: `app/src/test/java/com/tkpang/tvstriptest/protocol/DpCommandsTest.kt`

**Spec ref:** §3.2, §7.8。

- [ ] **Step 1: Update DpCommandsTest.kt**

替换整文件内容：

```kotlin
package com.tkpang.tvstriptest.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DpCommandsTest {
    @Test
    fun setPidJson() {
        assertEquals("{\"d158\":111}", DpCommands.setPid(111))
    }

    @Test
    fun maxBrightnessJsonClampsToValidRange() {
        assertEquals("{\"d162\":1000}", DpCommands.setMaxBrightness(1000))
        assertEquals("{\"d162\":1000}", DpCommands.setMaxBrightness(1200))
        assertEquals("{\"d162\":0}",    DpCommands.setMaxBrightness(-1))
    }

    @Test
    fun grooveStateJson() {
        assertEquals("{\"d161\":1}", DpCommands.grooveState(true))
        assertEquals("{\"d161\":0}", DpCommands.grooveState(false))
    }

    @Test
    fun solidColorGrooveProducesP10001Format() {
        assertEquals("N01:P10001ff0000;", DpCommands.solidColorGroove(0xFF0000))
        assertEquals("N01:P1000100ff00;", DpCommands.solidColorGroove(0x00FF00))
        assertEquals("N01:P100010000ff;", DpCommands.solidColorGroove(0x0000FF))
        assertEquals("N01:P10001ffffff;", DpCommands.solidColorGroove(0xFFFFFF))
        assertEquals("N01:P10001000000;", DpCommands.solidColorGroove(0x000000))
    }

    @Test
    fun solidColorGrooveRejectsRgbOutsideRange() {
        assertThrows(IllegalArgumentException::class.java) {
            DpCommands.solidColorGroove(rgb = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DpCommands.solidColorGroove(rgb = 0x1000000)
        }
    }

    @Test
    fun grooveHandleWrapsGrooveInD160Json() {
        assertEquals(
            "{\"d160\":\"N01:P10001000000;\"}",
            DpCommands.grooveHandle("N01:P10001000000;"),
        )
    }

    @Test
    fun grooveHandleEscapesJsonStringCharacters() {
        assertEquals(
            "{\"d160\":\"\\\\\\\"\\n\\r\\t\\b\\f\\u0001\\u001f\"}",
            DpCommands.grooveHandle("\\\"\n\r\t\b"),
        )
    }

    @Test
    fun maxPowerCommandIsHardcodedFirmwareString() {
        assertEquals("N01:B21001200E5018003E80064;", DpCommands.MAX_POWER_COMMAND)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests DpCommandsTest
```
Expected: FAIL — old `solidColorGroove(ledCount, rgb)` signature mismatch and `MAX_POWER_COMMAND` not defined.

- [ ] **Step 3: Update DpCommands.kt**

```kotlin
package com.tkpang.tvstriptest.protocol

object DpCommands {
    const val MAX_POWER_COMMAND = "N01:B21001200E5018003E80064;"

    fun setPid(pid: Int): String = "{\"d158\":$pid}"

    fun setMaxBrightness(value: Int): String = "{\"d162\":${value.coerceIn(0, 1000)}}"

    fun grooveState(enabled: Boolean): String = "{\"d161\":${if (enabled) 1 else 0}}"

    fun grooveHandle(groove: String): String = "{\"d160\":\"${escapeJson(groove)}\"}"

    fun solidColorGroove(rgb: Int): String {
        require(rgb in 0x000000..0xFFFFFF) { "Invalid RGB value: $rgb" }
        return "N01:P10001${rgb.toString(16).padStart(6, '0')};"
    }

    private fun escapeJson(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"'  -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '' -> append("\\f")
                in ' '..'' -> append("\\u00${char.code.toString(16).padStart(2, '0')}")
                else -> append(char)
            }
        }
    }
}
```

注意：`highestPowerSequence` 函数被删除（旧测试里测过的，新测试里没了）。`CommandDispatcher` 在 Task 5 适配。

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew test --tests DpCommandsTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tkpang/tvstriptest/protocol/DpCommands.kt \
        app/src/test/java/com/tkpang/tvstriptest/protocol/DpCommandsTest.kt
git commit -m "feat: simplify DpCommands — drop ledCount, add MAX_POWER_COMMAND"
```

---

## Task 5: CommandDispatcher 适配新 setColor + 新 setHighestPowerColor

**Files:**
- Modify: `app/src/main/java/com/tkpang/tvstriptest/factory/CommandDispatcher.kt`
- Modify: `app/src/test/java/com/tkpang/tvstriptest/factory/CommandDispatcherTest.kt`

**Spec ref:** §5.4.2。

> 同时需要先临时调整 `FactoryModels.FactorySettings`：保留 `pid`、删除 `maxPowerColor`/`maxBrightness`（颜色测试 task 无关）。后续 task 再彻底重设 FactorySettings。本 task 仅最小改动让接口签名变成不依赖 LED 数。

- [ ] **Step 1: 调整 CommandDispatcher 接口签名**

修改 `CommandDispatcher` 接口：

```kotlin
interface CommandDispatcher {
    suspend fun connect(device: FactoryDevice): CommandResult
    suspend fun setPid(device: FactoryDevice, pid: Int): CommandResult
    suspend fun setColor(device: FactoryDevice, rgb: Int): CommandResult
    suspend fun setMaxPower(device: FactoryDevice): CommandResult
    suspend fun lightOff(device: FactoryDevice): CommandResult
    suspend fun unbindAndDelete(device: FactoryDevice): CommandResult
    suspend fun closeAll()
}
```

> 老接口的 `FactorySettings` 入参全删除——上层用例（Step3）按需传 rgb。`setHighestPowerColor` 改名 `setMaxPower`（语义更准）。

- [ ] **Step 2: Update test**

替换 `CommandDispatcherTest.kt` 中的 `setHighestPowerColor` / `setColor` 测试：

```kotlin
@Test
fun setColorWritesGrooveOnAndSolidColor() = runTest {
    val session = RecordingSession()
    val dispatcher = PerDeviceBleDispatcher { session }

    val result = dispatcher.setColor(device(), rgb = 0xFF0000)

    assertTrue(result.success)
    val payloads = session.writes.map { LeMessageCodec.decode(it).payload.decodeToString() }
    assertEquals(
        listOf(
            "{\"d161\":1} ",
            "{\"d162\":1000} ",
            "{\"d160\":\"N01:P10001ff0000;\"} ",
        ),
        payloads,
    )
}

@Test
fun setMaxPowerWritesHardcodedFirmwareCommand() = runTest {
    val session = RecordingSession()
    val dispatcher = PerDeviceBleDispatcher { session }

    val result = dispatcher.setMaxPower(device())

    assertTrue(result.success)
    val payloads = session.writes.map { LeMessageCodec.decode(it).payload.decodeToString() }
    assertEquals(
        listOf(
            "{\"d161\":1} ",
            "{\"d160\":\"N01:B21001200E5018003E80064;\"} ",
        ),
        payloads,
    )
}

@Test
fun lightOffWritesGrooveOff() = runTest {
    val session = RecordingSession()
    val dispatcher = PerDeviceBleDispatcher { session }

    val result = dispatcher.lightOff(device())

    assertTrue(result.success)
    val payloads = session.writes.map { LeMessageCodec.decode(it).payload.decodeToString() }
    assertEquals(listOf("{\"d161\":0} "), payloads)
}
```

并删除原 `setHighestPowerColor` / 老 `setColor` 用 `FactorySettings` 的测试。`setPid` 测试改成传 `pid: Int` 直接。

- [ ] **Step 3: Run test to verify it fails**

```bash
./gradlew test --tests CommandDispatcherTest
```
Expected: FAIL — interface signature mismatch.

- [ ] **Step 4: Implement new methods in PerDeviceBleDispatcher**

```kotlin
override suspend fun setPid(device: FactoryDevice, pid: Int): CommandResult = withSession(
    device = device,
    commands = listOf(DpCommands.setPid(pid)),
    successMessage = "PID set to $pid",
)

override suspend fun setColor(device: FactoryDevice, rgb: Int): CommandResult = withSession(
    device = device,
    commands = listOf(
        DpCommands.grooveState(true),
        DpCommands.setMaxBrightness(1000),
        DpCommands.grooveHandle(DpCommands.solidColorGroove(rgb)),
    ),
    successMessage = "Color set",
)

override suspend fun setMaxPower(device: FactoryDevice): CommandResult = withSession(
    device = device,
    commands = listOf(
        DpCommands.grooveState(true),
        DpCommands.grooveHandle(DpCommands.MAX_POWER_COMMAND),
    ),
    successMessage = "Max power set",
)

override suspend fun lightOff(device: FactoryDevice): CommandResult = withSession(
    device = device,
    commands = listOf(DpCommands.grooveState(false)),
    successMessage = "Light off",
)
```

删除旧的 `setHighestPowerColor` 和老 `setColor(...,settings,rgb)` 实现。

- [ ] **Step 5: 删除 ProductCatalog 临时兼容方法**

打开 `ProductCatalog.kt`，删除 Task 3 加的 `requireLedCount()` 临时方法。

- [ ] **Step 6: 删除现有 FactoryViewModel 中调到 setHighestPowerColor / 旧 setColor / 旧 setPid 的调用**

Edit `FactoryViewModel.kt` 把所有用到旧接口的方法暂用 stub 包起来或注释掉（Task 12 会全部重写 VM，这里只为让 build 过）。最简办法：暂时注释 VM 中所有 `dispatcher.setHighestPowerColor` / `dispatcher.setColor(device, settings, rgb)` 调用，加上 `// TODO Task 12 rewire`。

- [ ] **Step 7: 删除 FactoryScreen.kt 中调到老 VM 接口的 UI**

`FactoryScreen.kt` 用到 `onSetLightPid`/`onSetColor`/`onSetHighestPowerColor` 的 lambdas 暂时改成空 lambda；`MainActivity.kt` 同样适配。仍能 build。Task 22 会删除整个文件。

- [ ] **Step 8: Verify build + tests**

```bash
./gradlew compileDebugKotlin compileDebugUnitTestKotlin
./gradlew test
```
Expected: PASS（DpCommandsTest + CommandDispatcherTest + ProductCatalogTest + 现有 codec/crc tests）。

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/tkpang/tvstriptest/factory/CommandDispatcher.kt \
        app/src/main/java/com/tkpang/tvstriptest/factory/FactoryViewModel.kt \
        app/src/main/java/com/tkpang/tvstriptest/ui/FactoryScreen.kt \
        app/src/main/java/com/tkpang/tvstriptest/MainActivity.kt \
        app/src/main/java/com/tkpang/tvstriptest/model/ProductCatalog.kt \
        app/src/test/java/com/tkpang/tvstriptest/factory/CommandDispatcherTest.kt
git commit -m "refactor: simplify CommandDispatcher API — no LED count needed"
```

---

## Task 6: BleScanner 集成 AdvDataParser + 过滤参数

**Files:**
- Modify: `app/src/main/java/com/tkpang/tvstriptest/ble/BleScanner.kt`
- Modify: `app/src/main/java/com/tkpang/tvstriptest/model/FactoryModels.kt`

**Spec ref:** §6.1, §7.2。

> Android BLE scanner 不易在 unit test 跑（依赖 BluetoothManager），所以本 task 的「测试」靠下游 `ScanFilterUseCase`（Task 7）+ 手动现场验证。本 task 只做接口扩展和数据流注入。

- [ ] **Step 1: 扩展 ScanDevice**

Edit `app/src/main/java/com/tkpang/tvstriptest/model/FactoryModels.kt`：

```kotlin
package com.tkpang.tvstriptest.model

data class ScanDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val pid: Int? = null,                                         // [新]
    val isBonded: Boolean = false,                                // [新]
    val pairingState: PairingState = PairingState.DETECTED,       // [新]
)
```

> 现有 `FactoryDevice`、`FactorySettings`、`DeviceConnectionState` 保持不动，由 Task 12 统一精简。

> **现有架构提示**：当前 `MainActivity.kt` 里有一个 `FactoryScanner` 接口包装 `BleScanner`，签名是 `start(targetCount, rssiThreshold)`。本 task 简化 `BleScanner.start()` 后，需要同步更新 `FactoryScanner` 接口（去掉 targetCount/rssiThreshold 参数），或直接删掉该接口让 VM 直接用 `BleScanner`。**推荐：删掉接口，VM 直接持有 `BleScanner`**（少一层抽象）。

- [ ] **Step 2: 改写 BleScanner.handleResult**

Edit `BleScanner.kt`：

```kotlin
import com.tkpang.tvstriptest.protocol.AdvDataParser
// ...

private data class Candidate(
    val address: String,
    val name: String?,
    val rssi: Int,
    val pid: Int?,
    val isBonded: Boolean,
)

private fun handleResult(result: ScanResult) {
    val name = result.scanRecord?.deviceName
    val serviceUuids = result.scanRecord?.serviceUuids.orEmpty()
    val matchesService = serviceUuids.any { it.uuid == LeConstants.SERVICE_UUID }
    val matchesName = name == LeConstants.ADV_NAME
    if (!matchesService && !matchesName) return

    val address = result.device.address ?: return

    // 取第一条 manufacturer data（厂家 ID 不限，因为固件用 raw 0xff 段）
    val mfgArray: ByteArray? = result.scanRecord?.manufacturerSpecificData?.let { sparse ->
        if (sparse.size() == 0) null else sparse.valueAt(0)
    }
    val parsed = mfgArray?.let { AdvDataParser.parse(it) }

    synchronized(lock) {
        candidates[address] = Candidate(
            address = address,
            name = name,
            rssi = result.rssi,
            pid = parsed?.pid,
            isBonded = parsed?.isBonded ?: false,
        )
        publishDevicesLocked()
    }
}

private fun publishDevicesLocked() {
    _devices.value = candidates.values.sortedByDescending { it.rssi }.map {
        ScanDevice(
            address = it.address,
            name = it.name,
            rssi = it.rssi,
            pid = it.pid,
            isBonded = it.isBonded,
            pairingState = PairingState.DETECTED,
        )
    }
}
```

删除：`manualSelections`、`targetCount`、`rssiThreshold`、`autoSelected` 相关全部（Task 7 在 use case 层用 RSSI；scanner 不再过滤 RSSI/手动勾选）。

`start()` 签名简化：

```kotlin
@RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
fun start(): Boolean {
    // ...同原逻辑，去掉 targetCount/rssiThreshold 参数
}
```

> 由于上游调用方的 VM 暂未改造，这一步会让 VM 编译失败。临时给 VM 注释或 stub 调用，让 build 通过；Task 12 重写 VM 时彻底接好。

- [ ] **Step 3: Verify build**

```bash
./gradlew compileDebugKotlin compileDebugUnitTestKotlin
```
Expected: SUCCESS（暂留的 stub 让其通过）。

- [ ] **Step 4: 现有测试不会涉及 BleScanner，直接 run all**

```bash
./gradlew test
```
Expected: PASS（现有测试全绿）。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tkpang/tvstriptest/ble/BleScanner.kt \
        app/src/main/java/com/tkpang/tvstriptest/model/FactoryModels.kt \
        app/src/main/java/com/tkpang/tvstriptest/factory/FactoryViewModel.kt \
        app/src/main/java/com/tkpang/tvstriptest/ui/FactoryScreen.kt
git commit -m "feat: BleScanner parses manufacturer data into ScanDevice"
```

---

## Task 7: ScanFilterUseCase（PID + 未绑定过滤）

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/factory/ScanFilterUseCase.kt`
- Create: `app/src/test/java/com/tkpang/tvstriptest/factory/ScanFilterUseCaseTest.kt`

**Spec ref:** §7.3。

- [ ] **Step 1: Write failing test**

```kotlin
package com.tkpang.tvstriptest.factory

import com.tkpang.tvstriptest.model.PidFilter
import com.tkpang.tvstriptest.model.ScanDevice
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanFilterUseCaseTest {
    private val devices = listOf(
        d("AA:01", pid = 144, bonded = false),
        d("AA:02", pid = 144, bonded = true),
        d("AA:03", pid = 111, bonded = false),
        d("AA:04", pid = null, bonded = false),
    )

    @Test
    fun anyPidPlusOnlyUnbondedKeeps01And03() {
        val out = ScanFilterUseCase.apply(devices, PidFilter.Any, onlyUnbonded = true)
        assertEquals(listOf("AA:01", "AA:03"), out.map { it.address })
    }

    @Test
    fun anyPidPlusBondedAllowedKeepsAll() {
        val out = ScanFilterUseCase.apply(devices, PidFilter.Any, onlyUnbonded = false)
        assertEquals(listOf("AA:01", "AA:02", "AA:03", "AA:04"), out.map { it.address })
    }

    @Test
    fun specificPidPlusOnlyUnbondedKeepsOnly01() {
        val out = ScanFilterUseCase.apply(devices, PidFilter.Specific(144), onlyUnbonded = true)
        assertEquals(listOf("AA:01"), out.map { it.address })
    }

    @Test
    fun specificPidPlusBondedAllowedKeeps01And02() {
        val out = ScanFilterUseCase.apply(devices, PidFilter.Specific(144), onlyUnbonded = false)
        assertEquals(listOf("AA:01", "AA:02"), out.map { it.address })
    }

    @Test
    fun nullPidIsExcludedWhenSpecificFilterUsed() {
        val out = ScanFilterUseCase.apply(devices, PidFilter.Specific(144), onlyUnbonded = false)
        assertEquals(false, out.any { it.address == "AA:04" })
    }

    @Test
    fun nullPidIsKeptWhenAnyFilter() {
        val out = ScanFilterUseCase.apply(devices, PidFilter.Any, onlyUnbonded = false)
        assertEquals(true, out.any { it.address == "AA:04" })
    }

    private fun d(addr: String, pid: Int?, bonded: Boolean) = ScanDevice(
        address = addr, name = "LP", rssi = -60, pid = pid, isBonded = bonded,
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests ScanFilterUseCaseTest
```
Expected: FAIL — Unresolved reference.

- [ ] **Step 3: Implement ScanFilterUseCase**

```kotlin
package com.tkpang.tvstriptest.factory

import com.tkpang.tvstriptest.model.PidFilter
import com.tkpang.tvstriptest.model.ScanDevice

object ScanFilterUseCase {
    fun apply(
        devices: List<ScanDevice>,
        pidFilter: PidFilter,
        onlyUnbonded: Boolean,
    ): List<ScanDevice> = devices.filter { d ->
        val pidOk = when (pidFilter) {
            is PidFilter.Any -> true
            is PidFilter.Specific -> d.pid == pidFilter.pid
        }
        val bondOk = !onlyUnbonded || !d.isBonded
        pidOk && bondOk
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew test --tests ScanFilterUseCaseTest
```
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tkpang/tvstriptest/factory/ScanFilterUseCase.kt \
        app/src/test/java/com/tkpang/tvstriptest/factory/ScanFilterUseCaseTest.kt
git commit -m "feat: add ScanFilterUseCase for PID + unbonded filtering"
```

---

## Task 8: RadarStateMachine（设备进圈/退圈/计时）

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/factory/RadarStateMachine.kt`
- Create: `app/src/test/java/com/tkpang/tvstriptest/factory/RadarStateMachineTest.kt`

**Spec ref:** §5.3.3, §7.4。

> 这个类负责：监听过滤后的 `ScanDevice` 流 → 维护每台设备 `PairingState` → 当某设备 RSSI 持续 ≥ 阈值 1.5s → 发出 `pairingTriggers` 事件供 PairingExecutor 消费。退出圈即取消计时。

- [ ] **Step 1: Write failing test**

```kotlin
package com.tkpang.tvstriptest.factory

import com.tkpang.tvstriptest.model.ScanDevice
import com.tkpang.tvstriptest.model.SensitivityLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarStateMachineTest {

    @Test
    fun deviceInZoneFor1500msEmitsPairingTrigger() = runTest {
        val source = MutableStateFlow(listOf(d("AA:01", rssi = -50)))
        val sm = RadarStateMachine(
            sensitivity = MutableStateFlow(SensitivityLevel.NEAR),  // -65 阈值
            source = source,
            scope = backgroundScope,
            stableMillis = 1500L,
        )
        val triggers = mutableListOf<String>()
        backgroundScope.launchCollectTriggers(sm, triggers)

        advanceTimeBy(1499)
        assertEquals(emptyList<String>(), triggers)
        advanceTimeBy(2)
        assertEquals(listOf("AA:01"), triggers)
    }

    @Test
    fun deviceLeavingZoneCancelsTimer() = runTest {
        val source = MutableStateFlow(listOf(d("AA:01", rssi = -50)))
        val sm = RadarStateMachine(
            sensitivity = MutableStateFlow(SensitivityLevel.NEAR),
            source = source,
            scope = backgroundScope,
            stableMillis = 1500L,
        )
        val triggers = mutableListOf<String>()
        backgroundScope.launchCollectTriggers(sm, triggers)

        advanceTimeBy(1000)
        source.value = listOf(d("AA:01", rssi = -80))  // 退出 -65 圈
        advanceTimeBy(2000)

        assertEquals(emptyList<String>(), triggers)
    }

    @Test
    fun multipleDevicesInZoneFireBothTriggers() = runTest {
        val source = MutableStateFlow(
            listOf(d("AA:01", rssi = -50), d("AA:02", rssi = -55)),
        )
        val sm = RadarStateMachine(
            sensitivity = MutableStateFlow(SensitivityLevel.NEAR),
            source = source,
            scope = backgroundScope,
            stableMillis = 1500L,
        )
        val triggers = mutableListOf<String>()
        backgroundScope.launchCollectTriggers(sm, triggers)

        advanceTimeBy(1600)
        assertEquals(setOf("AA:01", "AA:02"), triggers.toSet())
    }

    @Test
    fun deviceDisappearingFromScanCancelsTimer() = runTest {
        val source = MutableStateFlow(listOf(d("AA:01", rssi = -50)))
        val sm = RadarStateMachine(
            sensitivity = MutableStateFlow(SensitivityLevel.NEAR),
            source = source,
            scope = backgroundScope,
            stableMillis = 1500L,
        )
        val triggers = mutableListOf<String>()
        backgroundScope.launchCollectTriggers(sm, triggers)

        advanceTimeBy(1000)
        source.value = emptyList()
        advanceTimeBy(2000)
        assertEquals(emptyList<String>(), triggers)
    }

    private fun d(addr: String, rssi: Int) = ScanDevice(
        address = addr, name = "LP", rssi = rssi, pid = 144, isBonded = false,
    )
}

private fun kotlinx.coroutines.CoroutineScope.launchCollectTriggers(
    sm: RadarStateMachine,
    sink: MutableList<String>,
) {
    kotlinx.coroutines.launch {
        sm.pairingTriggers.collect { sink += it }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests RadarStateMachineTest
```
Expected: FAIL — Unresolved reference.

- [ ] **Step 3: Implement RadarStateMachine**

```kotlin
package com.tkpang.tvstriptest.factory

import com.tkpang.tvstriptest.model.ScanDevice
import com.tkpang.tvstriptest.model.SensitivityLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class RadarStateMachine(
    private val sensitivity: StateFlow<SensitivityLevel>,
    source: StateFlow<List<ScanDevice>>,
    private val scope: CoroutineScope,
    private val stableMillis: Long = 1500L,
) {
    private val _pairingTriggers = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val pairingTriggers: Flow<String> = _pairingTriggers.asSharedFlow()

    private val timers = mutableMapOf<String, Job>()
    private val pairedOrSkipped = mutableSetOf<String>()  // 防止重复触发

    init {
        scope.launch {
            source.collect { devices -> reconcile(devices) }
        }
    }

    /** 由外部告知某 address 已经处理过（PAIRED / PID_MISMATCH / 永久 FAILED），不要再触发 */
    fun markHandled(address: String) {
        pairedOrSkipped += address
        timers.remove(address)?.cancel()
    }

    /** 设备允许重新参与配对（FAILED 后 2s 重置）*/
    fun resetDevice(address: String) {
        pairedOrSkipped -= address
    }

    private fun reconcile(devices: List<ScanDevice>) {
        val threshold = sensitivity.value.rssiThreshold
        val inZoneAddresses = devices
            .filter { it.rssi >= threshold && it.address !in pairedOrSkipped }
            .map { it.address }
            .toSet()

        // 不在中心区或消失了 → 取消计时
        timers.keys.toList().forEach { addr ->
            if (addr !in inZoneAddresses) {
                timers.remove(addr)?.cancel()
            }
        }

        // 新进圈 → 起计时
        inZoneAddresses.forEach { addr ->
            if (addr !in timers) {
                timers[addr] = scope.launch {
                    delay(stableMillis)
                    _pairingTriggers.emit(addr)
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew test --tests RadarStateMachineTest
```
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tkpang/tvstriptest/factory/RadarStateMachine.kt \
        app/src/test/java/com/tkpang/tvstriptest/factory/RadarStateMachineTest.kt
git commit -m "feat: add RadarStateMachine for in-zone stability detection"
```

---

## Task 9: PairingExecutor（顺序队列：连接 → 验 PID → bond）

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/factory/PairingExecutor.kt`
- Create: `app/src/test/java/com/tkpang/tvstriptest/factory/PairingExecutorTest.kt`

**Spec ref:** §5.3.3, §7.5。

> PairingExecutor 接收 `pairingTriggers` 的 address 流，串行执行：connect → readDevInfo → 验证 PID → bond。结果 emit 为 `PairingOutcome`，由 VM 消费更新 ScanDevice 状态 + 计数器。

- [ ] **Step 1: 定义 PairingOutcome / PairingExecutor 接口**

```kotlin
// PairingExecutor.kt 第一段（先写出 sealed result + interface）
package com.tkpang.tvstriptest.factory

import com.tkpang.tvstriptest.model.PidFilter
import com.tkpang.tvstriptest.protocol.BleDeviceInfo

sealed interface PairingOutcome {
    val address: String
    data class Paired(override val address: String, val pid: Int) : PairingOutcome
    data class PidMismatch(override val address: String, val actualPid: Int) : PairingOutcome
    data class ConnectFailed(override val address: String, val attempt: Int) : PairingOutcome
    data class BondFailed(override val address: String, val attempt: Int) : PairingOutcome
    data class PermanentlyFailed(override val address: String) : PairingOutcome
}

interface PairingExecutor {
    suspend fun pair(address: String): PairingOutcome
}
```

> 「permanent fail」由调用方根据 `attempt` 累计判断；executor 本身只做单次尝试 + 报告原因。

- [ ] **Step 2: Write failing test**

```kotlin
package com.tkpang.tvstriptest.factory

import com.tkpang.tvstriptest.model.FactoryDevice
import com.tkpang.tvstriptest.model.PidFilter
import com.tkpang.tvstriptest.protocol.BleDeviceInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingExecutorTest {

    @Test
    fun successfulConnectAndPidMatchProducesPaired() = runTest {
        val dispatcher = FakeDispatcher(connectInfo = BleDeviceInfo(pid = 144, did = 1, firmwareVersion = null))
        val exec = PairingExecutorImpl(dispatcher, pidFilter = PidFilter.Specific(144))
        val outcome = exec.pair("AA:01")
        assertTrue(outcome is PairingOutcome.Paired)
        assertEquals(144, (outcome as PairingOutcome.Paired).pid)
    }

    @Test
    fun pidMismatchReportsPidMismatch() = runTest {
        val dispatcher = FakeDispatcher(connectInfo = BleDeviceInfo(pid = 111, did = 1, firmwareVersion = null))
        val exec = PairingExecutorImpl(dispatcher, pidFilter = PidFilter.Specific(144))
        val outcome = exec.pair("AA:01")
        assertEquals(PairingOutcome.PidMismatch("AA:01", actualPid = 111), outcome)
    }

    @Test
    fun connectFailureReportsConnectFailed() = runTest {
        val dispatcher = FakeDispatcher(connectInfo = null)  // 模拟连接失败
        val exec = PairingExecutorImpl(dispatcher, pidFilter = PidFilter.Any)
        val outcome = exec.pair("AA:01")
        assertTrue(outcome is PairingOutcome.ConnectFailed)
    }

    @Test
    fun anyPidFilterAcceptsMismatchPid() = runTest {
        val dispatcher = FakeDispatcher(connectInfo = BleDeviceInfo(pid = 999, did = 1, firmwareVersion = null))
        val exec = PairingExecutorImpl(dispatcher, pidFilter = PidFilter.Any)
        val outcome = exec.pair("AA:01")
        assertTrue(outcome is PairingOutcome.Paired)
    }

    private class FakeDispatcher(private val connectInfo: BleDeviceInfo?) : CommandDispatcher {
        override suspend fun connect(device: FactoryDevice): CommandResult = CommandResult(
            address = device.address,
            success = connectInfo != null,
            message = if (connectInfo != null) "ok" else "connect failed",
            deviceInfo = connectInfo,
        )
        override suspend fun setPid(device: FactoryDevice, pid: Int) = ok(device)
        override suspend fun setColor(device: FactoryDevice, rgb: Int) = ok(device)
        override suspend fun setMaxPower(device: FactoryDevice) = ok(device)
        override suspend fun lightOff(device: FactoryDevice) = ok(device)
        override suspend fun unbindAndDelete(device: FactoryDevice) = ok(device)
        override suspend fun closeAll() = Unit
        private fun ok(d: FactoryDevice) = CommandResult(d.address, true, "ok")
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
./gradlew test --tests PairingExecutorTest
```
Expected: FAIL — Unresolved reference.

- [ ] **Step 4: Implement PairingExecutorImpl**

加到 `PairingExecutor.kt`：

```kotlin
import com.tkpang.tvstriptest.model.FactoryDevice

class PairingExecutorImpl(
    private val dispatcher: CommandDispatcher,
    private val pidFilter: PidFilter,
) : PairingExecutor {
    private val attempts = mutableMapOf<String, Int>()

    override suspend fun pair(address: String): PairingOutcome {
        val device = FactoryDevice(address = address, name = null)
        val result = dispatcher.connect(device)

        if (!result.success || result.deviceInfo == null) {
            val attempt = (attempts[address] ?: 0) + 1
            attempts[address] = attempt
            return if (attempt >= 3) {
                PairingOutcome.PermanentlyFailed(address)
            } else {
                PairingOutcome.ConnectFailed(address, attempt)
            }
        }

        val info = result.deviceInfo
        val pidOk = when (pidFilter) {
            is PidFilter.Any -> true
            is PidFilter.Specific -> info.pid == pidFilter.pid
        }
        if (!pidOk) {
            return PairingOutcome.PidMismatch(address, actualPid = info.pid)
        }

        // PID 匹配 → 这里默认 connect 已经包含 bond 了（看现有 BleDeviceSession 实现），
        // 如果未来 bond 是独立步骤，可在此插入 dispatcher.bond(device)。
        attempts.remove(address)
        return PairingOutcome.Paired(address, pid = info.pid)
    }
}
```

> **关于 bond**：现有 `BleDeviceSession.connect()` 已经做了 bond（看 `LE_CMD_BOND` 在 connect 流程里发出）。所以 PairingExecutor 不再单独发 bond；如未来需要解耦，再加一个 `bond` 步骤。本 task 注释里把这点写明。

- [ ] **Step 5: Run test to verify it passes**

```bash
./gradlew test --tests PairingExecutorTest
```
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tkpang/tvstriptest/factory/PairingExecutor.kt \
        app/src/test/java/com/tkpang/tvstriptest/factory/PairingExecutorTest.kt
git commit -m "feat: add PairingExecutor with PID validation and retry tracking"
```

---

## Task 10: ColorTestUseCase（6 色 + 最大功率，并发 3）

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/factory/ColorTestUseCase.kt`
- Create: `app/src/test/java/com/tkpang/tvstriptest/factory/ColorTestUseCaseTest.kt`

**Spec ref:** §5.4.2, §7.6。

- [ ] **Step 1: Write failing test**

```kotlin
package com.tkpang.tvstriptest.factory

import com.tkpang.tvstriptest.model.FactoryDevice
import com.tkpang.tvstriptest.protocol.BleDeviceInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorTestUseCaseTest {

    @Test
    fun redDispatchesSetColorRedToAllDevices() = runTest {
        val dispatcher = RecordingDispatcher()
        val useCase = ColorTestUseCase(dispatcher)

        val result = useCase.execute(
            devices = listOf(dev("AA:01"), dev("AA:02"), dev("AA:03")),
            command = ColorTestUseCase.Command.RED,
        )

        assertEquals(3, result.success)
        assertEquals(0, result.failed.size)
        assertEquals(listOf(0xFF0000, 0xFF0000, 0xFF0000), dispatcher.colors)
    }

    @Test
    fun offDispatchesLightOff() = runTest {
        val dispatcher = RecordingDispatcher()
        val useCase = ColorTestUseCase(dispatcher)
        useCase.execute(listOf(dev("AA:01")), ColorTestUseCase.Command.OFF)
        assertEquals(listOf("lightOff"), dispatcher.calls)
    }

    @Test
    fun maxPowerDispatchesSetMaxPower() = runTest {
        val dispatcher = RecordingDispatcher()
        val useCase = ColorTestUseCase(dispatcher)
        useCase.execute(listOf(dev("AA:01")), ColorTestUseCase.Command.MAX_POWER)
        assertEquals(listOf("setMaxPower"), dispatcher.calls)
    }

    @Test
    fun mixedSuccessFailureProducesPartialResult() = runTest {
        val dispatcher = RecordingDispatcher(failAddresses = setOf("AA:02"))
        val useCase = ColorTestUseCase(dispatcher)
        val result = useCase.execute(
            listOf(dev("AA:01"), dev("AA:02"), dev("AA:03")),
            ColorTestUseCase.Command.GREEN,
        )
        assertEquals(2, result.success)
        assertEquals(setOf("AA:02"), result.failed.map { it.address }.toSet())
    }

    private fun dev(addr: String) = FactoryDevice(address = addr, name = null)

    private class RecordingDispatcher(
        private val failAddresses: Set<String> = emptySet(),
    ) : CommandDispatcher {
        val colors = mutableListOf<Int>()
        val calls = mutableListOf<String>()
        override suspend fun connect(device: FactoryDevice) = ok(device)
        override suspend fun setPid(device: FactoryDevice, pid: Int) = ok(device)
        override suspend fun setColor(device: FactoryDevice, rgb: Int): CommandResult {
            colors += rgb
            calls += "setColor"
            return result(device)
        }
        override suspend fun setMaxPower(device: FactoryDevice): CommandResult {
            calls += "setMaxPower"; return result(device)
        }
        override suspend fun lightOff(device: FactoryDevice): CommandResult {
            calls += "lightOff"; return result(device)
        }
        override suspend fun unbindAndDelete(device: FactoryDevice) = ok(device)
        override suspend fun closeAll() = Unit
        private fun result(d: FactoryDevice) =
            if (d.address in failAddresses) CommandResult(d.address, false, "boom")
            else CommandResult(d.address, true, "ok")
        private fun ok(d: FactoryDevice) = CommandResult(d.address, true, "ok")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests ColorTestUseCaseTest
```
Expected: FAIL.

- [ ] **Step 3: Implement ColorTestUseCase**

```kotlin
package com.tkpang.tvstriptest.factory

import com.tkpang.tvstriptest.model.FactoryDevice
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ColorTestUseCase(
    private val dispatcher: CommandDispatcher,
    private val maxConcurrent: Int = 3,
) {
    enum class Command(val rgb: Int?) {
        RED(0xFF0000),
        GREEN(0x00FF00),
        BLUE(0x0000FF),
        WHITE(0xFFFFFF),
        BLACK(0x000000),
        OFF(null),
        MAX_POWER(null),
    }

    data class Result(val success: Int, val failed: List<CommandResult>)

    suspend fun execute(devices: List<FactoryDevice>, command: Command): Result = coroutineScope {
        val gate = Semaphore(maxConcurrent)
        val results = devices.map { device ->
            async { gate.withPermit { dispatch(device, command) } }
        }.awaitAll()

        Result(
            success = results.count { it.success },
            failed = results.filterNot { it.success },
        )
    }

    private suspend fun dispatch(device: FactoryDevice, command: Command): CommandResult =
        when (command) {
            Command.RED, Command.GREEN, Command.BLUE,
            Command.WHITE, Command.BLACK -> dispatcher.setColor(device, command.rgb!!)
            Command.OFF -> dispatcher.lightOff(device)
            Command.MAX_POWER -> dispatcher.setMaxPower(device)
        }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew test --tests ColorTestUseCaseTest
```
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tkpang/tvstriptest/factory/ColorTestUseCase.kt \
        app/src/test/java/com/tkpang/tvstriptest/factory/ColorTestUseCaseTest.kt
git commit -m "feat: add ColorTestUseCase with concurrent dispatch (limit 3)"
```

---

## Task 11: UnbindUseCase

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/factory/UnbindUseCase.kt`
- Create: `app/src/test/java/com/tkpang/tvstriptest/factory/UnbindUseCaseTest.kt`

**Spec ref:** §5.5, §7.7。

- [ ] **Step 1: Write failing test**

```kotlin
package com.tkpang.tvstriptest.factory

import com.tkpang.tvstriptest.model.FactoryDevice
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UnbindUseCaseTest {

    @Test
    fun unbindsAllDevicesAndEmitsProgress() = runTest {
        val dispatcher = RecordingDispatcher()
        val useCase = UnbindUseCase(dispatcher)
        val devices = listOf(d("AA:01"), d("AA:02"), d("AA:03"))

        val progress = useCase.execute(devices).toList()
        // 3 progress updates + 1 final
        assertEquals(4, progress.size)
        assertEquals(3, progress.last().completed)
        assertEquals(0, progress.last().failed.size)
    }

    @Test
    fun reportsFailedDevicesWhenSomeFail() = runTest {
        val dispatcher = RecordingDispatcher(failAddresses = setOf("AA:02"))
        val useCase = UnbindUseCase(dispatcher)
        val progress = useCase.execute(listOf(d("AA:01"), d("AA:02"), d("AA:03"))).toList()
        val final = progress.last()
        assertEquals(2, final.completed)
        assertEquals(setOf("AA:02"), final.failed.map { it.address }.toSet())
    }

    private fun d(addr: String) = FactoryDevice(address = addr, name = null)

    private class RecordingDispatcher(
        private val failAddresses: Set<String> = emptySet(),
    ) : CommandDispatcher {
        override suspend fun connect(device: FactoryDevice) = ok(device)
        override suspend fun setPid(device: FactoryDevice, pid: Int) = ok(device)
        override suspend fun setColor(device: FactoryDevice, rgb: Int) = ok(device)
        override suspend fun setMaxPower(device: FactoryDevice) = ok(device)
        override suspend fun lightOff(device: FactoryDevice) = ok(device)
        override suspend fun unbindAndDelete(device: FactoryDevice) =
            if (device.address in failAddresses) CommandResult(device.address, false, "boom")
            else ok(device)
        override suspend fun closeAll() = Unit
        private fun ok(d: FactoryDevice) = CommandResult(d.address, true, "ok")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests UnbindUseCaseTest
```
Expected: FAIL.

- [ ] **Step 3: Implement UnbindUseCase**

```kotlin
package com.tkpang.tvstriptest.factory

import com.tkpang.tvstriptest.model.FactoryDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class UnbindUseCase(
    private val dispatcher: CommandDispatcher,
    private val maxConcurrent: Int = 3,
) {
    data class Progress(
        val completed: Int,
        val total: Int,
        val failed: List<CommandResult>,
    )

    fun execute(devices: List<FactoryDevice>): Flow<Progress> = flow {
        val gate = Semaphore(maxConcurrent)
        val failed = mutableListOf<CommandResult>()
        var completed = 0

        coroutineScope {
            val tasks = devices.map { device ->
                async {
                    gate.withPermit { dispatcher.unbindAndDelete(device) }
                }
            }
            for (task in tasks) {
                val result = task.await()
                if (!result.success) failed += result
                completed += 1
                emit(Progress(completed, devices.size, failed.toList()))
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew test --tests UnbindUseCaseTest
```
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tkpang/tvstriptest/factory/UnbindUseCase.kt \
        app/src/test/java/com/tkpang/tvstriptest/factory/UnbindUseCaseTest.kt
git commit -m "feat: add UnbindUseCase with progress flow"
```

---

## Task 12: 重写 FactoryViewModel + 精简 FactorySettings

**Files:**
- Modify: `app/src/main/java/com/tkpang/tvstriptest/factory/FactoryViewModel.kt`
- Modify: `app/src/main/java/com/tkpang/tvstriptest/model/FactoryModels.kt`

**Spec ref:** §4.1, §6.2。

> 这一 task 重写 ViewModel：仅持有「当前 step」+ Step1 设置 + Step2 的 paired devices + 各 use case 的事件订阅。所有具体功能委托给上面 task 的 use cases。

- [ ] **Step 1: 精简 FactorySettings**

Edit `model/FactoryModels.kt`：

```kotlin
data class FactorySettings(
    val productDevName: String = "STV1",
    val pidFilter: PidFilter = PidFilter.Any,
    val onlyUnbonded: Boolean = true,
    val sensitivity: SensitivityLevel = SensitivityLevel.NEAR,
)
```

删除字段：`pid`、`targetCount`、`rssiThreshold`、`maxPowerColor`、`maxBrightness`。

- [ ] **Step 2: 定义 WizardStep**

加到 `model/FactoryModels.kt`：

```kotlin
enum class WizardStep(val index: Int, val displayName: String) {
    PRODUCT(0, "选条件"),
    SCAN(1, "扫描配对"),
    COLOR(2, "颜色测试"),
    UNBIND(3, "解绑");
}
```

- [ ] **Step 3: 重写 FactoryUiState**

```kotlin
data class FactoryUiState(
    val step: WizardStep = WizardStep.PRODUCT,
    val settings: FactorySettings = FactorySettings(),
    val visibleDevices: List<ScanDevice> = emptyList(),  // 雷达上当前显示的（已过滤）
    val pairedDevices: List<FactoryDevice> = emptyList(), // 已配对、待测试
    val pairingMessage: String? = null,                   // 雷达页顶部短暂消息
    val colorTestResult: ColorTestUseCase.Result? = null,
    val unbindProgress: UnbindUseCase.Progress? = null,
    val errorBanner: ErrorBanner? = null,                 // 蓝牙关闭 / 缺权限
)

sealed interface ErrorBanner {
    data object BluetoothOff : ErrorBanner
    data object MissingPermission : ErrorBanner
}
```

- [ ] **Step 4: Rewrite FactoryViewModel**

```kotlin
package com.tkpang.tvstriptest.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkpang.tvstriptest.ble.BleScanner
import com.tkpang.tvstriptest.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FactoryViewModel(
    private val scanner: BleScanner,
    private val dispatcher: CommandDispatcher,
) : ViewModel() {
    private val _settings = MutableStateFlow(FactorySettings())
    private val _step = MutableStateFlow(WizardStep.PRODUCT)
    private val _pairedDevices = MutableStateFlow<List<FactoryDevice>>(emptyList())
    private val _pairingMessage = MutableStateFlow<String?>(null)
    private val _colorTestResult = MutableStateFlow<ColorTestUseCase.Result?>(null)
    private val _unbindProgress = MutableStateFlow<UnbindUseCase.Progress?>(null)
    private val _errorBanner = MutableStateFlow<ErrorBanner?>(null)

    private val sensitivityFlow = _settings.map { it.sensitivity }.stateIn(
        viewModelScope, SharingStarted.Eagerly, SensitivityLevel.NEAR,
    )

    private val filteredDevices: StateFlow<List<ScanDevice>> = combine(
        scanner.devices, _settings,
    ) { raw, s -> ScanFilterUseCase.apply(raw, s.pidFilter, s.onlyUnbonded) }.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList(),
    )

    private val radarStateMachine = RadarStateMachine(
        sensitivity = sensitivityFlow,
        source = filteredDevices,
        scope = viewModelScope,
    )

    private val pairingExecutor get() = PairingExecutorImpl(
        dispatcher = dispatcher,
        pidFilter = _settings.value.pidFilter,
    )

    val uiState: StateFlow<FactoryUiState> = combine(
        _step, _settings, filteredDevices, _pairedDevices,
        _pairingMessage, _colorTestResult, _unbindProgress, _errorBanner,
    ) { args ->
        FactoryUiState(
            step = args[0] as WizardStep,
            settings = args[1] as FactorySettings,
            visibleDevices = args[2] as List<ScanDevice>,
            pairedDevices = args[3] as List<FactoryDevice>,
            pairingMessage = args[4] as String?,
            colorTestResult = args[5] as ColorTestUseCase.Result?,
            unbindProgress = args[6] as UnbindUseCase.Progress?,
            errorBanner = args[7] as ErrorBanner?,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FactoryUiState())

    init {
        viewModelScope.launch {
            radarStateMachine.pairingTriggers.collect { addr -> handlePairingTrigger(addr) }
        }
    }

    private suspend fun handlePairingTrigger(addr: String) {
        val outcome = pairingExecutor.pair(addr)
        when (outcome) {
            is PairingOutcome.Paired -> {
                _pairedDevices.value = _pairedDevices.value + FactoryDevice(addr, name = null)
                _pairingMessage.value = "✓ ${addr.takeLast(4)} 已配上"
                radarStateMachine.markHandled(addr)
            }
            is PairingOutcome.PidMismatch -> {
                _pairingMessage.value = "⚠ ${addr.takeLast(4)} PID 不符 · 跳过"
                radarStateMachine.markHandled(addr)
            }
            is PairingOutcome.PermanentlyFailed -> {
                radarStateMachine.markHandled(addr)
            }
            is PairingOutcome.ConnectFailed,
            is PairingOutcome.BondFailed -> {
                // 稍后允许重试
                viewModelScope.launch {
                    kotlinx.coroutines.delay(2000)
                    radarStateMachine.resetDevice(addr)
                }
            }
        }
    }

    // === Step 控制 ===
    fun goToStep(step: WizardStep) { _step.value = step }
    fun next() { WizardStep.values().getOrNull(_step.value.index + 1)?.let { _step.value = it } }
    fun back() { WizardStep.values().getOrNull(_step.value.index - 1)?.let { _step.value = it } }
    fun resetToFirstStep() {
        _step.value = WizardStep.PRODUCT
        _pairedDevices.value = emptyList()
        _colorTestResult.value = null
        _unbindProgress.value = null
        _pairingMessage.value = null
    }

    // === Step 1 ===
    fun setProductType(devName: String) {
        _settings.update { it.copy(productDevName = devName) }
    }
    fun setPidFilter(filter: PidFilter) {
        _settings.update { it.copy(pidFilter = filter) }
    }
    fun setOnlyUnbonded(value: Boolean) {
        _settings.update { it.copy(onlyUnbonded = value) }
    }

    // === Step 2 ===
    fun setSensitivity(level: SensitivityLevel) {
        _settings.update { it.copy(sensitivity = level) }
    }
    fun startScan() {
        if (!scanner.start()) {
            _errorBanner.value = ErrorBanner.MissingPermission
        } else {
            _errorBanner.value = null
        }
    }
    fun stopScan() = scanner.stop()

    // === Step 3 ===
    fun runColorTest(command: ColorTestUseCase.Command) {
        viewModelScope.launch {
            val result = ColorTestUseCase(dispatcher).execute(_pairedDevices.value, command)
            _colorTestResult.value = result
        }
    }

    // === Step 4 ===
    fun runUnbind() {
        viewModelScope.launch {
            UnbindUseCase(dispatcher).execute(_pairedDevices.value).collect {
                _unbindProgress.value = it
            }
        }
    }
}

private fun MutableStateFlow<FactorySettings>.update(transform: (FactorySettings) -> FactorySettings) {
    value = transform(value)
}
```

> 实现里把所有不必要的 lambda 删掉，VM 入参极简：仅 BleScanner / CommandDispatcher。`dispatcher` 必填，由 Task 22 在 MainActivity 里通过现有 `BleDeviceSession` factory 注入（保留现有 BleDeviceSession 工厂，不改 BLE 实际连接逻辑）。`WritePidViewModel` 同理（构造也要必填 dispatcher）。

- [ ] **Step 5: Verify build**

```bash
./gradlew compileDebugKotlin
```

可能有 `ProductCatalog`、其他文件引用旧 `FactorySettings.pid` 之类编译错。逐个修：把所有读取旧字段的地方改成读取 `pidFilter` 或在 step 内部传具体 `Int`。

- [ ] **Step 6: 跑现有测试**

```bash
./gradlew test
```
Expected: PASS（VM 没有单测，所有 use case test 应当都过）。

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/tkpang/tvstriptest/factory/FactoryViewModel.kt \
        app/src/main/java/com/tkpang/tvstriptest/model/FactoryModels.kt
git commit -m "refactor: rewrite FactoryViewModel for 4-step wizard architecture"
```

---

## Task 13: UI 共用组件 — StepIndicator + BottomNavBar

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/ui/wizard/components/StepIndicator.kt`
- Create: `app/src/main/java/com/tkpang/tvstriptest/ui/wizard/components/BottomNavBar.kt`

**Spec ref:** §4.2。

> UI 组件用 @Preview 自检；不写单测。

- [ ] **Step 1: 创建 StepIndicator.kt**

```kotlin
package com.tkpang.tvstriptest.ui.wizard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tkpang.tvstriptest.model.WizardStep

@Composable
fun StepIndicator(currentStep: WizardStep, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = currentStep.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WizardStep.values().forEach { s ->
                val color = when {
                    s.index < currentStep.index -> Color(0xFF22C55E)  // done
                    s.index == currentStep.index -> Color(0xFF2563EB) // now
                    else -> Color(0xFFCBD5E1)                          // future
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color),
                )
            }
        }
    }
}

@Preview
@Composable
private fun StepIndicatorPreview() {
    StepIndicator(currentStep = WizardStep.SCAN, modifier = Modifier.padding(16.dp))
}
```

- [ ] **Step 2: 创建 BottomNavBar.kt**

```kotlin
package com.tkpang.tvstriptest.ui.wizard.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BottomNavBar(
    onBack: (() -> Unit)?,
    onNext: (() -> Unit)?,
    nextLabel: String = "下一步 →",
    backLabel: String = "← 上一步",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onBack ?: {},
            enabled = onBack != null,
            modifier = Modifier.weight(1f),
        ) { Text(backLabel) }
        Button(
            onClick = onNext ?: {},
            enabled = onNext != null,
            modifier = Modifier.weight(1f),
        ) { Text(nextLabel) }
    }
}
```

- [ ] **Step 3: Verify build**

```bash
./gradlew compileDebugKotlin
```
Expected: SUCCESS。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tkpang/tvstriptest/ui/wizard/components/
git commit -m "feat: add StepIndicator and BottomNavBar wizard components"
```

---

## Task 14: PulseRadar Composable

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/ui/wizard/step2/PulseRadar.kt`

**Spec ref:** §5.3.1, §5.3.2 + mockup `radar-page.html`。

> 雷达 UI 关键在：脉冲背景动画、中心固定的「自动配对中心区」虚线圆、设备 chip 按 RSSI 径向 + MAC hash 角度定位、不同 PairingState 不同样式。

- [ ] **Step 1: 创建 PulseRadar.kt（基础结构）**

```kotlin
package com.tkpang.tvstriptest.ui.wizard.step2

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tkpang.tvstriptest.model.PairingState
import com.tkpang.tvstriptest.model.ScanDevice
import com.tkpang.tvstriptest.model.SensitivityLevel
import kotlin.math.*

@Composable
fun PulseRadar(
    devices: List<ScanDevice>,
    sensitivity: SensitivityLevel,
    pairingStates: Map<String, PairingState>,
    radius: Dp = 110.dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(radius * 2), contentAlignment = Alignment.Center) {
        PulseRings(radius = radius)
        AutoPairZone(radius = radius, fraction = sensitivity.zoneRadiusFraction)
        CenterDot()
        devices.forEach { device ->
            val state = pairingStates[device.address] ?: device.pairingState
            DeviceBlip(
                device = device,
                state = state,
                radarRadiusPx = radius.value,
            )
        }
    }
}

@Composable
private fun PulseRings(radius: Dp) {
    val transition = rememberInfiniteTransition(label = "pulse")
    repeat(3) { i ->
        val scale by transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = LinearEasing),
                initialStartOffset = StartOffset(i * 800),
            ),
            label = "scale$i",
        )
        val alpha by transition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = LinearEasing),
                initialStartOffset = StartOffset(i * 800),
            ),
            label = "alpha$i",
        )
        Box(
            Modifier
                .size(radius * 2)
                .scale(scale)
                .border(2.dp, Color(0xFF2563EB).copy(alpha = alpha), CircleShape),
        )
    }
}

@Composable
private fun AutoPairZone(radius: Dp, fraction: Float) {
    Box(
        Modifier
            .size(radius * 2 * fraction)
            .border(
                width = 1.5.dp,
                color = Color(0xFF60A5FA),
                shape = CircleShape,
            )
            .background(Color(0x14_60A5FA), CircleShape),
    )
    // Note: dashed border in Compose 需要 Canvas，这里用实色简化；如需虚线效果，
    // 可在 Modifier.drawBehind 里画 Path with PathEffect.dashPathEffect。
}

@Composable
private fun CenterDot() {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(listOf(Color(0xFF3B82F6), Color(0xFF1E3A8A))),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text("📱", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun DeviceBlip(device: ScanDevice, state: PairingState, radarRadiusPx: Float) {
    val (bg, fg, dot) = chipColors(state)
    val (xFrac, yFrac) = positionForDevice(device)
    val xDp = (xFrac * radarRadiusPx).dp
    val yDp = (yFrac * radarRadiusPx).dp
    val label = "${chipPrefix(state)}${device.address.takeLast(4).replace(":", "")}${chipSuffix(state)}"

    Box(
        Modifier
            .offset(x = xDp, y = yDp)
            .background(bg, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(5.dp))
            Text(label, color = fg, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun chipColors(state: PairingState) = when (state) {
    PairingState.DETECTED -> Triple(Color.White, Color(0xFF0F172A), Color(0xFF22C55E))
    PairingState.PREPARING -> Triple(Color.White, Color(0xFF15803D), Color(0xFF22C55E))
    PairingState.QUEUED, PairingState.CONNECTING ->
        Triple(Color(0xFFF1F5F9), Color(0xFF64748B), Color(0xFF94A3B8))
    PairingState.PID_MISMATCH, PairingState.FAILED, PairingState.PAIRED ->
        Triple(Color(0xFFFEF2F2), Color(0xFFDC2626), Color(0xFFEF4444))
}

private fun chipPrefix(state: PairingState) = ""
private fun chipSuffix(state: PairingState) = when (state) {
    PairingState.PREPARING -> " 准备配对"
    PairingState.QUEUED -> " 队列中"
    PairingState.CONNECTING -> " 连接中"
    PairingState.PID_MISMATCH -> " PID 不符"
    PairingState.FAILED -> " 连接失败"
    else -> ""
}

private fun positionForDevice(device: ScanDevice): Pair<Float, Float> {
    val rssi = device.rssi.toFloat().coerceIn(-90f, -30f)
    val r = ((-30f - rssi) / 60f).coerceIn(0f, 1f) * 0.85f  // 0.85 留点边
    val angleDeg = (device.address.hashCode() and 0x1FF) % 360
    val angle = Math.toRadians(angleDeg.toDouble())
    return (r * cos(angle).toFloat()) to (r * sin(angle).toFloat())
}
```

> 这是结构骨架，外观细节（虚线圆、平滑动画）在实现时按 mockup 微调。`@Preview` 可以拿假设备验证视觉。

- [ ] **Step 2: 加 Preview**

```kotlin
import androidx.compose.ui.tooling.preview.Preview
@Preview
@Composable
private fun PulseRadarPreview() {
    val devices = listOf(
        ScanDevice("AA:01:02:03:04:05", "LP", -55, pid = 144, isBonded = false),
        ScanDevice("BB:11:22:33:44:55", "LP", -75, pid = 144, isBonded = false),
    )
    PulseRadar(
        devices = devices,
        sensitivity = SensitivityLevel.NEAR,
        pairingStates = mapOf("AA:01:02:03:04:05" to PairingState.PREPARING),
    )
}
```

- [ ] **Step 3: Build + 视觉自检**

```bash
./gradlew compileDebugKotlin
./gradlew assembleDebug
```
Expected: SUCCESS。在 Android Studio 里打开文件看 Preview 渲染。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tkpang/tvstriptest/ui/wizard/step2/PulseRadar.kt
git commit -m "feat: add PulseRadar composable with state-based device blips"
```

---

## Task 15: SensitivitySheet（灵敏度抽屉）

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/ui/wizard/step2/SensitivitySheet.kt`

**Spec ref:** §5.3.4。

- [ ] **Step 1: 创建 SensitivitySheet.kt**

```kotlin
package com.tkpang.tvstriptest.ui.wizard.step2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tkpang.tvstriptest.model.SensitivityLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensitivitySheet(
    current: SensitivityLevel,
    onChange: (SensitivityLevel) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Text("配对灵敏度", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "越严格 = 设备要凑得越近才会自动配上",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF64748B),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFE2E8F0))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SensitivityLevel.values().forEach { level ->
                    val active = level == current
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (active) Color(0xFF2563EB) else Color.Transparent)
                            .clickable { onChange(level) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            level.displayName,
                            color = if (active) Color.White else Color(0xFF475569),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("完成")
            }
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew compileDebugKotlin
```
Expected: SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tkpang/tvstriptest/ui/wizard/step2/SensitivitySheet.kt
git commit -m "feat: add SensitivitySheet bottom sheet"
```

---

## Task 16: LaunchScreen（启动页二选一）

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/ui/launch/LaunchScreen.kt`

**Spec ref:** §5.1 + mockup `step1-revised.html` 第 1 张图。

- [ ] **Step 1: 创建 LaunchScreen.kt**

```kotlin
package com.tkpang.tvstriptest.ui.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LaunchScreen(
    onTest: () -> Unit,
    onWritePid: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF1F5F9)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Text("工厂测试 App", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("请选择要进入的功能", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF64748B))
        Spacer(Modifier.height(20.dp))

        ModeCard(
            icon = "📡",
            iconBg = Color(0xFFDBEAFE),
            title = "测试设备",
            tag = "主功能",
            tagBg = Color(0xFF2563EB),
            subtitle = "扫描 → 配对 → 颜色测试 → 解绑",
            highlighted = true,
            onClick = onTest,
        )
        ModeCard(
            icon = "✏️",
            iconBg = Color(0xFFFEF3C7),
            title = "写 PID（工厂出厂用）",
            subtitle = "单台扫描连接 → 写入指定 PID",
            highlighted = false,
            onClick = onWritePid,
        )
    }
}

@Composable
private fun ModeCard(
    icon: String,
    iconBg: Color,
    title: String,
    subtitle: String,
    highlighted: Boolean,
    tag: String? = null,
    tagBg: Color = Color.Gray,
    onClick: () -> Unit,
) {
    val borderColor = if (highlighted) Color(0xFF2563EB) else Color.Transparent
    val cardBg = if (highlighted) Color(0xFFEFF6FF) else Color.White
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (tag != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(4.dp)).background(tagBg).padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(tag, color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(subtitle, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
        }
    }
}
```

- [ ] **Step 2: Build + Commit**

```bash
./gradlew compileDebugKotlin
git add app/src/main/java/com/tkpang/tvstriptest/ui/launch/LaunchScreen.kt
git commit -m "feat: add LaunchScreen with two-mode entry"
```

---

## Task 17: Step1ProductScreen

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/ui/wizard/step1/Step1ProductScreen.kt`

**Spec ref:** §5.2 + mockup `step1-revised.html` 第 2 张图。

- [ ] **Step 1: 创建 Step1ProductScreen.kt**

```kotlin
package com.tkpang.tvstriptest.ui.wizard.step1

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tkpang.tvstriptest.model.PidFilter
import com.tkpang.tvstriptest.model.PidOption
import com.tkpang.tvstriptest.model.ProductCatalog

@Composable
fun Step1ProductScreen(
    selectedProductDevName: String,
    pidFilter: PidFilter,
    onlyUnbonded: Boolean,
    onProductChange: (String) -> Unit,
    onPidFilterChange: (PidFilter) -> Unit,
    onOnlyUnbondedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("产品类型")
        ProductGrid(selectedProductDevName, onProductChange)

        SectionLabel("PID 筛选")
        PidList(pidFilter = pidFilter, onChange = onPidFilterChange)

        SectionLabel("其他条件")
        OnlyUnbondedSwitch(onlyUnbonded, onOnlyUnbondedChange)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF475569),
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun ProductGrid(selected: String, onChange: (String) -> Unit) {
    val products = ProductCatalog.productTypes
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        products.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p ->
                    val supported = p.pidOptions.isNotEmpty()
                    val isSelected = p.devName == selected
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = supported) { onChange(p.devName) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFFEFF6FF) else Color.White,
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 2.dp,
                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFFE5E7EB),
                        ),
                    ) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(p.devName, fontWeight = FontWeight.Bold)
                            Text(
                                if (supported) p.displayName else "暂未支持",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (supported) Color(0xFF64748B) else Color(0xFFCBD5E1),
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PidList(pidFilter: PidFilter, onChange: (PidFilter) -> Unit) {
    val product = ProductCatalog.productTypes.first { it.devName == "STV1" }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column {
            PidRow(
                label = "不筛选 PID（任意）",
                num = "—",
                selected = pidFilter is PidFilter.Any,
                onClick = { onChange(PidFilter.Any) },
                special = true,
            )
            product.pidOptions.forEach { option ->
                PidRow(
                    label = option.displayName,
                    num = option.pid.toString(),
                    selected = pidFilter is PidFilter.Specific && pidFilter.pid == option.pid,
                    onClick = { onChange(PidFilter.Specific(option.pid)) },
                )
            }
        }
    }
}

@Composable
private fun PidRow(label: String, num: String, selected: Boolean, onClick: () -> Unit, special: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(6.dp))
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (special) FontWeight.Medium else FontWeight.Normal)
        Text(num, color = Color(0xFF64748B), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun OnlyUnbondedSwitch(value: Boolean, onChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("只显示未绑定的设备", fontWeight = FontWeight.SemiBold)
                Text(
                    "过滤掉已被别人配过的灯带",
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Switch(checked = value, onCheckedChange = onChange)
        }
    }
}
```

- [ ] **Step 2: Build + Commit**

```bash
./gradlew compileDebugKotlin
git add app/src/main/java/com/tkpang/tvstriptest/ui/wizard/step1/Step1ProductScreen.kt
git commit -m "feat: add Step1ProductScreen with PID filter and unbonded switch"
```

---

## Task 18: Step2ScanScreen（雷达页主壳）

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/ui/wizard/step2/Step2ScanScreen.kt`

**Spec ref:** §5.3 + mockup `radar-page.html`。

> Step2ScanScreen 是壳子：顶部计数 chip + ⚙ 按钮、中间嵌入 PulseRadar、底部状态文字。错误条 / 5 秒空状态等也在此。

- [ ] **Step 1: 创建 Step2ScanScreen.kt**

```kotlin
package com.tkpang.tvstriptest.ui.wizard.step2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tkpang.tvstriptest.factory.FactoryUiState
import com.tkpang.tvstriptest.model.ErrorBanner

@Composable
fun Step2ScanScreen(
    state: FactoryUiState,
    onSensitivityChange: (com.tkpang.tvstriptest.model.SensitivityLevel) -> Unit,
    onOpenBluetooth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheetOpen by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Top row: 计数 + 提示 + ⚙
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF2563EB))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text("已配 ${state.pairedDevices.size}", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                state.pairingMessage ?: "附近 ${state.visibleDevices.size} 台",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF64748B),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { sheetOpen = true }) {
                Text("⚙", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (state.errorBanner != null) {
            ErrorBannerView(state.errorBanner, onOpenBluetooth)
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (state.errorBanner == null && state.visibleDevices.isEmpty()) {
                EmptyScanState()
            } else {
                PulseRadar(
                    devices = state.visibleDevices,
                    sensitivity = state.settings.sensitivity,
                    pairingStates = emptyMap(),  // 让 device.pairingState 自带；MVP 暂不映射
                )
            }
        }

        Text(
            text = if (state.pairedDevices.isEmpty()) "把灯带凑近手机，进入蓝圈自动配对"
                   else "已配 ${state.pairedDevices.size} 台 · 把下一台凑近",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF64748B),
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }

    if (sheetOpen) {
        SensitivitySheet(
            current = state.settings.sensitivity,
            onChange = onSensitivityChange,
            onDismiss = { sheetOpen = false },
        )
    }
}

@Composable
private fun ErrorBannerView(banner: ErrorBanner, onOpen: () -> Unit) {
    val (msg, btn) = when (banner) {
        is ErrorBanner.BluetoothOff -> "📵 蓝牙未打开" to "打开蓝牙"
        is ErrorBanner.MissingPermission -> "需要蓝牙权限" to "去授权"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFEF2F2))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(msg, color = Color(0xFF991B1B), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))) {
            Text(btn)
        }
    }
}

@Composable
private fun EmptyScanState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🔍", style = MaterialTheme.typography.displayMedium)
        Text("还没扫到设备", color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
        Text(
            "确认设备已通电、距离 ≤ 5 米",
            color = Color(0xFFCBD5E1),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
```

- [ ] **Step 2: Build + Commit**

```bash
./gradlew compileDebugKotlin
git add app/src/main/java/com/tkpang/tvstriptest/ui/wizard/step2/Step2ScanScreen.kt
git commit -m "feat: add Step2ScanScreen with radar + sensitivity sheet"
```

---

## Task 19: Step3ColorScreen（6 色 + 最大功率 tab）

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/ui/wizard/step3/Step3ColorScreen.kt`

**Spec ref:** §5.4 + mockup `steps.html` Step 3。

- [ ] **Step 1: 创建 Step3ColorScreen.kt**

```kotlin
package com.tkpang.tvstriptest.ui.wizard.step3

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tkpang.tvstriptest.factory.ColorTestUseCase
import com.tkpang.tvstriptest.factory.FactoryUiState

@Composable
fun Step3ColorScreen(
    state: FactoryUiState,
    onCommand: (ColorTestUseCase.Command) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tabIndex by remember { mutableStateOf(0) }

    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusBar(state.pairedDevices.size)

        TabRow(selectedTabIndex = tabIndex) {
            Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }) { Text("普通设灯", Modifier.padding(12.dp)) }
            Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }) { Text("最高亮度", Modifier.padding(12.dp)) }
        }

        if (tabIndex == 0) {
            ColorGrid(onCommand)
        } else {
            MaxPowerSection(onCommand)
        }

        FeedbackBar(state.colorTestResult)
    }
}

@Composable
private fun StatusBar(count: Int) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFECFDF5)).padding(10.dp)
    ) {
        Text("已配 $count 台", color = Color(0xFF065F46), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("全部 Ready ✓", color = Color(0xFF065F46))
    }
}

@Composable
private fun ColorGrid(onCommand: (ColorTestUseCase.Command) -> Unit) {
    val items = listOf(
        Triple("红", Color(0xFFEF4444), ColorTestUseCase.Command.RED),
        Triple("绿", Color(0xFF22C55E), ColorTestUseCase.Command.GREEN),
        Triple("蓝", Color(0xFF3B82F6), ColorTestUseCase.Command.BLUE),
        Triple("白", Color(0xFFF8FAFC), ColorTestUseCase.Command.WHITE),
        Triple("黑", Color(0xFF1E293B), ColorTestUseCase.Command.BLACK),
        Triple("关", Color(0xFFE5E7EB), ColorTestUseCase.Command.OFF),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, color, cmd) ->
                    val text = if (label == "白" || label == "关") Color(0xFF0F172A) else Color.White
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onCommand(cmd) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun MaxPowerSection(onCommand: (ColorTestUseCase.Command) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = { onCommand(ColorTestUseCase.Command.MAX_POWER) },
            modifier = Modifier.fillMaxWidth().height(68.dp),
        ) {
            Text("触发最大功率", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text(
            "用于 EMC 最大功耗测试",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF64748B),
        )
    }
}

@Composable
private fun FeedbackBar(result: ColorTestUseCase.Result?) {
    if (result == null) return
    val ok = result.success
    val fail = result.failed.size
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White)
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp)).padding(10.dp)
    ) {
        Text("✓ 已下发：$ok 台", color = Color(0xFF15803D), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (fail > 0) {
            Text("失败 $fail 台", color = Color(0xFFDC2626))
        }
    }
}
```

- [ ] **Step 2: Build + Commit**

```bash
./gradlew compileDebugKotlin
git add app/src/main/java/com/tkpang/tvstriptest/ui/wizard/step3/Step3ColorScreen.kt
git commit -m "feat: add Step3ColorScreen with 6 colors + max power tab"
```

---

## Task 20: Step4UnbindScreen

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/ui/wizard/step4/Step4UnbindScreen.kt`

**Spec ref:** §5.5 + mockup `steps.html` Step 4。

- [ ] **Step 1: 创建 Step4UnbindScreen.kt**

```kotlin
package com.tkpang.tvstriptest.ui.wizard.step4

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tkpang.tvstriptest.factory.FactoryUiState

@Composable
fun Step4UnbindScreen(
    state: FactoryUiState,
    onUnbind: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by remember { mutableStateOf(false) }
    val progress = state.unbindProgress

    Column(
        modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(70.dp).clip(CircleShape).background(Color(0xFFFEF2F2)),
            contentAlignment = Alignment.Center,
        ) {
            Text("🗑", style = MaterialTheme.typography.headlineMedium)
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "测试完成，把这 ${state.pairedDevices.size} 台设备\n从 App 上一键解绑删除",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(Modifier.height(20.dp))

        when {
            progress != null && progress.completed < progress.total -> {
                Text("正在解绑 ${progress.completed} / ${progress.total}", fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(
                    progress = { progress.completed.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            progress != null && progress.completed == progress.total -> {
                Text(
                    "✓ 全部解绑成功（失败 ${progress.failed.size} 台）",
                    color = Color(0xFF15803D),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
                    Text("完成 · 重新开始测试")
                }
            }
            else -> {
                Button(
                    onClick = { confirming = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text("全部解绑并删除", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "解绑后设备会回到出厂未配状态，下次扫描可以重新配",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("确认解绑？") },
            text = { Text("将对 ${state.pairedDevices.size} 台设备发送解绑命令，无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onUnbind()
                }) { Text("确认解绑", color = Color(0xFFDC2626)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("取消") } },
        )
    }
}
```

- [ ] **Step 2: Build + Commit**

```bash
./gradlew compileDebugKotlin
git add app/src/main/java/com/tkpang/tvstriptest/ui/wizard/step4/Step4UnbindScreen.kt
git commit -m "feat: add Step4UnbindScreen with confirmation and progress"
```

---

## Task 21: WritePidScreen + WritePidViewModel

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/factory/WritePidViewModel.kt`
- Create: `app/src/main/java/com/tkpang/tvstriptest/ui/writepid/WritePidScreen.kt`

**Spec ref:** §5.6。

> 写 PID 工具是独立 route，自有 VM；不复用 FactoryViewModel 的状态。本 task 给出最小可用版（选 PID + 开始写入 + 成功反馈），现场再补错误细节。

- [ ] **Step 1: 创建 WritePidViewModel.kt**

```kotlin
package com.tkpang.tvstriptest.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkpang.tvstriptest.ble.BleScanner
import com.tkpang.tvstriptest.model.FactoryDevice
import com.tkpang.tvstriptest.model.ScanDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WritePidViewModel(
    private val scanner: BleScanner,
    private val dispatcher: CommandDispatcher,
) : ViewModel() {

    sealed interface Phase {
        data object Idle : Phase
        data object Scanning : Phase
        data class Writing(val device: ScanDevice) : Phase
        data class Success(val pid: Int) : Phase
        data class Failed(val message: String) : Phase
    }

    private val _selectedPid = MutableStateFlow<Int?>(null)
    val selectedPid = _selectedPid.asStateFlow()

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase = _phase.asStateFlow()

    fun selectPid(pid: Int) { _selectedPid.value = pid }

    fun start() {
        val pid = _selectedPid.value ?: return
        viewModelScope.launch { runFlow(pid) }
    }

    private suspend fun runFlow(pid: Int) {
        _phase.value = Phase.Scanning
        scanner.start()
        try {
            val target = waitForStrongest(timeoutMillis = 5000) ?: run {
                _phase.value = Phase.Failed("请把 1 台设备靠近后重试")
                return
            }
            _phase.value = Phase.Writing(target)
            val device = FactoryDevice(target.address, target.name)
            val connected = dispatcher.connect(device)
            if (!connected.success) {
                _phase.value = Phase.Failed("连接失败：${connected.message}")
                return
            }
            val written = dispatcher.setPid(device, pid)
            if (!written.success) {
                _phase.value = Phase.Failed("写入失败：${written.message}")
                return
            }
            // 重读校验：再次 connect 获取 deviceInfo（内部会读 DEV_INFO_GETR）
            val verify = dispatcher.connect(device)
            val ok = verify.success && verify.deviceInfo?.pid == pid
            _phase.value = if (ok) Phase.Success(pid) else Phase.Failed("校验失败")
        } finally {
            scanner.stop()
            dispatcher.closeAll()
        }
    }

    private suspend fun waitForStrongest(timeoutMillis: Long): ScanDevice? {
        var elapsed = 0L
        while (elapsed < timeoutMillis) {
            val devices = scanner.devices.value
            if (devices.isNotEmpty()) {
                return devices.maxByOrNull { it.rssi }
            }
            delay(200)
            elapsed += 200
        }
        return null
    }

    fun reset() { _phase.value = Phase.Idle }
}
```

- [ ] **Step 2: 创建 WritePidScreen.kt**

```kotlin
package com.tkpang.tvstriptest.ui.writepid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkpang.tvstriptest.factory.WritePidViewModel
import com.tkpang.tvstriptest.model.ProductCatalog

@Composable
fun WritePidScreen(
    onBack: () -> Unit,
    vm: WritePidViewModel = viewModel(),
) {
    val pid by vm.selectedPid.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    val options = ProductCatalog.productTypes.first { it.devName == "STV1" }.pidOptions

    Column(Modifier.fillMaxSize().background(Color(0xFFF8FAFC)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Spacer(Modifier.weight(1f))
            Text("写 PID 工具", fontWeight = FontWeight.Bold)
        }

        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFFEF3C7)).padding(10.dp)
        ) {
            Text(
                "⚠ 仅供工厂出厂线使用 · 一次只配一台",
                color = Color(0xFF92400E),
                fontWeight = FontWeight.SemiBold,
            )
        }

        Text("选择要写入的 PID", fontWeight = FontWeight.Bold)
        Card {
            Column {
                options.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = pid == option.pid, onClick = { vm.selectPid(option.pid) })
                        Text(option.displayName, modifier = Modifier.weight(1f))
                        Text("${option.pid}", color = Color(0xFF64748B))
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        when (val p = phase) {
            is WritePidViewModel.Phase.Idle -> Button(
                onClick = vm::start,
                enabled = pid != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("开始写入") }
            is WritePidViewModel.Phase.Scanning -> Text("正在扫描设备…")
            is WritePidViewModel.Phase.Writing -> Text("正在写入到 ${p.device.address}")
            is WritePidViewModel.Phase.Success -> Column {
                Text("✓ PID 已写入：${p.pid}", color = Color(0xFF15803D), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Button(onClick = vm::reset, modifier = Modifier.fillMaxWidth()) { Text("再写一台") }
            }
            is WritePidViewModel.Phase.Failed -> Column {
                Text("✗ ${p.message}", color = Color(0xFFDC2626))
                Spacer(Modifier.height(8.dp))
                Button(onClick = vm::reset, modifier = Modifier.fillMaxWidth()) { Text("重试") }
            }
        }
    }
}
```

- [ ] **Step 3: Build + Commit**

```bash
./gradlew compileDebugKotlin
git add app/src/main/java/com/tkpang/tvstriptest/factory/WritePidViewModel.kt \
        app/src/main/java/com/tkpang/tvstriptest/ui/writepid/WritePidScreen.kt
git commit -m "feat: add WritePidScreen and WritePidViewModel"
```

---

## Task 22: FactoryWizardScreen 容器 + MainActivity NavHost

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/ui/wizard/FactoryWizardScreen.kt`
- Modify: `app/src/main/java/com/tkpang/tvstriptest/MainActivity.kt`
- Delete: `app/src/main/java/com/tkpang/tvstriptest/ui/FactoryScreen.kt`

**Spec ref:** §4.1, §4.2。

- [ ] **Step 1: 创建 FactoryWizardScreen.kt**

```kotlin
package com.tkpang.tvstriptest.ui.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkpang.tvstriptest.factory.FactoryViewModel
import com.tkpang.tvstriptest.model.WizardStep
import com.tkpang.tvstriptest.ui.wizard.components.BottomNavBar
import com.tkpang.tvstriptest.ui.wizard.components.StepIndicator
import com.tkpang.tvstriptest.ui.wizard.step1.Step1ProductScreen
import com.tkpang.tvstriptest.ui.wizard.step2.Step2ScanScreen
import com.tkpang.tvstriptest.ui.wizard.step3.Step3ColorScreen
import com.tkpang.tvstriptest.ui.wizard.step4.Step4UnbindScreen

@Composable
fun FactoryWizardScreen(
    onExit: () -> Unit,
    vm: FactoryViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Column(Modifier.background(Color.White).padding(12.dp)) {
                StepIndicator(currentStep = state.step)
            }
        },
        bottomBar = {
            BottomNavBar(
                onBack = if (state.step.index > 0) { { vm.back() } } else null,
                onNext = nextLambda(state.step, vm, onExit),
                nextLabel = if (state.step == WizardStep.UNBIND) "完成" else "下一步 →",
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(Color(0xFFF1F5F9))) {
            when (state.step) {
                WizardStep.PRODUCT -> Step1ProductScreen(
                    selectedProductDevName = state.settings.productDevName,
                    pidFilter = state.settings.pidFilter,
                    onlyUnbonded = state.settings.onlyUnbonded,
                    onProductChange = vm::setProductType,
                    onPidFilterChange = vm::setPidFilter,
                    onOnlyUnbondedChange = vm::setOnlyUnbonded,
                )
                WizardStep.SCAN -> {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Step2ScanScreen(
                        state = state,
                        onSensitivityChange = vm::setSensitivity,
                        onOpenBluetooth = {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                    )
                }
                WizardStep.COLOR -> Step3ColorScreen(
                    state = state,
                    onCommand = vm::runColorTest,
                )
                WizardStep.UNBIND -> Step4UnbindScreen(
                    state = state,
                    onUnbind = vm::runUnbind,
                    onRestart = vm::resetToFirstStep,
                )
            }
        }
    }
}

private fun nextLambda(
    step: WizardStep,
    vm: FactoryViewModel,
    onExit: () -> Unit,
): (() -> Unit)? = when (step) {
    WizardStep.PRODUCT -> { -> vm.next() }
    WizardStep.SCAN -> { -> vm.next() }
    WizardStep.COLOR -> { -> vm.next() }
    WizardStep.UNBIND -> { -> onExit() }
}
```

- [ ] **Step 2: 重写 MainActivity.kt（保留现有 BLE 工厂 + 权限申请逻辑）**

```kotlin
package com.tkpang.tvstriptest

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkpang.tvstriptest.ble.BleDeviceSession
import com.tkpang.tvstriptest.ble.BleScanner
import com.tkpang.tvstriptest.factory.*
import com.tkpang.tvstriptest.protocol.BleDeviceInfo
import com.tkpang.tvstriptest.protocol.LeMessageCodec
import com.tkpang.tvstriptest.ui.launch.LaunchScreen
import com.tkpang.tvstriptest.ui.wizard.FactoryWizardScreen
import com.tkpang.tvstriptest.ui.writepid.WritePidScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    private lateinit var scanner: BleScanner
    private lateinit var dispatcher: CommandDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestFactoryPermissions()
        scanner = BleScanner(this)
        dispatcher = PerDeviceBleDispatcher { device ->
            AndroidDeviceCommandSession.create(this, device.address)
        }

        setContent {
            MaterialTheme {
                Surface {
                    var route by remember { mutableStateOf("launch") }
                    when (route) {
                        "launch"   -> LaunchScreen(
                            onTest     = { route = "test" },
                            onWritePid = { route = "writepid" },
                        )
                        "test"     -> FactoryWizardScreen(
                            vm = viewModel(factory = factoryViewModelFactory()),
                            onExit = { route = "launch" },
                        )
                        "writepid" -> WritePidScreen(
                            vm = viewModel(factory = writePidViewModelFactory()),
                            onBack = { route = "launch" },
                        )
                    }
                }
            }
        }
    }

    private fun factoryViewModelFactory() = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FactoryViewModel(scanner, dispatcher) as T
    }

    private fun writePidViewModelFactory() = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WritePidViewModel(scanner, dispatcher) as T
    }

    private fun requestFactoryPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), REQUEST_FACTORY_PERMISSIONS)
        }
    }

    private companion object {
        const val REQUEST_FACTORY_PERMISSIONS = 1001
    }
}

// 保留现有的 AndroidDeviceCommandSession（从原 MainActivity 抠出来，可考虑搬到 ble/AndroidBleSession.kt 单独文件）
private class AndroidDeviceCommandSession(
    context: Context,
    device: android.bluetooth.BluetoothDevice,
) : DeviceCommandSession {
    private val session = BleDeviceSession(context = context, device = device)
    override val deviceInfo: BleDeviceInfo? get() = session.deviceInfo

    @Suppress("MissingPermission")
    override suspend fun connect(): Boolean {
        if (!session.connect()) return false
        val ready = withTimeoutOrNull(10_000) {
            session.state.first { state ->
                state == BleDeviceSession.State.Ready ||
                    state == BleDeviceSession.State.Failed ||
                    state == BleDeviceSession.State.Closed
            } == BleDeviceSession.State.Ready
        } ?: false
        return ready && session.handshake()
    }

    @Suppress("MissingPermission")
    override suspend fun write(payload: ByteArray): Boolean {
        val message = LeMessageCodec.decode(payload)
        return session.transact(message.cmd, message.sn, message.payload, expectedResponseCmd = message.cmd + 1)
    }

    @Suppress("MissingPermission")
    override suspend fun transact(payload: ByteArray, expectedResponseCmd: Int, timeoutMillis: Long): Boolean {
        val message = LeMessageCodec.decode(payload)
        return session.transact(message.cmd, message.sn, message.payload, expectedResponseCmd)
    }

    @Suppress("MissingPermission")
    override suspend fun close() = session.close()

    companion object {
        fun create(context: Context, address: String): DeviceCommandSession? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                return PermissionDeniedSession("BLUETOOTH_CONNECT permission missing")
            }
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
            return AndroidDeviceCommandSession(context, adapter.getRemoteDevice(address))
        }
    }
}

private class PermissionDeniedSession(
    override val connectFailureMessage: String,
) : DeviceCommandSession {
    override suspend fun connect(): Boolean = false
    override suspend fun write(payload: ByteArray): Boolean = false
    override suspend fun close() = Unit
}
```

> 同时改 `FactoryWizardScreen` 和 `WritePidScreen` 签名接受 `vm: ...` 显式参数（之前 plan 写的就是支持）。`AndroidDeviceCommandSession` / `PermissionDeniedSession` 也可以提取到单独 `ble/AndroidBleBridge.kt` 文件，但保留在 MainActivity.kt 也行。

- [ ] **Step 3: 删除老 FactoryScreen.kt**

```bash
rm app/src/main/java/com/tkpang/tvstriptest/ui/FactoryScreen.kt
```

- [ ] **Step 4: Build + assembleDebug**

```bash
./gradlew compileDebugKotlin
./gradlew test
./gradlew assembleDebug
```
Expected: SUCCESS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tkpang/tvstriptest/ui/wizard/FactoryWizardScreen.kt \
        app/src/main/java/com/tkpang/tvstriptest/MainActivity.kt
git rm app/src/main/java/com/tkpang/tvstriptest/ui/FactoryScreen.kt
git commit -m "feat: wire wizard + launch + writepid screens via simple route state"
```

---

## Task 23: 重写 README

**Files:**
- Modify: `README.md`

**Spec ref:** §10。

- [ ] **Step 1: 重写 README**

替换为：

```markdown
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
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: rewrite README for radar redesign"
```

---

## Task 24: 验收 — 运行全部测试 + assembleDebug

**Files:** —

- [ ] **Step 1: 跑全部测试**

```bash
./gradlew clean test
```
Expected: 全部 PASS（AdvDataParser 8 + ProductCatalog 3 + DpCommands 8 + CommandDispatcher 多 + ScanFilterUseCase 6 + RadarStateMachine 4 + PairingExecutor 4 + ColorTestUseCase 4 + UnbindUseCase 2 + 现有 Crc16 / BleCrypto / AesCbc / LeMessageCodec 全部）。

- [ ] **Step 2: 跑 assembleDebug**

```bash
./gradlew assembleDebug
```
Expected: SUCCESS, 产出 `app/build/outputs/apk/debug/app-debug.apk`。

- [ ] **Step 3: 推到 GitHub 让 Actions 跑 CI**

```bash
git push origin main  # 或 feature branch
```
Expected: GitHub Actions 全绿。

- [ ] **Step 4: 手动现场测试 checklist**

按 spec §13 验收标准 1-9 走一遍：

1. 启动 → 测试设备 → 4 步全跑通 → 完成回 Step1
2. 选「线下 5 米 (144)」+ 凑近 1 台 3 米灯带 → 「PID 不符 · 跳过」
3. 选「不筛选」+ 凑近 5 台不同 PID 灯带 → 全部能配上
4. 关蓝牙 → 红条 + 「打开蓝牙」按钮工作
5. 灵敏度调到「紧贴」→ 中心圈最小，必须贴脸才配上
6. 颜色测试 6 个按钮 + 最高亮度按钮全部下发成功
7. 解绑后所有设备回到未绑定状态（重新扫描可见）
8. 「写 PID」模式：选「线上 5m」→ 凑近 1 台 → 写入成功
9. 全 app 无英文残留（README 不算）

> 第 10、11 项（覆盖率 + CI）已经在 step 1-3 验证。

- [ ] **Step 5: Final commit（如果在 step 4 改了任何 bug）**

各 bug fix 单独 commit，不在此 task 范围。

---

## 完成

总计 **24 个 task**，覆盖 spec 全部章节：

- Task 1：§3.1 AdvDataParser
- Task 2-3：§6, §7.9 基础类型 + ProductCatalog 中文
- Task 4-5：§3.2, §7.8 DpCommands + CommandDispatcher 简化
- Task 6：§7.2 BleScanner
- Task 7-11：§7.3-§7.7 use cases
- Task 12：§4.1, §6.2 ViewModel 重写
- Task 13-15：§4.2, §5.3 UI 共用组件 + PulseRadar + 抽屉
- Task 16-21：§5.1-§5.6 5 个屏幕
- Task 22：wizard 容器 + NavHost + 删除老 FactoryScreen
- Task 23：§10 README
- Task 24：§13 验收
