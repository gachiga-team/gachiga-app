package com.gachiga.app.data.trip

import android.util.Log
import com.gachiga.app.data.settings.VehicleSettingsRepository
import com.gachiga.app.data.vehicle.VehicleDataSource
import com.gachiga.app.domain.EnergyTracker
import com.gachiga.app.domain.ExpenseCategory
import com.gachiga.app.domain.InputMethod
import com.gachiga.app.domain.MotionState
import com.gachiga.app.domain.RefuelEvent
import com.gachiga.app.domain.RefuelSource
import com.gachiga.app.domain.Settlement
import com.gachiga.app.domain.TripStatus
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 여행 기록기 (spec.md F1~F5).
 *
 * 진행 중인 여행이 있으면 차량 값(주행거리, 에너지 %, 주행/정차)을 구독해서
 * 열린 구간에 거리와 사용량을 쌓고, 주유·충전을 감지해 기록한다.
 * 모든 변경은 [mutex] 하나로 순서대로 처리한다.
 *
 * 앱 프로세스 범위(application scope)에서 돈다. 앱을 다시 열면 [attach]가 진행 중인 여행을 이어받는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripRecorder(
    private val dao: TripDao,
    private val settings: VehicleSettingsRepository,
    private val sourceFor: (demoMode: Boolean) -> VehicleDataSource,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val tracker = EnergyTracker()

    private val _live = MutableStateFlow<LiveTrip?>(null)
    val live: StateFlow<LiveTrip?> = _live.asStateFlow()

    private val _motion = MutableStateFlow(MotionState.UNKNOWN)
    private val _refuelDetected = MutableSharedFlow<Refuel>(extraBufferCapacity = 4)

    /** 새로 감지한 주유·충전. 화면은 이걸 받아 금액을 묻는다. */
    val refuelDetected: SharedFlow<Refuel> = _refuelDetected.asSharedFlow()

    private var trackingJob: Job? = null
    private var lastOdometerKm: Double? = null
    private var lastEnergyPercent: Double? = null
    private var dirty = false
    private var lastFlushAt = 0L

    sealed interface StartResult {
        data object Started : StartResult
        data class Failed(val reason: String) : StartResult
    }

    /** 앱 시작 시 한 번 부른다. 진행 중인 여행이 있으면 기록을 이어 간다 (F1 완료 조건). */
    fun attach() {
        scope.launch {
            mutex.withLock {
                val trip = dao.ongoingTrip() ?: return@withLock
                val segments = dao.segments(trip.id)
                _live.value = LiveTrip(trip, segments, dao.refuels(trip.id), dao.expenses(trip.id))
                lastOdometerKm = segments.lastOrNull()?.let { it.endOdometerKm ?: it.startOdometerKm }
                Log.i(TAG, "resumed trip ${trip.id} (${trip.status})")
            }
            startTracking()
        }
    }

    // ---- 명령 ----

    suspend fun start(headcount: Int): StartResult {
        val result = mutex.withLock { startLocked(headcount) }
        if (result is StartResult.Started) startTracking()
        return result
    }

    private suspend fun startLocked(headcount: Int): StartResult {
        if (_live.value != null) return StartResult.Failed("이미 진행 중인 여행이 있어요.")
        val s = settings.settings.first()
        val source = sourceFor(s.demoMode)
        val caps = source.capabilities()
        val energyType = caps.resolveEnergyType(s.energyType)
        if (!caps.odometer || energyType == null) {
            return StartResult.Failed("이 차량에서 주행거리나 연료·배터리 값을 읽지 못해 여행을 시작할 수 없어요.")
        }
        val odometer = withTimeoutOrNull(READ_TIMEOUT_MS) { source.odometerKm().first() }
            ?: return StartResult.Failed("주행거리를 읽지 못했어요. 잠시 뒤 다시 시도해 주세요.")
        val energy = withTimeoutOrNull(READ_TIMEOUT_MS) { source.energyPercent(energyType).first() }
            ?: return StartResult.Failed("연료·배터리 값을 읽지 못했어요. 잠시 뒤 다시 시도해 주세요.")

        val forType = s.copy(energyType = energyType)
        val now = clock()
        val trip = Trip(
            status = TripStatus.ACTIVE,
            headcount = headcount.coerceIn(1, 9),
            energyType = energyType,
            capacity = forType.capacity,
            defaultUnitPrice = forType.unitPrice,
            startedAt = now,
            startOdometerKm = odometer,
            startEnergyPercent = energy,
        )
        val tripId = dao.insertTrip(trip)
        val segment = Segment(tripId = tripId, startedAt = now, startOdometerKm = odometer, endOdometerKm = odometer)
        val segmentId = dao.insertSegment(segment)

        _live.value = LiveTrip(trip.copy(id = tripId), listOf(segment.copy(id = segmentId)), emptyList(), emptyList())
        lastOdometerKm = odometer
        lastEnergyPercent = energy
        Log.i(TAG, "started trip $tripId: ${headcount}명, $odometer km, $energy% ($energyType)")
        return StartResult.Started
    }

    suspend fun pause() = mutex.withLock {
        val live = _live.value ?: return@withLock
        if (live.trip.status != TripStatus.ACTIVE) return@withLock
        val closed = closeOpenSegment(live)
        val trip = live.trip.copy(status = TripStatus.PAUSED)
        dao.updateTrip(trip)
        _live.value = closed.copy(trip = trip)
    }

    suspend fun resume() = mutex.withLock {
        val live = _live.value ?: return@withLock
        if (live.trip.status != TripStatus.PAUSED) return@withLock
        val start = lastOdometerKm ?: live.segments.lastOrNull()?.endOdometerKm ?: live.trip.startOdometerKm
        val segment = Segment(tripId = live.trip.id, startedAt = clock(), startOdometerKm = start, endOdometerKm = start)
        val id = dao.insertSegment(segment)
        val trip = live.trip.copy(status = TripStatus.ACTIVE)
        dao.updateTrip(trip)
        _live.value = live.copy(trip = trip, segments = live.segments + segment.copy(id = id))
    }

    /** 정차 중에만 종료할 수 있다 (F5). 종료한 여행의 id를 돌려준다. */
    suspend fun end(): Long? {
        val tripId = mutex.withLock {
            val live = _live.value ?: return@withLock null
            if (_motion.value != MotionState.PARKED) return@withLock null
            val closed = if (live.trip.status == TripStatus.ACTIVE) closeOpenSegment(live) else live
            val trip = closed.trip.copy(
                status = TripStatus.ENDED,
                endedAt = clock(),
                endOdometerKm = lastOdometerKm,
                endEnergyPercent = lastEnergyPercent,
            )
            dao.updateTrip(trip)
            _live.value = null
            Log.i(TAG, "ended trip ${trip.id}")
            trip.id
        }
        if (tripId != null) {
            trackingJob?.cancel()
            trackingJob = null
        }
        return tripId
    }

    suspend fun addExpense(category: ExpenseCategory, amountWon: Int, input: InputMethod) = mutex.withLock {
        val live = _live.value ?: return@withLock
        val expense = Expense(tripId = live.trip.id, createdAt = clock(), category = category, amountWon = amountWon, input = input)
        val id = dao.insertExpense(expense)
        _live.value = live.copy(expenses = live.expenses + expense.copy(id = id))
    }

    suspend fun setRefuelAmount(refuelId: Long, amountWon: Int) = mutex.withLock {
        val live = _live.value ?: return@withLock
        val refuel = live.refuels.find { it.id == refuelId } ?: return@withLock
        val updated = refuel.copy(amountWon = amountWon)
        dao.updateRefuel(updated)
        _live.value = live.copy(refuels = live.refuels.map { if (it.id == refuelId) updated else it })
    }

    // ---- 기록 ----

    private sealed interface Event {
        data class Odometer(val km: Double) : Event
        data class Energy(val percent: Double) : Event
        data class Motion(val state: MotionState) : Event
        data object Tick : Event
    }

    private fun startTracking() {
        val live = _live.value ?: return
        trackingJob?.cancel()
        trackingJob = scope.launch {
            settings.settings.map { it.demoMode }.distinctUntilChanged().collectLatest { demoMode ->
                val source = sourceFor(demoMode)
                mutex.withLock { tracker.reset() }
                merge(
                    source.odometerKm().map { Event.Odometer(it) },
                    source.energyPercent(live.trip.energyType).map { Event.Energy(it) },
                    source.motion().map { Event.Motion(it) },
                    flow { while (true) { emit(Event.Tick); delay(TICK_MS) } },
                ).collect { event -> mutex.withLock { handle(event) } }
            }
        }
    }

    private suspend fun handle(event: Event) {
        if (_live.value == null) return
        val now = clock()
        when (event) {
            is Event.Odometer -> {
                lastOdometerKm = event.km
                updateOpenSegment { it.copy(endOdometerKm = event.km) }
            }
            is Event.Energy -> {
                lastEnergyPercent = event.percent
                val used = tracker.onEnergy(now, event.percent)
                // 일시정지 중에는 열린 구간이 없어서 사용량이 쌓이지 않는다 (F5)
                if (used > 0) updateOpenSegment { it.copy(energyUsedPercent = it.energyUsedPercent + used) }
            }
            is Event.Motion -> {
                _motion.value = event.state
                tracker.onMotion(event.state)?.let { recordRefuel(it) }
            }
            Event.Tick -> {
                tracker.tick(now)?.let { recordRefuel(it) }
                if (dirty && now - lastFlushAt >= FLUSH_MS) flush()
            }
        }
    }

    private fun updateOpenSegment(change: (Segment) -> Segment) {
        val live = _live.value ?: return
        val open = live.segments.lastOrNull()?.takeIf { it.endedAt == null } ?: return
        val updated = change(open)
        if (updated == open) return
        _live.value = live.copy(segments = live.segments.dropLast(1) + updated)
        dirty = true
    }

    /** DB 쓰기는 값이 바뀔 때만, 최소 10초 간격으로 모아서 한다 (F2). */
    private suspend fun flush() {
        val open = _live.value?.segments?.lastOrNull()?.takeIf { it.endedAt == null }
        if (open != null) dao.updateSegment(open)
        dirty = false
        lastFlushAt = clock()
    }

    private suspend fun closeOpenSegment(live: LiveTrip): LiveTrip {
        val open = live.segments.lastOrNull()?.takeIf { it.endedAt == null } ?: return live
        val closed = open.copy(endedAt = clock(), endOdometerKm = lastOdometerKm ?: open.endOdometerKm)
        dao.updateSegment(closed)
        dirty = false
        return live.copy(segments = live.segments.dropLast(1) + closed)
    }

    private suspend fun recordRefuel(event: RefuelEvent) {
        val live = _live.value ?: return
        val refuel = Refuel(
            tripId = live.trip.id,
            detectedAt = clock(),
            percentBefore = event.percentBefore,
            percentAfter = event.percentAfter,
            source = RefuelSource.DETECTED,
        )
        val saved = refuel.copy(id = dao.insertRefuel(refuel))
        _live.value = live.copy(refuels = live.refuels + saved)
        Log.i(TAG, "refuel detected: ${event.percentBefore}% → ${event.percentAfter}%")
        _refuelDetected.tryEmit(saved)
    }

    private companion object {
        const val TAG = "TripRecorder"
        const val READ_TIMEOUT_MS = 8_000L
        const val TICK_MS = 5_000L
        const val FLUSH_MS = 10_000L
    }
}
