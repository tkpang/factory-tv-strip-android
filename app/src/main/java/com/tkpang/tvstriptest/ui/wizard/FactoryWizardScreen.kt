package com.tkpang.tvstriptest.ui.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    vm: FactoryViewModel,
    onExit: () -> Unit,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            Column(Modifier.background(Color.White).padding(12.dp)) {
                StepIndicator(currentStep = state.step)
            }
        },
        bottomBar = {
            BottomNavBar(
                // Step 1 的「上一步」用作退出测试模式回主页（顺手清理状态，
                // 避免下次进入看到旧的已配设备/反馈消息）；其余 step 才是回退一步。
                onBack = if (state.step.index > 0) {
                    { vm.back() }
                } else {
                    { vm.resetToFirstStep(); onExit() }
                },
                onNext = nextLambda(state.step, vm, onExit),
                nextLabel = if (state.step == WizardStep.UNBIND) "完成" else "下一步 →",
                backLabel = if (state.step.index == 0) "← 返回主页" else "← 上一步",
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF1F5F9))
        ) {
            when (state.step) {
                WizardStep.PRODUCT -> Step1ProductScreen(
                    selectedProductDevName = state.settings.productDevName,
                    pidFilter = state.settings.pidFilter,
                    onlyUnbonded = state.settings.onlyUnbonded,
                    onProductChange = vm::setProductType,
                    onPidFilterChange = vm::setPidFilter,
                    onOnlyUnbondedChange = vm::setOnlyUnbonded,
                )
                WizardStep.SCAN -> Step2ScanScreen(
                    state = state,
                    onSensitivityChange = vm::setSensitivity,
                    onOpenBluetooth = {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onStartScan = vm::startScan,
                    onStopScan = vm::stopScan,
                    onPairDevice = vm::pairDevice,
                )
                WizardStep.COLOR -> Step3ColorScreen(
                    state = state,
                    onCommand = vm::runColorTest,
                    onColorContinuous = vm::setColorContinuous,
                    onBrightnessContinuous = vm::setBrightnessContinuous,
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
