package com.tkpang.tvstriptest.factory

import com.tkpang.tvstriptest.model.FactoryDevice
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
                if (result.success) completed += 1 else failed += result
                emit(Progress(completed, devices.size, failed.toList()))
            }
        }
    }
}
