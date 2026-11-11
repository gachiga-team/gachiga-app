package com.gachiga.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gachiga.app.data.trip.LiveTrip
import com.gachiga.app.domain.MotionState
import com.gachiga.app.ui.theme.CarDimens

@Composable
fun HomeScreen(vm: HomeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val overlay by vm.overlay.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // F7: 주행 중(또는 판별 불가)이면 한 줄 정보만 보여 주고 입력 UI와 대화상자를 모두 숨긴다.
        // 차량 값을 하나도 읽지 못하면 기록할 것이 없으므로 잠그지 않는다 (spec.md 3.2-4).
        if (state.locked) {
            DrivingModeScreen(state, onClick = vm::onVoiceClick, onDemoPark = vm::demoPark)
            return@Surface
        }

        val currentSummary = summary
        when {
            currentSummary != null -> SummaryScreen(currentSummary, onDone = vm::dismissSummary)
            state.live != null -> TripScreen(state, state.live!!, vm)
            else -> IdleScreen(state, vm)
        }

        when (val o = overlay) {
            null -> Unit
            Overlay.Headcount -> HeadcountDialog(onChosen = vm::onHeadcountChosen, onDismiss = vm::dismissOverlay)
            Overlay.Expense -> AmountDialog(
                title = "비용 추가",
                subtitle = null,
                showCategories = true,
                dismissLabel = "취소",
                onConfirm = vm::onExpenseEntered,
                onDismiss = vm::dismissOverlay,
            )
            is Overlay.RefuelAmount -> {
                val word = state.energyType.refuelWord()
                AmountDialog(
                    title = "${word}하셨네요. 얼마 들었나요?",
                    subtitle = "%.0f%% → %.0f%%. 건너뛰면 기본 단가로 추정해요.".format(o.refuel.percentBefore, o.refuel.percentAfter),
                    showCategories = false,
                    dismissLabel = "건너뛰기",
                    onConfirm = { _, amount -> vm.onRefuelAmountEntered(o.refuel.id, amount) },
                    onDismiss = vm::dismissOverlay,
                    onInputStarted = vm::onAmountInputStarted,
                )
            }
            Overlay.ConfirmEnd -> ConfirmEndDialog(onConfirm = vm::onEndConfirmed, onDismiss = vm::dismissOverlay)
            is Overlay.Message -> MessageDialog(o.text, onDismiss = vm::dismissOverlay)
        }
    }
}

@Composable
private fun DrivingModeScreen(state: HomeUiState, onClick: () -> Unit, onDemoPark: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (state.demoMode) DemoBadge()
            Text(drivingLine(state), style = MaterialTheme.typography.displayMedium)
            // F7: 주행 중 입력은 음성 버튼 하나뿐이다.
            TalkButton(state, onClick)
            // 데모 모드에서만: 차량 동작(정차)을 흉내 내는 버튼. 실제 차량에서는 보이지 않는다.
            if (state.demoMode) {
                OutlinedButton(onClick = onDemoPark, Modifier.height(CarDimens.buttonHeight)) { Text("정차 (데모)") }
            }
        }
    }
}

/** 여행이 없을 때 (정차 중). */
@Composable
private fun IdleScreen(state: HomeUiState, vm: HomeViewModel) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (state.demoMode) DemoBadge()
        Text("같이가", style = MaterialTheme.typography.headlineMedium)
        Text(vehicleLine(state), style = MaterialTheme.typography.bodyLarge)

        if (!state.capabilities.canRecordTrip) {
            Text(
                "이 차량에서 주행거리나 연료·배터리 값을 읽지 못했어요. 데모 모드로 확인할 수 있어요.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = vm::onStartClick,
                enabled = state.capabilities.canRecordTrip, // spec.md 3.2-4: 값을 못 읽으면 기록 기능은 막는다
                modifier = Modifier.height(CarDimens.buttonHeight),
            ) { Text("여행 시작", style = MaterialTheme.typography.labelLarge) }
            if (state.capabilities.canRecordTrip) TalkButton(state, vm::onVoiceClick)
        }

        OutlinedButton(
            onClick = { vm.setDemoMode(!state.demoMode) },
            modifier = Modifier.height(CarDimens.buttonHeight),
        ) { Text(if (state.demoMode) "데모 모드 끄기" else "데모 모드 켜기") }

        if (state.demoMode) DemoPanel(vm)
    }
}

/** 여행 중, 정차 상태. */
@Composable
private fun TripScreen(state: HomeUiState, live: LiveTrip, vm: HomeViewModel) {
    val type = live.trip.energyType
    val settlement = live.settlement()
    val usedAmount = live.energyUsedPercent / 100.0 * live.trip.capacity

    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (state.demoMode) DemoBadge()
        Text(
            if (live.isPaused) "일시정지 중 · ${live.trip.headcount}명" else "여행 중 · ${live.trip.headcount}명",
            style = MaterialTheme.typography.headlineMedium,
            color = if (live.isPaused) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
        )
        Text(
            "${km(live.distanceKm)} · ${type.label()} %.1f%%p 사용 (%.1f %s)".format(live.energyUsedPercent, usedAmount, type.unit()),
            style = MaterialTheme.typography.displayMedium,
        )
        if (live.trip.headcount > 1) {
            Text("지금까지 1인 부담 약 ${won(settlement.perPersonWon)}", style = MaterialTheme.typography.bodyLarge)
        }
        val unpaid = live.refuels.count { it.amountWon == null }
        Text(
            buildString {
                append("${type.refuelWord()} ${live.refuels.size}건")
                if (unpaid > 0) append(" (금액 미입력 ${unpaid}건)")
                append(" · 기타 비용 ${won(live.expenses.sumOf { it.amountWon })}")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TalkButton(state, vm::onVoiceClick)
            Button(onClick = vm::onExpenseClick, modifier = Modifier.height(CarDimens.buttonHeight)) { Text("비용 추가") }
            if (unpaid > 0) {
                Button(onClick = vm::onUnpaidRefuelClick, modifier = Modifier.height(CarDimens.buttonHeight)) {
                    Text("${type.refuelWord()} 금액 입력")
                }
            }
            OutlinedButton(
                onClick = if (live.isPaused) vm::onResume else vm::onPause,
                modifier = Modifier.height(CarDimens.buttonHeight),
            ) { Text(if (live.isPaused) "다시 시작" else "일시정지") }
            OutlinedButton(
                onClick = vm::onEndClick,
                enabled = state.motion == MotionState.PARKED, // F5: 정차 중에만 끝낼 수 있다
                modifier = Modifier.height(CarDimens.buttonHeight),
            ) { Text("여행 종료") }
        }

        if (state.demoMode) DemoPanel(vm)
    }
}

@Composable
private fun DemoPanel(vm: HomeViewModel) {
    // F10 데모 패널
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = vm::demoDrive, Modifier.height(CarDimens.buttonHeight)) { Text("10 km 주행") }
        Button(onClick = { vm.demoAddEnergy(50.0) }, Modifier.height(CarDimens.buttonHeight)) { Text("주유 +50%") }
        Button(onClick = { vm.demoAddEnergy(40.0) }, Modifier.height(CarDimens.buttonHeight)) { Text("충전 +40%") }
    }
}

/** F8 말하기 버튼. 음성 인식을 못 쓰면 보이지 않는다(터치로 대체). */
@Composable
private fun TalkButton(state: HomeUiState, onClick: () -> Unit) {
    if (!state.canListen) return
    Button(
        onClick = onClick,
        enabled = !state.listening,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
        modifier = Modifier.height(CarDimens.buttonHeight),
    ) { Text(if (state.listening) "듣고 있어요…" else "말하기", style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun DemoBadge() {
    Surface(color = MaterialTheme.colorScheme.secondary, shape = MaterialTheme.shapes.small) {
        Text("데모 모드", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
    }
}

/** 주행 모드 한 줄 (F7): "여행 중 · 132.0 km · 배터리 −18%" */
private fun drivingLine(state: HomeUiState): String {
    val live = state.live ?: return vehicleLine(state)
    val head = if (live.isPaused) "일시정지" else "여행 중"
    return "$head · ${km(live.distanceKm)} · ${state.energyType.label()} −%.0f%%".format(live.energyUsedPercent)
}

private fun vehicleLine(state: HomeUiState): String {
    val odo = state.odometerKm?.let { km(it) } ?: "주행거리 -"
    val label = state.energyType.label()
    val energy = state.energyPercent?.let { "$label %.0f%%".format(it) } ?: "$label -"
    val motion = when (state.motion) {
        MotionState.DRIVING -> "주행 중"
        MotionState.PARKED -> "정차 중"
        MotionState.UNKNOWN -> "상태 확인 중"
    }
    return "$motion · $odo · $energy"
}
