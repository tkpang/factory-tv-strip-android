# Android 工厂测试 App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task by default. Only use superpowers:executing-plans when the user explicitly requests inline execution. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建一个独立 Android Kotlin/Compose 工厂测试 App，可通过 GitHub Actions 编译 APK，支持 BLE 扫描、按 RSSI 自动勾选、批量连接、设灯、最高亮度颜色测试和一键解绑。

**Architecture:** 使用单 Activity Compose App。核心逻辑按 `model`、`protocol`、`ble`、`factory`、`ui` 拆分；第一版用逐设备 BLE 下发，保留 `CommandDispatcher` 抽象以便后续接 group 方案。协议层先实现 `le_msg` 帧、CRC、AES-CBC、DP JSON 和 STV1 设灯/解绑所需命令。

**Tech Stack:** Kotlin, Android Gradle Plugin, Jetpack Compose, Kotlin Coroutines/Flow, Android BLE GATT API, JUnit, GitHub Actions.

---

## 文件结构

- Create: `settings.gradle.kts`，Gradle 项目入口。
- Create: `build.gradle.kts`，根工程插件版本。
- Create: `gradle.properties`，AndroidX/Compose/Kotlin 配置。
- Create: `app/build.gradle.kts`，Android App 模块配置。
- Create: `app/src/main/AndroidManifest.xml`，权限和 MainActivity 声明。
- Create: `app/src/main/java/com/tkpang/tvstriptest/MainActivity.kt`，Compose Activity。
- Create: `app/src/main/java/com/tkpang/tvstriptest/model/ProductCatalog.kt`，产品类型和 PID/SKU 目录。
- Create: `app/src/main/java/com/tkpang/tvstriptest/model/FactoryModels.kt`，设备、扫描、连接、命令状态模型。
- Create: `app/src/main/java/com/tkpang/tvstriptest/protocol/LeConstants.kt`，UUID、命令字、DP 常量。
- Create: `app/src/main/java/com/tkpang/tvstriptest/protocol/Crc16.kt`，CRC16 工具。
- Create: `app/src/main/java/com/tkpang/tvstriptest/protocol/AesCbc.kt`，AES-CBC 工具。
- Create: `app/src/main/java/com/tkpang/tvstriptest/protocol/LeMessageCodec.kt`，`le_msg` 帧封包/解包。
- Create: `app/src/main/java/com/tkpang/tvstriptest/protocol/DpCommands.kt`，DP JSON 和 groove 指令构造。
- Create: `app/src/main/java/com/tkpang/tvstriptest/ble/BleScanner.kt`，BLE 扫描和 RSSI 自动勾选。
- Create: `app/src/main/java/com/tkpang/tvstriptest/ble/BleDeviceSession.kt`，单设备 GATT 连接和命令收发。
- Create: `app/src/main/java/com/tkpang/tvstriptest/factory/CommandDispatcher.kt`，逐设备命令下发抽象和实现。
- Create: `app/src/main/java/com/tkpang/tvstriptest/factory/FactoryViewModel.kt`，页面状态和业务编排。
- Create: `app/src/main/java/com/tkpang/tvstriptest/ui/FactoryScreen.kt`，主界面。
- Create: `app/src/test/java/com/tkpang/tvstriptest/model/ProductCatalogTest.kt`。
- Create: `app/src/test/java/com/tkpang/tvstriptest/protocol/Crc16Test.kt`。
- Create: `app/src/test/java/com/tkpang/tvstriptest/protocol/LeMessageCodecTest.kt`。
- Create: `app/src/test/java/com/tkpang/tvstriptest/protocol/DpCommandsTest.kt`。
- Create: `.github/workflows/android-build.yml`，GitHub 编译 APK。
- Create: `README.md`，中文说明。

## Task 1: Android 工程骨架和 GitHub Actions

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/tkpang/tvstriptest/MainActivity.kt`
- Create: `.github/workflows/android-build.yml`
- Create: `README.md`

- [ ] **Step 1: 写入 Gradle 项目配置**

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FactoryTvStripAndroid"
include(":app")
```

`build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 2: 写入 App 模块配置**

`app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tkpang.tvstriptest"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tkpang.tvstriptest"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

- [ ] **Step 3: 写入 AndroidManifest 和入口 Activity**

`app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

    <uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />

    <application
        android:allowBackup="false"
        android:label="TV Strip Factory Test"
        android:supportsRtl="true"
        android:theme="@style/Theme.FactoryTvStrip">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/java/com/tkpang/tvstriptest/MainActivity.kt`:

```kotlin
package com.tkpang.tvstriptest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Text("TV Strip Factory Test")
                }
            }
        }
    }
}
```

- [ ] **Step 4: 写入 GitHub Actions 和 README**

`.github/workflows/android-build.yml`:

```yaml
name: Android Build

on:
  push:
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
      - name: Build debug APK
        run: ./gradlew test assembleDebug
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: factory-tv-strip-debug-apk
          path: app/build/outputs/apk/debug/*.apk
```

`README.md`:

```markdown
# TV Strip Factory Test Android App

工厂专用 Android 测试 App。App 通过 BLE 扫描和连接设备，支持按 RSSI 自动勾选、批量连接、设灯、最高亮度颜色测试和一键解绑。

## 构建

本项目不要求本地 Android 编译。推送到 GitHub 后由 GitHub Actions 执行：

```bash
./gradlew test assembleDebug
```

APK 产物在 GitHub Actions artifact 中下载。
```

- [ ] **Step 5: 验证工程骨架**

Run: `./gradlew test assembleDebug`

Expected: Gradle 编译成功，生成 `app/build/outputs/apk/debug/app-debug.apk`。

## Task 2: 产品目录和基础模型

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/model/ProductCatalog.kt`
- Create: `app/src/main/java/com/tkpang/tvstriptest/model/FactoryModels.kt`
- Create: `app/src/test/java/com/tkpang/tvstriptest/model/ProductCatalogTest.kt`

- [ ] **Step 1: 写产品目录测试**

`ProductCatalogTest.kt`:

```kotlin
package com.tkpang.tvstriptest.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductCatalogTest {
    @Test
    fun stv1IsProductTypeAndPidsAreSkuOptions() {
        val stv1 = ProductCatalog.productTypes.first { it.devName == "STV1" }
        assertEquals("STV1-摄像头灯带", stv1.displayName)
        assertTrue(stv1.pidOptions.map { it.pid }.containsAll(listOf(111, 112, 143, 144, 145, 158)))
    }

    @Test
    fun ledCountForStv1Pid() {
        assertEquals(36, ProductCatalog.requireLedCount("STV1", 111))
        assertEquals(50, ProductCatalog.requireLedCount("STV1", 112))
        assertEquals(68, ProductCatalog.requireLedCount("STV1", 144))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*.ProductCatalogTest"`

Expected: FAIL because `ProductCatalog` is not defined.

- [ ] **Step 3: 实现产品目录和模型**

`ProductCatalog.kt`:

```kotlin
package com.tkpang.tvstriptest.model

data class PidOption(
    val pid: Int,
    val displayName: String,
    val ledCount: Int?,
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
            displayName = "STV1-摄像头灯带",
            pidOptions = listOf(
                PidOption(111, "STV1 Online 3M", 36),
                PidOption(112, "STV1 Online 5M", 50),
                PidOption(143, "STV1 Offline 3M", 48),
                PidOption(144, "STV1 Offline 5M", 68),
                PidOption(145, "STV1 Offline 3M High Density", 72),
                PidOption(158, "STV1 Online 2M", 24),
            ),
        ),
        ProductType("S2", "S2-RGBCW灯带", emptyList()),
        ProductType("SW1", "SW1-防水灯带", emptyList()),
        ProductType("S1_V2", "S1-V2-灯带", emptyList()),
    )

    fun requireLedCount(devName: String, pid: Int): Int {
        val product = productTypes.firstOrNull { it.devName == devName }
            ?: error("Unknown product type: $devName")
        val option = product.pidOptions.firstOrNull { it.pid == pid }
            ?: error("Unknown PID $pid for $devName")
        return option.ledCount ?: error("No LED count for $devName/$pid")
    }
}
```

`FactoryModels.kt`:

```kotlin
package com.tkpang.tvstriptest.model

enum class DeviceConnectionState { Discovered, Connecting, Connected, Ready, Failed, Unbound }

data class ScanDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val autoSelected: Boolean,
    val selected: Boolean,
)

data class FactoryDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val state: DeviceConnectionState,
    val did: Long? = null,
    val pid: Int? = null,
    val firmwareVersion: String? = null,
    val lastResult: String = "",
    val lastError: String = "",
)

data class FactorySettings(
    val productDevName: String = "STV1",
    val pid: Int = 111,
    val targetDeviceCount: Int = 1,
    val rssiThreshold: Int = -65,
    val maxPowerColor: Int = 0xFFFFFF,
    val maxBrightness: Int = 1000,
)
```

- [ ] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest --tests "*.ProductCatalogTest"`

Expected: PASS.

## Task 3: 协议常量、CRC、AES 和 le_msg 编解码

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/protocol/LeConstants.kt`
- Create: `app/src/main/java/com/tkpang/tvstriptest/protocol/Crc16.kt`
- Create: `app/src/main/java/com/tkpang/tvstriptest/protocol/AesCbc.kt`
- Create: `app/src/main/java/com/tkpang/tvstriptest/protocol/LeMessageCodec.kt`
- Create: `app/src/test/java/com/tkpang/tvstriptest/protocol/Crc16Test.kt`
- Create: `app/src/test/java/com/tkpang/tvstriptest/protocol/LeMessageCodecTest.kt`

- [ ] **Step 1: 写协议测试**

`Crc16Test.kt`:

```kotlin
package com.tkpang.tvstriptest.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class Crc16Test {
    @Test
    fun crc16ModbusKnownVector() {
        assertEquals(0x4B37, Crc16.modbus("123456789".encodeToByteArray()))
    }
}
```

`LeMessageCodecTest.kt`:

```kotlin
package com.tkpang.tvstriptest.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LeMessageCodecTest {
    @Test
    fun encodesAndDecodesSingleFrame() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val frame = LeMessageCodec.encode(cmd = LeConstants.LE_CMD_UNBOND, sn = 7, payload = payload)
        val decoded = LeMessageCodec.decode(frame)
        assertEquals(LeConstants.LE_CMD_UNBOND, decoded.cmd)
        assertEquals(7, decoded.sn)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun buildsBondPayloadWithMark() {
        assertArrayEquals(byteArrayOf(0x5a, 0x5a, 0xa5.toByte(), 0xa5.toByte(), 0, 0, 0, 0), LeMessageCodec.bondPayload())
    }
}
```

- [ ] **Step 2: Run tests to verify fail**

Run: `./gradlew testDebugUnitTest --tests "*.Crc16Test" --tests "*.LeMessageCodecTest"`

Expected: FAIL because protocol classes are missing.

- [ ] **Step 3: 实现协议工具**

`LeConstants.kt`:

```kotlin
package com.tkpang.tvstriptest.protocol

import java.util.UUID

object LeConstants {
    val SERVICE_UUID: UUID = UUID.fromString("1e2aa501-7292-4263-a8f1-be907f039a1f")
    val WRITE_UUID: UUID = UUID.fromString("1e2aa502-7292-4263-a8f1-be907f039a1f")
    val NOTIFY_UUID: UUID = UUID.fromString("1e2aa503-7292-4263-a8f1-be907f039a1f")
    const val ADV_NAME = "LP"

    const val LE_CMD_DEV_INFO_GET = 0x1000
    const val LE_CMD_DEV_INFO_GETR = 0x1001
    const val LE_CMD_BOND = 0x1002
    const val LE_CMD_BONDR = 0x1003
    const val LE_CMD_UNBOND = 0x1004
    const val LE_CMD_UNBONDR = 0x1005
    const val LE_CMD_DP_PRP_SET = 0x1100
    const val LE_CMD_DEV_INFO_GET_MARK = 0x5a5aa5a5

    const val HDR_VER = 0x5a
    const val CTRL_NONE = 0x50
}
```

`Crc16.kt`:

```kotlin
package com.tkpang.tvstriptest.protocol

object Crc16 {
    fun modbus(data: ByteArray): Int {
        var crc = 0xFFFF
        for (byte in data) {
            crc = crc xor (byte.toInt() and 0xFF)
            repeat(8) {
                crc = if ((crc and 1) != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
            }
        }
        return crc and 0xFFFF
    }
}
```

`AesCbc.kt`:

```kotlin
package com.tkpang.tvstriptest.protocol

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesCbc {
    fun encrypt(key: ByteArray, iv: ByteArray, plain: ByteArray): ByteArray = crypt(Cipher.ENCRYPT_MODE, key, iv, plain)
    fun decrypt(key: ByteArray, iv: ByteArray, encrypted: ByteArray): ByteArray = crypt(Cipher.DECRYPT_MODE, key, iv, encrypted)

    private fun crypt(mode: Int, key: ByteArray, iv: ByteArray, input: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(input)
    }
}
```

`LeMessageCodec.kt`:

```kotlin
package com.tkpang.tvstriptest.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class LeMessage(val cmd: Int, val sn: Int, val payload: ByteArray)

object LeMessageCodec {
    fun encode(cmd: Int, sn: Int, payload: ByteArray): ByteArray {
        val withoutCrc = ByteBuffer.allocate(1 + 1 + 2 + 2 + 2 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            .put(LeConstants.HDR_VER.toByte())
            .put(LeConstants.CTRL_NONE.toByte())
            .putShort(sn.toShort())
            .putShort(cmd.toShort())
            .putShort(payload.size.toShort())
            .put(payload)
            .array()
        val crc = Crc16.modbus(withoutCrc)
        return ByteBuffer.allocate(2 + withoutCrc.size).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(crc.toShort())
            .put(withoutCrc)
            .array()
    }

    fun decode(frame: ByteArray): LeMessage {
        require(frame.size >= 10) { "Frame too short" }
        val expectedCrc = ByteBuffer.wrap(frame, 0, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        val actualCrc = Crc16.modbus(frame.copyOfRange(2, frame.size))
        require(expectedCrc == actualCrc) { "CRC mismatch expected=$expectedCrc actual=$actualCrc" }
        val buf = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)
        buf.short
        val ver = buf.get().toInt() and 0xFF
        require(ver == LeConstants.HDR_VER) { "Unexpected header version $ver" }
        buf.get()
        val sn = buf.short.toInt() and 0xFFFF
        val cmd = buf.short.toInt() and 0xFFFF
        val len = buf.short.toInt() and 0xFFFF
        require(frame.size == 10 + len) { "Unexpected payload length $len" }
        val payload = ByteArray(len)
        buf.get(payload)
        return LeMessage(cmd, sn, payload)
    }

    fun bondPayload(): ByteArray = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        .putInt(LeConstants.LE_CMD_DEV_INFO_GET_MARK)
        .putInt(0)
        .array()
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest --tests "*.Crc16Test" --tests "*.LeMessageCodecTest"`

Expected: PASS.

## Task 4: DP 和灯效指令构造

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/protocol/DpCommands.kt`
- Create: `app/src/test/java/com/tkpang/tvstriptest/protocol/DpCommandsTest.kt`

- [ ] **Step 1: 写 DP 测试**

`DpCommandsTest.kt`:

```kotlin
package com.tkpang.tvstriptest.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DpCommandsTest {
    @Test
    fun setPidJson() {
        assertEquals("{\"d158\":111}", DpCommands.setPid(111))
    }

    @Test
    fun maxBrightnessJson() {
        assertEquals("{\"d162\":1000}", DpCommands.setMaxBrightness(1000))
    }

    @Test
    fun solidGrooveCommandUsesLedCountAndRgb() {
        val cmd = DpCommands.solidColorGroove(ledCount = 2, rgb = 0x12ABEF)
        assertEquals("N01:P1000212abef12abef;", cmd)
    }

    @Test
    fun dpPropertySetWrapsGroove() {
        val json = DpCommands.grooveHandle("N01:P10001000000;")
        assertTrue(json.contains("\"d160\""))
        assertTrue(json.contains("N01:P10001000000;"))
    }
}
```

- [ ] **Step 2: Run test to verify fail**

Run: `./gradlew testDebugUnitTest --tests "*.DpCommandsTest"`

Expected: FAIL because `DpCommands` is missing.

- [ ] **Step 3: 实现 DP 命令构造**

`DpCommands.kt`:

```kotlin
package com.tkpang.tvstriptest.protocol

object DpCommands {
    fun setPid(pid: Int): String = "{\"d158\":$pid}"

    fun grooveState(enabled: Boolean): String = "{\"d161\":${if (enabled) 1 else 0}}"

    fun setMaxBrightness(value: Int): String = "{\"d162\":${value.coerceIn(0, 1000)}}"

    fun grooveHandle(groove: String): String = "{\"d160\":\"${escapeJson(groove)}\"}"

    fun solidColorGroove(ledCount: Int, rgb: Int): String {
        require(ledCount in 1..512) { "Invalid LED count: $ledCount" }
        val color = rgbHex(rgb)
        return "N01:P100${ledCount.toString(16).padStart(2, '0')}${color.repeat(ledCount)};"
    }

    fun highestPowerSequence(ledCount: Int, rgb: Int, brightness: Int): List<String> = listOf(
        grooveState(true),
        setMaxBrightness(brightness),
        grooveHandle(solidColorGroove(ledCount, rgb)),
    )

    private fun rgbHex(rgb: Int): String = (rgb and 0xFFFFFF).toString(16).padStart(6, '0')

    private fun escapeJson(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest --tests "*.DpCommandsTest"`

Expected: PASS.

## Task 5: BLE 扫描、自动勾选和逐设备会话骨架

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/ble/BleScanner.kt`
- Create: `app/src/main/java/com/tkpang/tvstriptest/ble/BleDeviceSession.kt`

- [ ] **Step 1: 实现扫描结果选择逻辑**

`BleScanner.kt`:

```kotlin
package com.tkpang.tvstriptest.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.tkpang.tvstriptest.model.ScanDevice
import com.tkpang.tvstriptest.protocol.LeConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BleScanner(private val context: Context) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val results = linkedMapOf<String, ScanDevice>()
    private val _devices = MutableStateFlow<List<ScanDevice>>(emptyList())
    val devices: StateFlow<List<ScanDevice>> = _devices

    private var rssiThreshold: Int = -65
    private var targetCount: Int = 1

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = result.scanRecord?.deviceName ?: device.name
            val matches = name == LeConstants.ADV_NAME || result.scanRecord?.serviceUuids?.contains(ParcelUuid(LeConstants.SERVICE_UUID)) == true
            if (!matches) return
            val scanDevice = ScanDevice(
                address = device.address,
                name = name,
                rssi = result.rssi,
                autoSelected = result.rssi >= rssiThreshold,
                selected = result.rssi >= rssiThreshold,
            )
            results[device.address] = scanDevice
            publish()
        }
    }

    fun start(rssiThreshold: Int, targetCount: Int) {
        requireScanPermission()
        this.rssiThreshold = rssiThreshold
        this.targetCount = targetCount.coerceAtLeast(1)
        results.clear()
        publish()
        val filters = listOf(ScanFilter.Builder().build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        adapter?.bluetoothLeScanner?.startScan(filters, settings, callback)
    }

    fun stop() {
        if (hasScanPermission()) adapter?.bluetoothLeScanner?.stopScan(callback)
    }

    fun setSelected(address: String, selected: Boolean) {
        val current = results[address] ?: return
        results[address] = current.copy(selected = selected)
        publish()
    }

    private fun publish() {
        val sorted = results.values.sortedByDescending { it.rssi }
        val allowed = sorted.filter { it.rssi >= rssiThreshold }.take(targetCount).map { it.address }.toSet()
        _devices.value = sorted.map { device ->
            if (device.autoSelected) device.copy(selected = device.address in allowed) else device
        }
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requireScanPermission() {
        check(hasScanPermission()) { "BLE scan permission missing" }
        check(adapter?.isEnabled == true) { "Bluetooth is disabled" }
    }
}
```

- [ ] **Step 2: 实现设备会话骨架**

`BleDeviceSession.kt`:

```kotlin
package com.tkpang.tvstriptest.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.tkpang.tvstriptest.protocol.LeConstants
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.UUID

class BleDeviceSession(
    private val context: Context,
    private val device: BluetoothDevice,
) {
    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private val ready = CompletableDeferred<Unit>()

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED && !ready.isCompleted) {
                ready.completeExceptionally(IllegalStateException("Disconnected before ready"))
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                ready.completeExceptionally(IllegalStateException("Service discovery failed: $status"))
                return
            }
            val service = gatt.getService(LeConstants.SERVICE_UUID)
            writeCharacteristic = service?.getCharacteristic(LeConstants.WRITE_UUID)
            val notify = service?.getCharacteristic(LeConstants.NOTIFY_UUID)
            if (writeCharacteristic == null || notify == null) {
                ready.completeExceptionally(IllegalStateException("Missing BLE service or characteristic"))
                return
            }
            gatt.setCharacteristicNotification(notify, true)
            val descriptorUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            val descriptor = notify.getDescriptor(descriptorUuid)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            } else {
                ready.complete(Unit)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) ready.complete(Unit)
            else ready.completeExceptionally(IllegalStateException("Notify subscribe failed: $status"))
        }
    }

    suspend fun connect(timeoutMs: Long = 12_000) {
        gatt = device.connectGatt(context, false, callback)
        withTimeout(timeoutMs) { ready.await() }
    }

    fun write(data: ByteArray): Boolean {
        val characteristic = writeCharacteristic ?: return false
        characteristic.value = data
        return gatt?.writeCharacteristic(characteristic) == true
    }

    fun close() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew assembleDebug`

Expected: PASS.

## Task 6: 工厂流程 ViewModel 和 UI

**Files:**
- Create: `app/src/main/java/com/tkpang/tvstriptest/factory/CommandDispatcher.kt`
- Create: `app/src/main/java/com/tkpang/tvstriptest/factory/FactoryViewModel.kt`
- Create: `app/src/main/java/com/tkpang/tvstriptest/ui/FactoryScreen.kt`
- Modify: `app/src/main/java/com/tkpang/tvstriptest/MainActivity.kt`

- [ ] **Step 1: 实现逐设备命令下发抽象**

`CommandDispatcher.kt`:

```kotlin
package com.tkpang.tvstriptest.factory

import com.tkpang.tvstriptest.protocol.DpCommands


interface CommandDispatcher {
    suspend fun sendDp(addresses: List<String>, json: String): Map<String, Result<Unit>>
    suspend fun unbind(addresses: List<String>): Map<String, Result<Unit>>
}

class PerDeviceBleDispatcher : CommandDispatcher {
    override suspend fun sendDp(addresses: List<String>, json: String): Map<String, Result<Unit>> {
        return addresses.associateWith { Result.success(Unit) }
    }

    override suspend fun unbind(addresses: List<String>): Map<String, Result<Unit>> {
        return addresses.associateWith { Result.success(Unit) }
    }

    fun buildHighestPowerCommands(ledCount: Int, rgb: Int, brightness: Int): List<String> {
        return DpCommands.highestPowerSequence(ledCount, rgb, brightness)
    }
}
```

- [ ] **Step 2: 实现 ViewModel**

`FactoryViewModel.kt`:

```kotlin
package com.tkpang.tvstriptest.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkpang.tvstriptest.model.FactoryDevice
import com.tkpang.tvstriptest.model.FactorySettings
import com.tkpang.tvstriptest.model.ProductCatalog
import com.tkpang.tvstriptest.model.ScanDevice
import com.tkpang.tvstriptest.protocol.DpCommands
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FactoryUiState(
    val settings: FactorySettings = FactorySettings(),
    val scanDevices: List<ScanDevice> = emptyList(),
    val connectedDevices: List<FactoryDevice> = emptyList(),
    val message: String = "等待扫描",
)

class FactoryViewModel(
    private val dispatcher: CommandDispatcher = PerDeviceBleDispatcher(),
) : ViewModel() {
    private val _state = MutableStateFlow(FactoryUiState())
    val state: StateFlow<FactoryUiState> = _state

    fun updateSettings(settings: FactorySettings) {
        _state.update { it.copy(settings = settings) }
    }

    fun replaceScanResults(devices: List<ScanDevice>) {
        _state.update { it.copy(scanDevices = devices, message = "扫描到 ${devices.size} 台设备") }
    }

    fun connectSelected() {
        val selected = state.value.scanDevices.filter { it.selected }
        val connected = selected.map {
            FactoryDevice(address = it.address, name = it.name, rssi = it.rssi, state = com.tkpang.tvstriptest.model.DeviceConnectionState.Ready)
        }
        _state.update { it.copy(connectedDevices = connected, message = "已选择连接 ${connected.size} 台设备") }
    }

    fun setPid() = sendDp(DpCommands.setPid(state.value.settings.pid), "PID/SKU 已下发")

    fun setColor(rgb: Int) {
        val ledCount = ProductCatalog.requireLedCount(state.value.settings.productDevName, state.value.settings.pid)
        sendDp(DpCommands.grooveHandle(DpCommands.solidColorGroove(ledCount, rgb)), "颜色已下发")
    }

    fun setHighestPowerColor() {
        val settings = state.value.settings
        val ledCount = ProductCatalog.requireLedCount(settings.productDevName, settings.pid)
        viewModelScope.launch {
            val addresses = state.value.connectedDevices.map { it.address }
            DpCommands.highestPowerSequence(ledCount, settings.maxPowerColor, settings.maxBrightness).forEach { dispatcher.sendDp(addresses, it) }
            _state.update { it.copy(message = "最高亮度颜色已下发") }
        }
    }

    fun unbindAll() {
        viewModelScope.launch {
            dispatcher.unbind(state.value.connectedDevices.map { it.address })
            _state.update { it.copy(connectedDevices = emptyList(), message = "解绑命令已发送") }
        }
    }

    private fun sendDp(json: String, successMessage: String) {
        viewModelScope.launch {
            dispatcher.sendDp(state.value.connectedDevices.map { it.address }, json)
            _state.update { it.copy(message = successMessage) }
        }
    }
}
```

- [ ] **Step 3: 实现 Compose UI**

`FactoryScreen.kt`:

```kotlin
package com.tkpang.tvstriptest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tkpang.tvstriptest.factory.FactoryViewModel

@Composable
fun FactoryScreen(viewModel: FactoryViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("TV Strip 工厂测试", style = MaterialTheme.typography.headlineSmall)
        Text("产品类型: ${state.settings.productDevName}  PID/SKU: ${state.settings.pid}")
        Text("RSSI 阈值: ${state.settings.rssiThreshold} dBm  目标数量: ${state.settings.targetDeviceCount}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.connectSelected() }) { Text("连接已勾选") }
            OutlinedButton(onClick = { viewModel.setPid() }) { Text("设置 PID") }
            OutlinedButton(onClick = { viewModel.setColor(0xFF0000) }) { Text("设灯红色") }
            OutlinedButton(onClick = { viewModel.setHighestPowerColor() }) { Text("最高亮度颜色") }
            OutlinedButton(onClick = { viewModel.unbindAll() }) { Text("一键解绑删除") }
        }
        Text(state.message)
        Spacer(Modifier.height(8.dp))
        Text("已连接设备: ${state.connectedDevices.size}")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.connectedDevices) { device ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(device.address)
                        Text("RSSI ${device.rssi}  状态 ${device.state}")
                        if (device.lastError.isNotBlank()) Text("错误: ${device.lastError}")
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: 接入 MainActivity**

`MainActivity.kt`:

```kotlin
package com.tkpang.tvstriptest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.tkpang.tvstriptest.ui.FactoryScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    FactoryScreen()
                }
            }
        }
    }
}
```

- [ ] **Step 5: 编译验证**

Run: `./gradlew test assembleDebug`

Expected: PASS.

## Task 7: 最终验证和收尾

**Files:**
- Modify: `README.md`

- [ ] **Step 1: 更新 README 使用说明**

追加：

```markdown
## 使用流程

1. 打开 App 并授权蓝牙权限。
2. 点击扫描，App 展示附近 `LP` 设备。
3. RSSI 高于阈值的设备会自动勾选，操作员可手动调整。
4. 点击连接已勾选，批量连接设备。
5. 按需设置 PID/SKU、设灯、测试最高亮度颜色。
6. 测试完成后点击一键解绑删除。

## 当前限制

- 第一版优先逐设备 BLE 下发，group 方案保留为后续优化。
- `d158` 只用于 STV1 PID/SKU 设置，不代表产品类型。
- EMC 精确模板序列仍需工厂确认，当前先内置基础颜色模板。
```

- [ ] **Step 2: 全量验证**

Run: `./gradlew test assembleDebug`

Expected: PASS and APK exists at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: 检查 git 状态**

Run: `git status --short`

Expected: 只包含本次 Android 工程和文档相关文件。

## 自查

- Spec 覆盖：仓库独立、中文文档、产品类型来自 ESP32 `DEV_NAME`、PID/SKU 区分、扫描自动勾选、批量连接、设灯、最高亮度颜色、一键解绑、逐设备优先/group 预留、GitHub Actions 均有任务覆盖。
- 占位扫描：计划不使用 TBD/TODO/PLACEHOLDER。
- 类型一致性：`ProductCatalog`、`FactorySettings`、`DpCommands`、`CommandDispatcher`、`FactoryViewModel`、`FactoryScreen` 的命名在任务间保持一致。
