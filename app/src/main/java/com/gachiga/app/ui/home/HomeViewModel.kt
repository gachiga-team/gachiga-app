package com.gachiga.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gachiga.app.AppContainer
import com.gachiga.app.BuildConfig
import com.gachiga.app.data.convoy.ConvoyException
import com.gachiga.app.data.convoy.ConvoyMessage
import com.gachiga.app.data.convoy.ConvoyState
import com.gachiga.app.data.navi.NaviEvent
import com.gachiga.app.data.settings.VehicleSettings
import com.gachiga.app.data.trip.LiveTrip
import com.gachiga.app.data.trip.Refuel
import com.gachiga.app.data.trip.TripDetail
import com.gachiga.app.data.trip.TripRecorder
import com.gachiga.app.data.vehicle.VehicleCapabilities
import com.gachiga.app.data.voice.VoiceIO
import com.gachiga.app.domain.ConvoyGap
import com.gachiga.app.domain.ConvoyMonitor
import com.gachiga.app.domain.VoiceCommand
import com.gachiga.app.domain.EnergyType
import com.gachiga.app.domain.ExpenseCategory
import com.gachiga.app.domain.FatigueTimer
import com.gachiga.app.domain.InputMethod
import com.gachiga.app.domain.KoreanJosa
import com.gachiga.app.domain.KoreanUnits
import com.gachiga.app.domain.MotionState
import com.gachiga.app.domain.Settlement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    /** 같이 가기: 그룹 번호 넣기 */
    data object GroupJoin : Overlay
    /** 같이 가기: 내 그룹 번호 보여 주기 */
    data class GroupCode(val code: String) : Overlay
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

    // ---- 내비 연동 (spec.md 9절 N1~N3) ----

    /** 마지막으로 받은 경로. 여행을 나중에 시작해도 목적지·통행료를 반영하려고 기억해 둔다. */
    private var lastRoute: NaviEvent.RouteStarted? = null
    private var batteryWarnedFor: String? = null

    init {
        viewModelScope.launch { container.navi.events.collect { onNaviEvent(it) } }
    }

    private fun onNaviEvent(event: NaviEvent) {
        when (event) {
            is NaviEvent.RouteStarted -> {
                lastRoute = event
                if (state.value.live != null) {
                    viewModelScope.launch { applyRoute(event.name, event.tollFare) }
                } else if (event.name.isNotBlank()) {
                    // N2: 경로를 잡았는데 여행 기록이 없으면 먼저 제안한다.
                    runVoice {
                        voice.speak("${KoreanJosa.withEuro(event.name)} 출발하시네요. 여행 기록을 시작할까요?")
                        if (listenCommand() == VoiceCommand.Yes) voiceStart(null)
                    }
                }
                warnArrivalBattery(event.name, event.arrivalCharge)
            }
            is NaviEvent.Destination -> {
                if (state.value.live != null && event.name.isNotBlank()) {
                    viewModelScope.launch { applyRoute(event.name, event.tollFare) }
                }
            }
            is NaviEvent.RouteState -> warnArrivalBattery(lastRoute?.name.orEmpty(), event.arrivalCharge)
            is NaviEvent.Arrived -> onArrived(event.name)
            NaviEvent.RouteEnded -> Unit
        }
    }

    /** N1: 예상 통행료를 기록하고, 새로 기록했으면 짧게 알려 준다. */
    private suspend fun applyRoute(name: String, tollFare: Int) {
        val recorded = recorder.applyNaviRoute(name, tollFare) ?: return
        announce("예상 통행료 ${won(recorded)}을 기록했어요.")
    }

    /**
     * N3: 전기차에서 도착 때 배터리가 15% 미만으로 예상되면 한 번 알려 준다.
     * 내비가 주는 값은 %가 아니라 **Wh**다 (에뮬레이터 확인 11/11: 49,454 = 내비 화면 63%). 용량으로 나눠 %로 바꾼다.
     */
    private fun warnArrivalBattery(destination: String, arrivalChargeWh: Int) {
        if (state.value.energyType != EnergyType.EV || arrivalChargeWh < 0) return
        val capacityKWh = state.value.live?.trip?.capacity ?: vehicleSettings.value.evCapacityKWh
        val percent = KoreanUnits.whToPercent(arrivalChargeWh, capacityKWh) ?: return
        if (percent >= LOW_ARRIVAL_CHARGE) return
        val key = "$destination:${percent / 5}"
        if (batteryWarnedFor == key) return
        batteryWarnedFor = key
        announce("도착할 때 배터리가 ${percent}%로 예상돼요. 가는 길에 충전하세요.")
    }

    /** N2: 목적지에 도착하면 여행을 끝낼지 묻는다. 주행 중이면 정차한 뒤 말해 달라고 한다. */
    private fun onArrived(name: String) {
        if (state.value.live == null) return
        runVoice {
            val place = name.ifBlank { "목적지" }
            if (state.value.motion != MotionState.PARKED) {
                voice.speak("${place}에 도착했어요. 정차한 뒤 여행 종료라고 말해 주세요.")
                return@runVoice
            }
            voice.speak("${place}에 도착했어요. 여행을 끝낼까요?")
            if (listenCommand() == VoiceCommand.Yes) endTrip() else voice.speak("계속 기록할게요.")
        }
    }

    private val speakLock = Mutex()

    /** 다른 음성 대화가 끝난 뒤에 안내를 말한다 (끼어들지 않게). 안내가 겹치면 차례로 말한다. */
    private fun announce(text: String) {
        viewModelScope.launch {
            voiceJob?.join()
            speakLock.withLock { voice.speak(text) }
        }
    }

    // ---- 같이 가기 (컨보이 모드) ----

    val convoy: StateFlow<ConvoyState?> = container.convoy.state

    init {
        // 일행 상태가 바뀌면 한 문장으로 알려 준다 (뒤처짐, 정차, 배터리 부족 등).
        viewModelScope.launch { container.convoy.alerts.collect { announce(it.spoken()) } }
        viewModelScope.launch { container.convoy.messages.collect { onConvoyMessage(it) } }
        // 끝난 여행의 인원이나 금액을 고치면 그룹 합산 정산에도 반영한다.
        viewModelScope.launch {
            _summary.collect { d ->
                val c = convoy.value
                if (d != null && c != null && c.done && c.tripId == d.trip.id) {
                    container.convoy.markDone(d.trip.id, d.result.totalCostWon, d.trip.headcount, d.result.distanceKm)
                }
            }
        }
        viewModelScope.launch { state.map { it.energyPercent }.distinctUntilChanged().collect { suggestRestIfLow(it) } }
    }

    private fun onConvoyMessage(m: ConvoyMessage) {
        when (m.kind) {
            "voice" -> announce("${m.from} 메시지예요. ${m.text}")
            "rest" -> viewModelScope.launch {
                // 함께 쉬기 제안: 다른 차량이 연료·배터리가 모자라 쉬어 가자고 한다.
                voiceJob?.join()
                runVoice {
                    voice.speak("${m.from}가 다음 휴게소에서 함께 쉬자고 해요. ${m.text}예요. 함께 쉴까요?")
                    if (listenCommand() == VoiceCommand.Yes) {
                        container.convoy.send("rest-ok", "함께 쉬어요")
                        voice.speak("함께 쉰다고 알렸어요.")
                    }
                }
            }
            "rest-ok" -> announce("${m.from}도 함께 쉬기로 했어요.")
            "left" -> announce("${m.from}가 그룹에서 나갔어요.")
        }
    }

    private var restAsked = false

    /** 함께 쉬기 제안: 내 차의 연료·배터리가 20% 아래로 내려가면 일행에게 쉬어 가자고 할지 한 번 묻는다. */
    private fun suggestRestIfLow(percent: Double?) {
        percent ?: return
        if (percent >= LOW_ENERGY + 5) restAsked = false
        val c = convoy.value ?: return
        if (percent >= LOW_ENERGY || restAsked || c.done || c.others.isEmpty() || state.value.live == null) return
        restAsked = true
        viewModelScope.launch {
            voiceJob?.join()
            runVoice {
                val label = state.value.energyType.label()
                voice.speak("${label}가 ${percent.toInt()}퍼센트예요. 일행에게 다음 휴게소에서 함께 쉬자고 할까요?")
                if (listenCommand() == VoiceCommand.Yes) {
                    val ok = container.convoy.send("rest", "$label ${percent.toInt()}퍼센트")
                    voice.speak(if (ok) "일행에게 알렸어요." else "지금은 일행에게 알리지 못했어요.")
                }
            }
        }
    }

    /** 위치 권한 동의 창을 띄워 달라는 요청. 화면이 받아서 시스템 창을 연다. */
    private val _permissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val permissionRequests: SharedFlow<Unit> = _permissionRequests.asSharedFlow()
    private var afterPermission: (suspend () -> Unit)? = null

    /** 위치를 나누려면 사용자 동의가 필요하다. 아직이면 먼저 묻고, 답과 상관없이 [block]을 이어서 한다. */
    private fun withLocationPermission(block: suspend () -> Unit) {
        if (state.value.demoMode || state.value.locked || container.hasLocationPermission()) {
            viewModelScope.launch { block() }
            return
        }
        afterPermission = block
        _permissionRequests.tryEmit(Unit)
    }

    fun onLocationPermissionResult(granted: Boolean) {
        val block = afterPermission
        afterPermission = null
        viewModelScope.launch {
            block?.invoke()
            if (!granted) announce("위치 권한이 없어서 내 위치는 나누지 않아요.")
        }
    }

    /** 그룹에는 들어왔지만 위치 권한이 없을 때 누르는 버튼. */
    fun onShareLocationClick() = withLocationPermission { }

    /** 그룹 화면에 "위치 나누기 켜기" 버튼을 보일지. */
    fun needsLocationPermission(): Boolean = !state.value.demoMode && !container.hasLocationPermission()

    fun onGroupCreateClick() = withLocationPermission { createGroup() }
    fun onGroupJoinClick() { _overlay.value = Overlay.GroupJoin }
    fun onGroupCodeClick() { convoy.value?.let { _overlay.value = Overlay.GroupCode(it.code) } }
    fun onGroupLeaveClick() { viewModelScope.launch { leaveGroup() } }

    fun onGroupCodeEntered(code: String) {
        _overlay.value = null
        withLocationPermission { joinGroup(code) }
    }

    /** 일행에게 말하기: 한 문장을 듣고 일행 차량에서 읽어 주게 한다. */
    fun onGroupTalkClick() = runVoice {
        voice.speak("일행에게 전할 말을 해 주세요.")
        val text = listenText()?.trim()
        if (text.isNullOrBlank()) voice.speak("잘 못 들었어요.") else sendMessage(text)
    }

    /** 정차 중이면 화면에도 보여 주고, 항상 음성으로 말한다. */
    private suspend fun tell(text: String) {
        if (!state.value.locked) _overlay.value = Overlay.Message(text)
        voice.speak(text)
    }

    private suspend fun createGroup() {
        if (state.value.live == null) { tell("여행을 시작한 뒤에 그룹을 만들 수 있어요."); return }
        convoy.value?.let { tell("이미 그룹 ${spell(it.code)}에 있어요."); return }
        container.convoy.create().fold(
            onSuccess = { t ->
                if (!state.value.locked) _overlay.value = Overlay.GroupCode(t.code)
                voice.speak("그룹을 만들었어요. 그룹 번호는 ${spell(t.code)}예요.")
            },
            onFailure = { tell(groupError(it)) },
        )
    }

    private suspend fun joinGroup(code: String?) {
        if (state.value.live == null) { tell("여행을 시작한 뒤에 그룹에 참여할 수 있어요."); return }
        convoy.value?.let { tell("이미 그룹 ${spell(it.code)}에 있어요."); return }
        if (code == null) {
            if (!state.value.locked) _overlay.value = Overlay.GroupJoin
            else voice.speak("그룹 번호 네 자리를 함께 말해 주세요. 예를 들면 그룹 참여 일 이 삼 사.")
            return
        }
        container.convoy.join(code).fold(
            onSuccess = { t -> voice.speak("${t.label}로 그룹에 참여했어요. 여행하는 동안 일행과 위치를 나눠요.") },
            onFailure = { tell(groupError(it)) },
        )
    }

    private suspend fun leaveGroup() {
        if (convoy.value == null) { voice.speak("참여한 그룹이 없어요."); return }
        container.convoy.leave()
        voice.speak("그룹에서 나왔어요. 위치 공유를 멈췄어요.")
    }

    private suspend fun sendMessage(text: String) {
        if (convoy.value == null) { voice.speak("참여한 그룹이 없어요."); return }
        val ok = container.convoy.send("voice", text)
        voice.speak(if (ok) "일행에게 전했어요." else "지금은 전하지 못했어요.")
    }

    private fun groupError(e: Throwable): String = when ((e as? ConvoyException)?.reason) {
        ConvoyException.Reason.NO_ROOM -> "그 번호의 그룹을 찾지 못했어요."
        ConvoyException.Reason.FULL -> "그룹이 가득 찼어요."
        else -> "일행 서버에 연결하지 못했어요. 잠시 뒤에 다시 해 주세요."
    }

    /** "일행 어디야"에 답한다. */
    private fun groupStatusSpoken(c: ConvoyState): String {
        if (c.others.isEmpty()) return "아직 그룹에 다른 차량이 없어요. 그룹 번호는 ${spell(c.code)}예요."
        return c.others.joinToString(" ") { o ->
            val gap = c.me?.let { ConvoyMonitor.gap(it, o) }
            when {
                o.done -> "${o.label}는 여행을 끝냈어요."
                gap == null -> "${o.label} 위치는 아직 몰라요."
                else -> "${o.label}는 ${spokenGap(gap)}"
            }
        }
    }

    private fun spokenGap(gap: ConvoyGap): String {
        val d = if (gap.meters < 1000) "%.0f미터".format(gap.meters) else "%.1f킬로미터".format(gap.meters / 1000)
        return when (gap.ahead) {
            true -> "$d 앞에 있어요."
            false -> "$d 뒤에 있어요."
            null -> "$d 떨어져 있어요."
        }
    }

    /** 그룹 번호를 한 자리씩 읽게 한다. */
    private fun spell(code: String) = code.toCharArray().joinToString(", ")

    /** debug 빌드 전용: 내비에 테스트 경로를 건다. */
    fun onTestRouteClick() = container.navi.requestTestRoute(37.7639, 128.8994, "강릉역")

    /** 끝난 여행에서 잘못 들어간 비용(예: 내비 예상 통행료)을 지운다. */
    fun onEndedExpenseDelete(expenseId: Long) {
        val tripId = _summary.value?.trip?.id ?: return
        viewModelScope.launch { _summary.value = container.history.deleteExpense(tripId, expenseId) }
    }

    // ---- 장시간 운전 휴식 안내 (spec.md 9절 N4) ----

    /** release는 2시간(이후 1시간마다), debug는 시연·확인용으로 2분(이후 2분마다). 10분(debug 1분) 넘게 쉬면 초기화. */
    private val fatigue = if (BuildConfig.DEBUG) {
        FatigueTimer(limitMs = 2 * FatigueTimer.MINUTE, repeatMs = 2 * FatigueTimer.MINUTE, restMs = FatigueTimer.MINUTE)
    } else {
        FatigueTimer()
    }

    init {
        viewModelScope.launch {
            state.map { it.motion }.distinctUntilChanged().collect { fatigue.onMotion(System.currentTimeMillis(), it) }
        }
        viewModelScope.launch {
            while (true) {
                delay(FATIGUE_TICK_MS)
                fatigue.tick(System.currentTimeMillis())?.let { minutes ->
                    announce("${FatigueTimer.spoken(minutes)} 동안 운전하셨어요. 다음 휴게소에서 잠시 쉬어 가세요.")
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
            VoiceCommand.CreateGroup -> createGroup()
            is VoiceCommand.JoinGroup -> joinGroup(command.code)
            VoiceCommand.LeaveGroup -> leaveGroup()
            VoiceCommand.GroupStatus -> voice.speak(convoy.value?.let { groupStatusSpoken(it) } ?: "참여한 그룹이 없어요.")
            is VoiceCommand.SendMessage -> sendMessage(command.text)
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
        // 지난 여행의 그룹(정산용으로 남아 있던 것)은 새 여행을 시작할 때 정리한다.
        if (state.value.live == null && convoy.value?.done == true) container.convoy.leave()
        when (val result = recorder.start(headcount)) {
            TripRecorder.StartResult.Started -> {
                voice.speak("${headcount}명, 여행을 시작할게요.")
                // N1·N2: 이미 내비 경로가 걸려 있으면 목적지와 예상 통행료를 바로 반영한다.
                lastRoute?.let { applyRoute(it.name, it.tollFare) }
                container.navi.refresh()
            }
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
        val r = summary.result
        // 같이 가기: 확정된 금액만 일행에게 남기고 위치 공유를 멈춘다.
        container.convoy.markDone(tripId, r.totalCostWon, summary.trip.headcount, r.distanceKm)
        _summary.value = summary
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

    fun demoDrive() {
        container.demoVehicle.drive(10.0)
        container.demoLocation.advance(10.0)
    }
    fun demoPark() = container.demoVehicle.park()
    fun demoAddEnergy(percent: Double) = container.demoVehicle.addEnergy(percent)

    /** 첫 값이 오기 전에도 combine이 화면을 그릴 수 있게 null로 시작한다. */
    private fun Flow<Double>.onStartNull(): Flow<Double?> =
        map<Double, Double?> { it }.onStart { emit(null) }

    companion object {
        private const val REFUEL_PROMPT_MS = 30_000L
        private const val LOW_ARRIVAL_CHARGE = 15
        private const val LOW_ENERGY = 20
        private const val FATIGUE_TICK_MS = 15_000L

        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(container) as T
        }
    }
}
