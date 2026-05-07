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
