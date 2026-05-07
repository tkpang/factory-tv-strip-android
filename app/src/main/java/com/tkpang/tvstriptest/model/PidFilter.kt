package com.tkpang.tvstriptest.model

sealed interface PidFilter {
    data object Any : PidFilter
    data class Specific(val pid: Int) : PidFilter
}
