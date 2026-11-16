package com.gachiga.app.data.convoy

import android.content.Context
import android.util.Log
import com.gachiga.app.data.location.CarLocation
import com.gachiga.app.data.location.LocationSource
import com.gachiga.app.data.settings.VehicleSettingsRepository
import com.gachiga.app.data.trip.TripRecorder
import com.gachiga.app.data.vehicle.VehicleDataSource
import com.gachiga.app.domain.ConvoyAlert
import com.gachiga.app.domain.ConvoyMember
import com.gachiga.app.domain.ConvoyMonitor
import com.gachiga.app.domain.EnergyType
import com.gachiga.app.domain.MotionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 화면에 보여 줄 그룹 상태. */
data class ConvoyState(
    val code: String,
    val myId: String,
    val myLabel: String,
    /** 이 그룹으로 기록한 여행. 정산 화면에서 그룹 합산을 보여 줄지 정한다. */
    val tripId: Long?,
    /** 나를 뺀 일행 차량의 최근 상태 */
    val others: List<ConvoyMember> = emptyList(),
    val me: ConvoyMember? = null,
    /** 서버와 마지막 주고받기에 성공했는지 */
    val online: Boolean = true,
    /** 지금 내 위치를 일행과 나누고 있는지 (여행 중에만) */
    val sharingLocation: Boolean = false,
    /** 내 차량이 여행을 끝내 금액이 확정됐는지 */
    val done: Boolean = false,
    val lastMessage: ConvoyMessage? = null,
)

/**
 * 같이 가기(컨보이) 세션.
 *
 * - 그룹에 있는 동안 3초마다 내 최근 상태를 올리고 일행 상태를 받는다.
 * - 위치는 **여행을 기록하는 동안에만** 올린다. 여행을 끝내면 금액만 남기고 위치 공유를 멈춘다.
 * - 위치 이력은 어디에도 저장하지 않는다. 저장하는 것은 그룹 번호와 참여 표뿐이다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConvoyManager(
    context: Context,
    private val api: ConvoyApi,
    private val scope: CoroutineScope,
    private val settings: VehicleSettingsRepository,
    private val vehicleSource: (demoMode: Boolean) -> VehicleDataSource,
    private val locationSource: (demoMode: Boolean) -> LocationSource,
    private val recorder: TripRecorder,
) {
    private val prefs = context.getSharedPreferences("convoy", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<ConvoyState?>(null)
    val state: StateFlow<ConvoyState?> = _state.asStateFlow()

    private val _alerts = MutableSharedFlow<ConvoyAlert>(extraBufferCapacity = 16)
    val alerts: SharedFlow<ConvoyAlert> = _alerts.asSharedFlow()

    /** 다른 차량이 보낸 메시지 (음성 메시지, 함께 쉬기 제안, 나감). */
    private val _messages = MutableSharedFlow<ConvoyMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<ConvoyMessage> = _messages.asSharedFlow()

    private var ticket: ConvoyTicket? = null
    private var lastSeq = 0L
    private var fresh = true
    private var done: Done? = null
    private var loop: Job? = null
    private val monitor = ConvoyMonitor()

    private data class Done(val costWon: Long, val headcount: Int, val tripKm: Double)
    private data class Car(val location: CarLocation?, val energyPercent: Double?, val motion: MotionState, val ev: Boolean)

    @Volatile private var car = Car(null, null, MotionState.UNKNOWN, false)

    /** 앱을 다시 켰을 때 저장해 둔 그룹이 있으면 이어 간다. */
    fun attach() {
        val code = prefs.getString(K_CODE, null) ?: return
        val key = prefs.getString(K_KEY, null) ?: return
        ticket = ConvoyTicket(code, key, prefs.getString(K_ID, "").orEmpty(), prefs.getString(K_LABEL, "").orEmpty())
        lastSeq = prefs.getLong(K_SEQ, 0)
        fresh = false
        if (prefs.getBoolean(K_DONE, false)) {
            done = Done(prefs.getLong(K_DONE_COST, 0), prefs.getInt(K_DONE_HEADCOUNT, 1), prefs.getFloat(K_DONE_KM, 0f).toDouble())
        }
        val tripId = prefs.getLong(K_TRIP, -1).takeIf { it >= 0 }
        _state.value = ConvoyState(code, ticket!!.id, ticket!!.label, tripId, done = done != null)
        startLoop()
    }

    suspend fun create(): Result<ConvoyTicket> = enter { api.create() }

    suspend fun join(code: String): Result<ConvoyTicket> = enter { api.join(code) }

    private suspend fun enter(request: suspend () -> ConvoyTicket): Result<ConvoyTicket> = mutex.withLock {
        if (ticket != null) return@withLock Result.failure(IllegalStateException("already in group"))
        val t = try { request() } catch (e: ConvoyException) { return@withLock Result.failure(e) }
        val tripId = recorder.live.value?.trip?.id
        ticket = t
        lastSeq = 0
        fresh = true
        done = null
        monitor.reset()
        prefs.edit().clear()
            .putString(K_CODE, t.code).putString(K_KEY, t.key).putString(K_ID, t.id).putString(K_LABEL, t.label)
            .putLong(K_TRIP, tripId ?: -1).apply()
        _state.value = ConvoyState(t.code, t.id, t.label, tripId)
        startLoop()
        Result.success(t)
    }

    /** 그룹에서 나간다. 서버에 있는 내 위치를 지우고, 이 기기에 저장한 그룹 정보도 지운다. */
    suspend fun leave() = mutex.withLock {
        val t = ticket ?: return@withLock
        loop?.cancel()
        loop = null
        runCatching { api.leave(t) }
        clear()
    }

    /** 일행에게 말을 전한다. 실패하면 false. */
    suspend fun send(kind: String, text: String): Boolean {
        val t = ticket ?: return false
        return runCatching { api.send(t, kind, text) }.isSuccess
    }

    /**
     * 내 차량이 여행을 끝냈다. 확정된 금액만 올리고 위치 공유는 멈춘다.
     * 끝난 뒤에 인원이나 금액을 고치면 다시 부른다.
     */
    fun markDone(tripId: Long, costWon: Long, headcount: Int, tripKm: Double) {
        val s = _state.value ?: return
        if (s.tripId != null && s.tripId != tripId) return
        done = Done(costWon, headcount, tripKm)
        prefs.edit().putBoolean(K_DONE, true).putLong(K_DONE_COST, costWon).putInt(K_DONE_HEADCOUNT, headcount)
            .putFloat(K_DONE_KM, tripKm.toFloat()).putLong(K_TRIP, tripId).apply()
        _state.update { it?.copy(tripId = tripId, done = true, sharingLocation = false) }
    }

    private fun clear() {
        ticket = null
        done = null
        prefs.edit().clear().apply()
        monitor.reset()
        _state.value = null
    }

    private fun startLoop() {
        loop?.cancel()
        loop = scope.launch {
            val watcher = launch { watchCar() }
            while (isActive) {
                val t = ticket ?: break
                try {
                    val upload = upload()
                    val snapshot = api.sync(t, upload, lastSeq)
                    onSnapshot(t, upload, snapshot)
                } catch (e: ConvoyException) {
                    if (e.reason == ConvoyException.Reason.NOT_MEMBER || e.reason == ConvoyException.Reason.NO_ROOM) {
                        // 그룹이 없어졌다 (서버 재시작, 12시간 경과). 조용히 정리한다.
                        Log.i(TAG, "group is gone: ${e.reason}")
                        clear()
                        break
                    }
                    _state.update { it?.copy(online = false) }
                }
                delay(if (done != null) DONE_INTERVAL_MS else INTERVAL_MS)
            }
            watcher.cancel()
        }
    }

    /** 차량 값과 위치의 최신 값을 기억해 둔다. 그룹에 있는 동안에만 읽는다. */
    private suspend fun watchCar() {
        settings.settings.flatMapLatest { s ->
            val vehicle = vehicleSource(s.demoMode)
            val location = locationSource(s.demoMode)
            flow { emit(vehicle.capabilities()) }.flatMapLatest { caps ->
                val type = caps.resolveEnergyType(s.energyType) ?: s.energyType
                combine(
                    location.locations().map<CarLocation, CarLocation?> { it }.onStart { emit(null) },
                    vehicle.energyPercent(type).map<Double, Double?> { it }.onStart { emit(null) },
                    vehicle.motion().onStart { emit(MotionState.UNKNOWN) },
                ) { loc, energy, motion -> Car(loc, energy, motion, type == EnergyType.EV) }
            }
        }.collect { car = it }
    }

    private fun upload(): ConvoyUpload {
        val c = car
        val d = done
        val live = recorder.live.value
        val moving = when (c.motion) {
            MotionState.DRIVING -> true
            MotionState.PARKED -> false
            MotionState.UNKNOWN -> null
        }
        if (d != null) {
            return ConvoyUpload(null, null, null, null, c.energyPercent?.toInt(), c.ev, d.tripKm, d.costWon, d.headcount, done = true)
        }
        // 위치는 여행을 기록하는 동안에만 나눈다.
        val loc = c.location.takeIf { live != null }
        return ConvoyUpload(
            lat = loc?.lat,
            lon = loc?.lon,
            heading = loc?.heading,
            moving = moving,
            energyPercent = c.energyPercent?.toInt(),
            ev = c.ev,
            tripKm = live?.distanceKm ?: 0.0,
            costWon = live?.settlement()?.totalCostWon ?: 0,
            headcount = live?.trip?.headcount ?: 1,
            done = false,
        )
    }

    private fun onSnapshot(t: ConvoyTicket, upload: ConvoyUpload, snapshot: ConvoySnapshot) {
        val me = ConvoyMember(
            id = t.id, label = t.label, lat = upload.lat, lon = upload.lon, heading = upload.heading, moving = upload.moving,
            energyPercent = upload.energyPercent, ev = upload.ev, tripKm = upload.tripKm, costWon = upload.costWon,
            headcount = upload.headcount, done = upload.done,
        )
        val others = snapshot.members.filter { it.id != t.id }
        if (!upload.done) monitor.update(me, others).forEach { _alerts.tryEmit(it) }

        val incoming = snapshot.messages.filter { it.fromId != t.id }
        // 방금 들어온 그룹의 지난 메시지는 읽어 주지 않는다.
        if (!fresh) incoming.forEach { _messages.tryEmit(it) }
        fresh = false
        if (snapshot.seq != lastSeq) {
            lastSeq = snapshot.seq
            prefs.edit().putLong(K_SEQ, lastSeq).apply()
        }
        _state.update { s ->
            s?.copy(
                others = others,
                me = me,
                online = true,
                sharingLocation = upload.lat != null,
                lastMessage = incoming.lastOrNull { it.kind != "left" } ?: s.lastMessage,
            )
        }
    }

    private companion object {
        const val TAG = "Convoy"
        const val INTERVAL_MS = 3_000L
        const val DONE_INTERVAL_MS = 10_000L
        const val K_CODE = "code"
        const val K_KEY = "key"
        const val K_ID = "id"
        const val K_LABEL = "label"
        const val K_SEQ = "seq"
        const val K_TRIP = "trip"
        const val K_DONE = "done"
        const val K_DONE_COST = "done_cost"
        const val K_DONE_HEADCOUNT = "done_headcount"
        const val K_DONE_KM = "done_km"
    }
}
