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
        // 当要求「只看未绑定」时，必须既未绑定，又有有效广播 PID（pid != null）；
        // pid=null 意味着 manufacturer data 解析失败，绑定状态不可信，安全起见过滤掉。
        val bondOk = !onlyUnbonded || (!d.isBonded && d.pid != null)
        pidOk && bondOk
    }
}
