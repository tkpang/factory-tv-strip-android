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
