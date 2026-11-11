package com.gachiga.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gachiga.app.AppContainer
import com.gachiga.app.data.settings.VehicleSettings
import com.gachiga.app.data.trip.LiveTrip
import com.gachiga.app.data.trip.Refuel
import com.gachiga.app.data.trip.TripDetail
import com.gachiga.app.data.trip.TripRecorder
import com.gachiga.app.data.vehicle.VehicleCapabilities
import com.gachiga.app.data.voice.VoiceIO
import com.gachiga.app.domain.VoiceCommand
import com.gachiga.app.domain.EnergyType
import com.gachiga.app.domain.ExpenseCategory
import com.gachiga.app.domain.InputMethod
import com.gachiga.app.domain.MotionState
import com.gachiga.app.domain.Settlement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val demoMode: Boolean = false,
    val capabilities: VehicleCapabilities = VehicleCapabilities.NONE,
    val energyType: EnergyType = EnergyType.FUEL,
    val odometerKm: Double? = null,
    val energyPercent: Double? = null,
    val motion: MotionState = MotionState.UNKNOWN,
    /** 차량 지원 여부 확인이 끝났는지. 앱을 켠 직후에는 차량 서비스 연결에 몇 초 걸린다. */
    val vehicleChecked: Boolean = false,
    val live: LiveTrip? = null,
    /** F8: 음성 인식을 쓸 수 있으면 말하기 버튼을 보여 준다. */
    val canListen: Boolean = false,
    val listening: Boolean = false,
    /** 플랫폼 주행 제한(CarUxRestrictions). null이면 이 기기에서 확인할 수 없음. */
    val uxRestricted: Boolean? = null,
) {
    /**
     * 입력을 잠그고 주행 화면을 보여 줄지 (F7, UI_GUIDELINES.md 5절).
     * 1. 플랫폼이 주행 제한을 요구하면 무조건 잠근다 (Pleos 요구 사항).
     * 2. 기록할 수 있는 차량에서 기어가 P가 아니거나 판별 불가면 잠근다 (spec.md 3.2, 3.2-4).
     */
    val locked: Boolean get() = uxRestricted == true || (motion.locksInput && capabilities.canRecordTrip)
}

/** 정차 중에만 보이는 대화상자. */
sealed interface Overlay {
    data object Headcount : Overlay
    data object Expense : Overlay
    data class RefuelAmount(val refuel: Refuel) : Overlay
    data object ConfirmEnd : Overlay
    data class Message(val text: String) : Overlay
    /** F6: 끝난 여행의 주유·충전 금액 고치기 */
    data class EndedRefuelAmount(val tripId: Long, val refuel: Refuel) : Overlay
    /** F6: 끝난 여행의 인원 고치기 */
    data class EndedHeadcount(val tripId: Long) : Overlay
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val recorder: TripRecorder = container.recorder

    private val vehicle: Flow<HomeUiState> = container.settings.settings
        .flatMapLatest { settings ->
            val source = container.vehicleSource(settings.demoMode)
            flow { emit(source.capabilities()) }.flatMapLatest { caps ->
                // spec.md 3.1: 읽을 수 있는 쪽을 쓰고, 둘 다 되면 설정값을 따른다.
                val energyType = caps.resolveEnergyType(settings.energyType) ?: settings.energyType
                combine(
                    source.odometerKm().onStartNull(),
                    source.energyPercent(energyType).onStartNull(),
                    source.motion().onStart { emit(MotionState.UNKNOWN) },
                ) { odo, energy, motion ->
                    HomeUiState(settings.demoMode, caps, energyType, odo, energy, motion, vehicleChecked = true)
                }
            }
        }

    private val voice: VoiceIO = container.voice
    private val _listening = MutableStateFlow(false)

    val state: StateFlow<HomeUiState> = combine(
        vehicle,
        recorder.live,
        voice.canListen,
        _listening,
        container.uxRestrictions.requiresDistractionOptimization().onStart { emit(null) },
    ) { v, live, canListen, listening, uxRestricted ->
        v.copy(
            live = live,
            energyType = live?.trip?.energyType ?: v.energyType,
            canListen = canListen,
            listening = listening,
            uxRestricted = uxRestricted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _overlay = MutableStateFlow<Overlay?>(null)
    val overlay: StateFlow<Overlay?> = _overlay.asStateFlow()

    /** 정산 화면에 띄운 여행 (방금 끝낸 여행이거나 지난 여행 목록에서 고른 여행). */
    private val _summary = MutableStateFlow<TripDetail?>(null)
    val summary: StateFlow<TripDetail?> = _summary.asStateFlow()

    /** 지난 여행 목록. null이면 닫혀 있다. */
    private val _history = MutableStateFlow<List<TripDetail>?>(null)
    val history: StateFlow<List<TripDetail>?> = _history.asStateFlow()

    // ---- 지난 여행과 정산 수정 (F6) ----

    fun onHistoryClick() { viewModelScope.launch { _history.value = container.history.endedTrips() } }
    fun dismissHistory() { _history.value = null }
    fun onHistoryItemClick(detail: TripDetail) { _summary.value = detail }

    fun onEndedRefuelClick(refuel: Refuel) {
        val tripId = _summary.value?.trip?.id ?: return
        _overlay.value = Overlay.EndedRefuelAmount(tripId, refuel)
    }

    fun onEndedRefuelAmountEntered(tripId: Long, refuelId: Long, amountWon: Int) {
        _overlay.value = null
        viewModelScope.launch { _summary.value = container.history.setRefuelAmount(tripId, refuelId, amountWon) }
    }

    fun onEndedHeadcountClick() {
        val tripId = _summary.value?.trip?.id ?: return
        _overlay.value = Overlay.EndedHeadcount(tripId)
    }

    fun onEndedHeadcountChosen(tripId: Long, headcount: Int) {
        _overlay.value = null
        viewModelScope.launch { _summary.value = container.history.setHeadcount(tripId, headcount) }
    }

    // ---- 차량 설정 (F9) ----

    val vehicleSettings: StateFlow<VehicleSettings> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, VehicleSettings())

    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    fun onSettingsClick() { _settingsOpen.value = true }

    fun onSettingsSaved(settings: VehicleSettings) {
        _settingsOpen.value = false
        viewModelScope.launch { container.settings.update(settings.copy(onboarded = true)) }
    }

    /** 첫 실행에서 "기본값으로 시작"을 누르면 기본값 그대로 쓰고 다시 묻지 않는다. */
    fun onSettingsCancelled() {
        _settingsOpen.value = false
        viewModelScope.launch { container.settings.setOnboarded() }
    }

    init {
        // F9: 처음 실행하면 차량 설정을 한 번 보여 준다.
        viewModelScope.launch {
            if (!container.settings.settings.first().onboarded) _settingsOpen.value = true
        }
    }

    init {
        // F3: 주유·충전을 감지하면 금액을 묻는다. 30초 안에 입력을 시작하지 않으면 닫고 미입력으로 둔다.
        viewModelScope.launch {
            recorder.refuelDetected.collect { refuel ->
                val prompt = Overlay.RefuelAmount(refuel)
                _overlay.value = prompt
                refuelPromptTimeout?.cancel()
                refuelPromptTimeout = launch {
                    delay(REFUEL_PROMPT_MS)
                    if (_overlay.value == prompt) _overlay.value = null
                }
                // F8: 음성으로도 묻고 답을 한 번 듣는다. 금액이 아니면 화면 입력을 기다린다.
                runVoice {
                    voice.speak("${state.value.energyType.refuelWord()}하셨네요. 얼마 들었나요?")
                    val command = listenCommand()
                    if (command is VoiceCommand.Amount && _overlay.value == prompt) {
                        applyRefuelAmount(refuel.id, command.amountWon.toInt())
                    }
                }
            }
        }
    }

    // ---- 음성 (F8) ----

    private var voiceJob: Job? = null

    /** 음성 대화는 한 번에 하나만. 진행 중이면 새로 시작하지 않는다. */
    private fun runVoice(block: suspend () -> Unit) {
        if (voiceJob?.isActive == true) return
        voiceJob = viewModelScope.launch { block() }
    }

    private suspend fun listenText(): String? {
        _listening.value = true
        return try { voice.listen() } finally { _listening.value = false }
    }

    private suspend fun listenCommand(): VoiceCommand? = listenText()?.let { VoiceCommand.parse(it) }

    /** 말하기 버튼. 주행 중에도 쓸 수 있는 유일한 입력이다 (F7). */
    fun onVoiceClick() = runVoice {
        when (val command = listenCommand()) {
            null -> voice.speak("잘 못 들었어요. 다시 말해 주세요.")
            is VoiceCommand.StartTrip -> voiceStart(command.headcount)
            VoiceCommand.Pause -> {
                if (state.value.live?.isPaused == false) { recorder.pause(); voice.speak("기록을 잠시 멈출게요.") }
                else voice.speak("지금은 멈출 기록이 없어요.")
            }
            VoiceCommand.Resume -> {
                if (state.value.live?.isPaused == true) { recorder.resume(); voice.speak("다시 기록할게요.") }
                else voice.speak("지금은 다시 시작할 기록이 없어요.")
            }
            VoiceCommand.EndTrip -> voiceEnd()
            is VoiceCommand.AddExpense -> {
                if (state.value.live == null) { voice.speak("진행 중인 여행이 없어요."); return@runVoice }
                recorder.addExpense(command.category, command.amountWon.toInt(), InputMethod.VOICE)
                voice.speak("${command.category.label()} ${won(command.amountWon)}을 기록했어요.")
            }
            is VoiceCommand.Amount -> {
                val pending = _overlay.value as? Overlay.RefuelAmount
                if (pending != null) applyRefuelAmount(pending.refuel.id, command.amountWon.toInt())
                else voice.speak("어떤 비용인지 함께 말해 주세요. 예를 들면 통행료 삼천 원.")
            }
            VoiceCommand.Yes, VoiceCommand.No, VoiceCommand.Unknown ->
                voice.speak("잘 못 알아들었어요. 여행 시작, 통행료 얼마, 여행 종료처럼 말해 주세요.")
        }
    }

    private suspend fun voiceStart(spokenHeadcount: Int?) {
        if (state.value.live != null) { voice.speak("이미 여행 중이에요."); return }
        var headcount = spokenHeadcount
        if (headcount == null) {
            voice.speak("몇 명이 함께 가나요?")
            headcount = listenText()?.let { VoiceCommand.headcountOf(it) } // "세 명"처럼 인원만 말해도 된다
        }
        if (headcount == null) {
            // F1: 정차 중이면 인원 선택 화면, 주행 중이면 1명으로 시작하고 나중에 고친다.
            if (!state.value.locked) { _overlay.value = Overlay.Headcount; return }
            headcount = 1
        }
        startTrip(headcount)
    }

    private suspend fun voiceEnd() {
        if (state.value.live == null) { voice.speak("진행 중인 여행이 없어요."); return }
        if (state.value.motion != MotionState.PARKED) { voice.speak("정차한 뒤에 여행을 끝낼 수 있어요."); return }
        // F5: 실수 방지를 위해 한 번 확인한다.
        voice.speak("여행을 끝낼까요?")
        if (listenCommand() == VoiceCommand.Yes) endTrip() else voice.speak("계속 기록할게요.")
    }

    private suspend fun applyRefuelAmount(refuelId: Long, amountWon: Int) {
        if ((_overlay.value as? Overlay.RefuelAmount)?.refuel?.id == refuelId) _overlay.value = null
        recorder.setRefuelAmount(refuelId, amountWon)
        voice.speak("${won(amountWon)}으로 기록했어요.")
    }

    private suspend fun startTrip(headcount: Int) {
        when (val result = recorder.start(headcount)) {
            TripRecorder.StartResult.Started -> voice.speak("${headcount}명, 여행을 시작할게요.")
            is TripRecorder.StartResult.Failed -> {
                _overlay.value = Overlay.Message(result.reason)
                voice.speak(result.reason)
            }
        }
    }

    private suspend fun endTrip() {
        val tripId = recorder.end()
        if (tripId == null) {
            _overlay.value = Overlay.Message("여행은 정차 중(기어 P)에만 끝낼 수 있어요.")
            return
        }
        val summary = container.history.detail(tripId) ?: return
        _summary.value = summary
        val r = summary.result
        val distance = "총 %.0f킬로미터를 달렸어요.".format(r.distanceKm)
        voice.speak(
            if (summary.trip.headcount > 1) "$distance 한 사람당 ${won(r.perPersonWon)}이에요."
            else "$distance 비용은 ${won(r.totalCostWon)}이에요.",
        )
    }

    private var refuelPromptTimeout: Job? = null

    /** 금액을 누르기 시작하면 자동으로 닫히지 않게 한다. */
    fun onAmountInputStarted() {
        refuelPromptTimeout?.cancel()
        refuelPromptTimeout = null
    }

    /** 감지 때 금액을 놓친 주유·충전을 나중에 입력한다 (F3). 가장 최근 미입력 건부터. */
    fun onUnpaidRefuelClick() {
        val refuel = state.value.live?.refuels?.lastOrNull { it.amountWon == null } ?: return
        _overlay.value = Overlay.RefuelAmount(refuel)
    }

    // ---- 여행 명령 ----

    fun onStartClick() { _overlay.value = Overlay.Headcount }

    fun onHeadcountChosen(headcount: Int) {
        _overlay.value = null
        viewModelScope.launch { startTrip(headcount) }
    }

    fun onPause() { viewModelScope.launch { recorder.pause() } }
    fun onResume() { viewModelScope.launch { recorder.resume() } }

    fun onEndClick() { _overlay.value = Overlay.ConfirmEnd }

    fun onEndConfirmed() {
        _overlay.value = null
        viewModelScope.launch { endTrip() }
    }

    fun onExpenseClick() { _overlay.value = Overlay.Expense }

    fun onExpenseEntered(category: ExpenseCategory, amountWon: Int) {
        _overlay.value = null
        viewModelScope.launch { recorder.addExpense(category, amountWon, InputMethod.TOUCH) }
    }

    fun onRefuelAmountEntered(refuelId: Long, amountWon: Int) {
        _overlay.value = null
        voiceJob?.cancel() // 화면으로 답했으면 듣기를 멈춘다
        viewModelScope.launch { recorder.setRefuelAmount(refuelId, amountWon) }
    }

    fun dismissOverlay() { _overlay.value = null }
    fun dismissSummary() {
        _summary.value = null
        // 목록에서 열었으면 고친 내용이 목록에도 보이도록 다시 읽는다.
        if (_history.value != null) onHistoryClick()
    }

    // ---- 데모 모드 (F10) ----

    fun setDemoMode(enabled: Boolean) {
        viewModelScope.launch { container.settings.setDemoMode(enabled) }
    }

    fun demoDrive() = container.demoVehicle.drive(10.0)
    fun demoPark() = container.demoVehicle.park()
    fun demoAddEnergy(percent: Double) = container.demoVehicle.addEnergy(percent)

    /** 첫 값이 오기 전에도 combine이 화면을 그릴 수 있게 null로 시작한다. */
    private fun Flow<Double>.onStartNull(): Flow<Double?> =
        map<Double, Double?> { it }.onStart { emit(null) }

    companion object {
        private const val REFUEL_PROMPT_MS = 30_000L

        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(container) as T
        }
    }
}
