package com.tkpang.tvstriptest.model

enum class DeviceConnectionState { Discovered, Connecting, Connected, Ready, Failed, Unbound }

data class ScanDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val pid: Int? = null,
    val isBonded: Boolean = false,
    val pairingState: PairingState = PairingState.DETECTED,
    // Legacy fields — kept for backward compatibility; Task 12 will remove them
    @Deprecated("Superseded by pairingState logic in Task 12 use-cases")
    val autoSelected: Boolean = false,
    @Deprecated("Superseded by pairingState logic in Task 12 use-cases")
    val selected: Boolean = false,
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
