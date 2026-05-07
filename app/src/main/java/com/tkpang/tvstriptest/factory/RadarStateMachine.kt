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
    private val pairedOrSkipped = mutableSetOf<String>()

    init {
        scope.launch {
            source.collect { devices -> reconcile(devices) }
        }
    }

    fun markHandled(address: String) {
        pairedOrSkipped += address
        timers.remove(address)?.cancel()
    }

    fun resetDevice(address: String) {
        pairedOrSkipped -= address
    }

    private fun reconcile(devices: List<ScanDevice>) {
        val threshold = sensitivity.value.rssiThreshold
        val inZoneAddresses = devices
            .filter { it.rssi >= threshold && it.address !in pairedOrSkipped }
            .map { it.address }
            .toSet()

        timers.keys.toList().forEach { addr ->
            if (addr !in inZoneAddresses) {
                timers.remove(addr)?.cancel()
            }
        }

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
