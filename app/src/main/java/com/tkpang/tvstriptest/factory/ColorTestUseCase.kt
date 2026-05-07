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
