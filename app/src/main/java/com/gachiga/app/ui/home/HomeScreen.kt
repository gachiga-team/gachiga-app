package com.gachiga.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gachiga.app.data.settings.VehicleSettings
import com.gachiga.app.data.trip.LiveTrip
import com.gachiga.app.domain.EnergyType
import com.gachiga.app.domain.MotionState
import com.gachiga.app.ui.theme.CarDimens
import com.gachiga.app.ui.theme.SubtleText

/**
 * 화면 기준은 UI_GUIDELINES.md를 따른다.
 * - 주행 중(또는 판별 불가): 큰 숫자 2개 + 말하기 버튼 하나. 대화상자와 상세 화면은 모두 숨긴다.
 * - 정차 중: 주요 버튼 2~3개를 위쪽에 크게, 나머지는 보조 버튼으로.
 */
@Composable
fun HomeScreen(vm: HomeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val overlay by vm.overlay.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val settingsOpen by vm.settingsOpen.collectAsStateWithLifecycle()
    val vehicleSettings by vm.vehicleSettings.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // F7: 주행 중(또는 판별 불가)이면 입력 UI와 대화상자를 모두 숨긴다.
        // 차량 값을 하나도 읽지 못하면 기록할 것이 없으므로 잠그지 않는다 (spec.md 3.2-4).
        if (state.locked) {
            DrivingModeScreen(state, onClick = vm::onVoiceClick, onDemoPark = vm::demoPark)
            return@Surface
        }

        val currentSummary = summary
        when {
            settingsOpen -> SettingsScreen(
                settings = vehicleSettings,
                capabilities = state.capabilities,
                firstRun = !vehicleSettings.onboarded,
                tripInProgress = state.live != null,
                demoMode = state.demoMode,
                onToggleDemo = { vm.setDemoMode(!state.demoMode) },
                onSave = vm::onSettingsSaved,
                onCancel = vm::onSettingsCancelled,
            )
            currentSummary != null -> SummaryScreen(
                detail = currentSummary,
                onRefuelClick = vm::onEndedRefuelClick,
                onHeadcountClick = vm::onEndedHeadcountClick,
                onDone = vm::dismissSummary,
            )
            history != null -> HistoryScreen(history!!, onItemClick = vm::onHistoryItemClick, onBack = vm::dismissHistory)
            state.live != null -> TripScreen(state, state.live!!, vm)
            else -> IdleScreen(state, vehicleSettings, vm)
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
            is Overlay.EndedRefuelAmount -> AmountDialog(
                title = "${state.energyType.refuelWord()} 금액",
                subtitle = "%.0f%% → %.0f%%".format(o.refuel.percentBefore, o.refuel.percentAfter),
                showCategories = false,
                dismissLabel = "취소",
                onConfirm = { _, amount -> vm.onEndedRefuelAmountEntered(o.tripId, o.refuel.id, amount) },
                onDismiss = vm::dismissOverlay,
            )
            is Overlay.EndedHeadcount -> HeadcountDialog(
                onChosen = { n -> vm.onEndedHeadcountChosen(o.tripId, n) },
                onDismiss = vm::dismissOverlay,
            )
        }
    }
}

/**
 * 주행 중 화면 (F7, UI_GUIDELINES.md 5절): 큰 숫자 2개와 말하기 버튼 하나.
 * 2초 안에 읽히도록 문장 대신 숫자만 크게 보여 준다.
 */
@Composable
private fun DrivingModeScreen(state: HomeUiState, onClick: () -> Unit, onDemoPark: () -> Unit) {
    val live = state.live
    Box(Modifier.fillMaxSize().padding(CarDimens.screenPadding), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CarDimens.gap),
        ) {
            if (state.demoMode) DemoBadge()
            if (live != null) {
                Text(
                    if (live.isPaused) "일시정지 중" else "여행 중 · ${live.trip.headcount}명",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (live.isPaused) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                )
                Text(km(live.distanceKm), style = MaterialTheme.typography.displayLarge)
                Text(
                    "${state.energyType.label()} −%.0f%%".format(live.energyUsedPercent),
                    style = MaterialTheme.typography.displayMedium,
                )
            } else {
                Text("주행 중", style = MaterialTheme.typography.displayLarge)
                if (state.canListen) {
                    Text("\"여행 시작, 3명\"처럼 말해 보세요", style = MaterialTheme.typography.bodyLarge, color = SubtleText)
                }
            }
            // F7: 주행 중 입력은 음성 버튼 하나뿐이다.
            TalkButton(state, onClick, wide = true)
            // 데모 모드에서만: 차량 동작(정차)을 흉내 내는 버튼. 실제 차량에서는 보이지 않는다.
            if (state.demoMode) SecondaryButton("정차 (데모)", onDemoPark)
        }
    }
}

/** 여행이 없을 때 (정차 중). 주요 버튼 2개(여행 시작, 말하기) + 보조 버튼 2개(지난 여행, 차량 설정). */
@Composable
private fun IdleScreen(state: HomeUiState, settings: VehicleSettings, vm: HomeViewModel) {
    Column(
        Modifier.fillMaxSize().padding(CarDimens.screenPadding).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CarDimens.gap),
    ) {
        if (state.demoMode) DemoBadge()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CarDimens.gap)) {
            Text("같이가", style = MaterialTheme.typography.headlineMedium)
            Text("달린 만큼 나누는 그룹 여행", style = MaterialTheme.typography.bodyMedium, color = SubtleText)
        }
        if (!state.vehicleChecked) {
            Text("차량 정보를 확인하는 중이에요…", style = MaterialTheme.typography.bodyLarge)
            SecondaryButton("차량 설정", vm::onSettingsClick)
            return@Column
        }
        Text(vehicleLine(state), style = MaterialTheme.typography.bodyLarge)
        // F9: 정산에 쓰는 설정값을 늘 보여 준다 (intent.md 원칙 2: 근거를 정직하게)
        val type = state.energyType
        val (capacity, price) = if (type == EnergyType.EV) settings.evCapacityKWh to settings.evPriceWonPerKWh
        else settings.fuelCapacityL to settings.fuelPriceWonPerL
        Text(
            "정산 기준: ${type.label()} %.1f %s · 기본 단가 %,d원/%s".format(capacity, type.unit(), price, type.unit()),
            style = MaterialTheme.typography.bodyMedium,
            color = SubtleText,
        )
        if (!state.capabilities.canRecordTrip) {
            Text(
                "이 차량에서 주행거리나 연료·배터리 값을 읽지 못했어요. 차량 설정에서 데모 모드를 켜면 확인할 수 있어요.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // 주요 버튼 (UI_GUIDELINES.md 4절: 최대 2~3개, 위쪽에 크게)
        ButtonRow {
            Button(
                onClick = vm::onStartClick,
                enabled = state.capabilities.canRecordTrip, // spec.md 3.2-4: 값을 못 읽으면 기록 기능은 막는다
                modifier = Modifier.heightIn(min = CarDimens.touch).widthIn(min = PRIMARY_MIN_WIDTH),
            ) { Text("여행 시작", style = MaterialTheme.typography.labelLarge) }
            if (state.capabilities.canRecordTrip) TalkButton(state, vm::onVoiceClick)
        }
        // 보조 버튼
        ButtonRow {
            SecondaryButton("지난 여행", vm::onHistoryClick)
            SecondaryButton("차량 설정", vm::onSettingsClick)
        }
        if (state.demoMode) DemoPanel(vm)
    }
}

/** 여행 중, 정차 상태. 큰 숫자 칸 3개 + 주요 버튼(말하기, 비용 추가) + 보조 버튼(일시정지, 종료). */
@Composable
private fun TripScreen(state: HomeUiState, live: LiveTrip, vm: HomeViewModel) {
    val type = live.trip.energyType
    val settlement = live.settlement()
    val usedAmount = live.energyUsedPercent / 100.0 * live.trip.capacity
    val unpaid = live.refuels.count { it.amountWon == null }

    Column(
        Modifier.fillMaxSize().padding(CarDimens.screenPadding).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CarDimens.gap),
    ) {
        if (state.demoMode) DemoBadge()
        Text(
            if (live.isPaused) "일시정지 중 · ${live.trip.headcount}명" else "여행 중 · ${live.trip.headcount}명",
            style = MaterialTheme.typography.headlineMedium,
            color = if (live.isPaused) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
        )
        // 숫자는 크게, 글은 짧게 (UI_GUIDELINES.md 4절)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CarDimens.gap * 2),
            verticalArrangement = Arrangement.spacedBy(CarDimens.gap),
        ) {
            StatTile(km(live.distanceKm), "이동 거리")
            StatTile("%.1f%%p".format(live.energyUsedPercent), "${type.label()} 사용 (%.1f %s)".format(usedAmount, type.unit()))
            if (live.trip.headcount > 1) StatTile(won(settlement.perPersonWon), "지금까지 1인 부담")
            else StatTile(won(settlement.totalCostWon), "지금까지 비용")
        }
        Text(
            buildString {
                append("${type.refuelWord()} ${live.refuels.size}건")
                if (unpaid > 0) append(" (금액 미입력 ${unpaid}건)")
                append(" · 기타 비용 ${won(live.expenses.sumOf { it.amountWon })}")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = SubtleText,
        )

        // 주요 버튼
        ButtonRow {
            TalkButton(state, vm::onVoiceClick)
            Button(
                onClick = vm::onExpenseClick,
                modifier = Modifier.heightIn(min = CarDimens.touch).widthIn(min = PRIMARY_MIN_WIDTH),
            ) { Text("비용 추가", style = MaterialTheme.typography.labelLarge) }
            if (unpaid > 0) {
                Button(onClick = vm::onUnpaidRefuelClick, modifier = Modifier.heightIn(min = CarDimens.touch)) {
                    Text("${type.refuelWord()} 금액 입력", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        // 보조 버튼
        ButtonRow {
            SecondaryButton(if (live.isPaused) "다시 시작" else "일시정지", if (live.isPaused) vm::onResume else vm::onPause)
            // F5: 정차 중에만 끝낼 수 있다
            SecondaryButton("여행 종료", vm::onEndClick, enabled = state.motion == MotionState.PARKED)
        }
        if (state.demoMode) DemoPanel(vm)
    }
}

/** 버튼 줄. 화면이 좁으면 다음 줄로 넘어간다 (Pleos 화면 분할 대응). */
@Composable
fun ButtonRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(CarDimens.gap),
        verticalArrangement = Arrangement.spacedBy(CarDimens.gap),
    ) { content() }
}

/** 큰 숫자 하나와 짧은 설명. */
@Composable
private fun StatTile(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.displayMedium)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SubtleText)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = CarDimens.touch)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DemoPanel(vm: HomeViewModel) {
    // F10 데모 패널
    ButtonRow {
        SecondaryButton("10 km 주행", vm::demoDrive)
        SecondaryButton("주유 +50%", { vm.demoAddEnergy(50.0) })
        SecondaryButton("충전 +40%", { vm.demoAddEnergy(40.0) })
    }
}

/** F8 말하기 버튼. 음성 인식을 못 쓰면 보이지 않는다(터치로 대체). */
@Composable
private fun TalkButton(state: HomeUiState, onClick: () -> Unit, wide: Boolean = false) {
    if (!state.canListen) return
    Button(
        onClick = onClick,
        enabled = !state.listening,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
        modifier = Modifier.heightIn(min = CarDimens.touch).widthIn(min = if (wide) 320.dp else PRIMARY_MIN_WIDTH),
    ) { Text(if (state.listening) "듣고 있어요…" else "말하기", style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun DemoBadge() {
    // 색과 글자를 함께 쓴다 (색만으로 의미를 전달하지 않기)
    Surface(color = MaterialTheme.colorScheme.secondary, shape = MaterialTheme.shapes.small) {
        Text("데모 모드", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
    }
}

private val PRIMARY_MIN_WIDTH = 200.dp

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
