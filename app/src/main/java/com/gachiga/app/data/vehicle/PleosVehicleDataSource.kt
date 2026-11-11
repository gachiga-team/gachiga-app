package com.gachiga.app.data.vehicle

import android.content.Context
import android.os.SystemClock
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import com.gachiga.app.domain.EnergyType
import com.gachiga.app.domain.MotionState
import com.gachiga.app.domain.OdometerMotionEstimator
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Pleos Vehicle SDK 2.3.2 구현 (spec.md 3.1, 3.2).
 *
 * 사용 순서: Vehicle(context) → initialize() → getXxx() → checkXxxCapabilities
 * → AccessType이 READ/READ_WRITE일 때만 get/register → unregister → release().
 * 모든 결과는 onComplete/onFailed 콜백으로 비동기로 온다.
 *
 * 시그니처는 SDK 클래스 파일에서 확인했다 (2026-11-11):
 * - Odometer.getCurrentDistanceDriven → Float, registerDistanceDriven(DistanceDrivenListener)
 * - Fuel.getFuelPercent → Int, EvBattery.getEvBatteryStateOfCharge → Int
 * - Gear.getCurrentGearSelectionState → VehicleGear (문서에 없는 도메인. 권한 CAR_DRIVE, capability 확인 API 없음)
 */
class PleosVehicleDataSource(context: Context) : VehicleDataSource {

    private val vehicle = Vehicle(context)
    private var initialized = false

    @Volatile private var cachedCapabilities: VehicleCapabilities? = null

    /** 실패해도 앱이 죽지 않게 한다 (intent.md 원칙 3). 실패하면 모든 값을 '지원 안 함'으로 둔다. */
    fun initialize() {
        if (!initialized) {
            initialized = runCatching { vehicle.initialize() }
                .onFailure { Log.w(TAG, "Vehicle SDK initialize failed", it) }
                .isSuccess
        }
    }

    /** 비동기 콜백이 모두 끝난 뒤에 부른다. */
    fun release() {
        if (initialized) {
            vehicle.release()
            initialized = false
            cachedCapabilities = null
        }
    }

    override suspend fun capabilities(): VehicleCapabilities {
        if (!initialized) return VehicleCapabilities.NONE
        cachedCapabilities?.let { return it }

        // initialize() 직후에는 차량 서비스 연결이 끝나지 않아 첫 호출이 시간 초과될 수 있다 (2026-11-11 에뮬레이터에서 확인).
        // 시간 초과는 '모름(null)'으로 두고, 모르는 항목이 남아 있으면 확정하지 않고 다시 묻는다.
        var odometer: Boolean? = null
        var fuel: Boolean? = null
        var evSoc: Boolean? = null
        var gear: Boolean? = null
        for (attempt in 1..CAPABILITY_ATTEMPTS) {
            if (odometer == null) odometer = readable("odometer") { ok, fail -> vehicle.getOdometer().checkCurrentDistanceDrivenCapabilities(ok, fail) }
            if (fuel == null) fuel = readable("fuel") { ok, fail -> vehicle.getFuel().checkFuelPercentCapabilities(ok, fail) }
            if (evSoc == null) evSoc = readable("evSoc") { ok, fail -> vehicle.getEvBattery().checkEvBatteryStateOfChargeCapabilities(ok, fail) }
            // Gear에는 capability 확인 API가 없어서 한 번 읽어 보고 판단한다.
            if (gear == null) gear = tryCall { readGear() != VehicleGear.GEAR_UNKNOWN }
            Log.i(TAG, "capabilities (attempt $attempt): odometer=$odometer fuel=$fuel evSoc=$evSoc gear=$gear")
            if (listOf(odometer, fuel, evSoc, gear).none { it == null }) break
            delay(CAPABILITY_RETRY_MS)
        }
        val caps = VehicleCapabilities(
            odometer = odometer == true,
            fuelPercent = fuel == true,
            evSoc = evSoc == true,
            speedOrGear = gear == true,
        )
        if (listOf(odometer, fuel, evSoc, gear).none { it == null }) cachedCapabilities = caps
        return caps
    }

    override fun odometerKm(): Flow<Double> = callbackFlow {
        val odometer = vehicle.getOdometer()
        runCatching { await<Float?> { ok, fail -> odometer.getCurrentDistanceDriven(ok, fail) } }
            .onSuccess { Log.d(TAG, "odometer get=$it"); it?.let { v -> trySend(v.toDouble()) } }
            .onFailure { Log.w(TAG, "odometer get failed", it) }

        val listener = object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(value: Float?) {
                Log.d(TAG, "odometer update=$value")
                value?.let { trySend(it.toDouble()) }
            }
            override fun onFailed(e: Exception) { Log.w(TAG, "odometer listener failed", e) }
        }
        odometer.registerDistanceDriven(listener)
        awaitClose { odometer.unregisterDistanceDriven(listener) }
    }

    override fun energyPercent(type: EnergyType): Flow<Double> =
        if (type == EnergyType.FUEL) fuelPercent() else evSoc()

    private fun fuelPercent(): Flow<Double> = callbackFlow {
        val fuel = vehicle.getFuel()
        runCatching { await<Int?> { ok, fail -> fuel.getFuelPercent(ok, fail) } }
            .onSuccess { Log.d(TAG, "fuel get=$it"); it?.let { v -> trySend(v.toDouble()) } }
            .onFailure { Log.w(TAG, "fuel get failed", it) }

        val listener = object : FuelPercentListener {
            override fun onFuelPercentUpdated(value: Int?) {
                Log.d(TAG, "fuel update=$value")
                value?.let { trySend(it.toDouble()) }
            }
            override fun onFailed(e: Exception) { Log.w(TAG, "fuel listener failed", e) }
        }
        fuel.registerFuelPercent(listener)
        awaitClose { fuel.unregisterFuelPercent(listener) }
    }

    private fun evSoc(): Flow<Double> = callbackFlow {
        val battery = vehicle.getEvBattery()
        runCatching { await<Int?> { ok, fail -> battery.getEvBatteryStateOfCharge(ok, fail) } }
            .onSuccess { Log.d(TAG, "evSoc get=$it"); it?.let { v -> trySend(v.toDouble()) } }
            .onFailure { Log.w(TAG, "evSoc get failed", it) }

        val listener = object : EvBatteryStateOfChargeListener {
            override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
                Log.d(TAG, "evSoc update=$value")
                value?.let { trySend(it.toDouble()) }
            }
            override fun onFailed(e: Exception) { Log.w(TAG, "evSoc listener failed", e) }
        }
        battery.registerEvBatteryStateOfCharge(listener)
        awaitClose { battery.unregisterEvBatteryStateOfCharge(listener) }
    }

    /** spec.md 3.2: 기어를 읽을 수 있으면 기어로, 아니면 주행거리 변화로 판별한다. */
    override fun motion(): Flow<MotionState> = flow {
        val caps = capabilities()
        when {
            caps.speedOrGear -> emitAll(gearMotion())
            caps.odometer -> emitAll(estimatedMotion())
            else -> emit(MotionState.UNKNOWN)
        }
    }.distinctUntilChanged()

    private fun gearMotion(): Flow<MotionState> = callbackFlow {
        val gear = vehicle.getGear()
        runCatching { readGear() }.onSuccess { trySend(it.toMotion()) }

        val listener = object : GearSelectionListener {
            override fun onSelected(value: VehicleGear?) {
                Log.d(TAG, "gear update=$value")
                trySend(value?.toMotion() ?: MotionState.UNKNOWN)
            }
            override fun onFailed(e: Exception) { Log.w(TAG, "gear listener failed", e) }
        }
        gear.registerGearSelection(listener)
        awaitClose { gear.unregisterGearSelection(listener) }
    }

    private fun estimatedMotion(): Flow<MotionState> = channelFlow {
        val estimator = OdometerMotionEstimator()
        launch {
            odometerKm().collect { km -> send(estimator.onOdometer(SystemClock.elapsedRealtime(), km)) }
        }
        while (isActive) {
            delay(ESTIMATE_TICK_MS)
            send(estimator.evaluate(SystemClock.elapsedRealtime()))
        }
    }

    private suspend fun readGear(): VehicleGear =
        await<VehicleGear?> { ok, fail -> vehicle.getGear().getCurrentGearSelectionState(ok, fail) } ?: VehicleGear.GEAR_UNKNOWN

    private suspend fun readable(
        name: String,
        check: (onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit) -> Unit,
    ): Boolean? = tryCall {
        val config = await(check)
        config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE
    }.also { if (it == null) Log.w(TAG, "capability check timed out: $name") }

    /** 성공하면 결과, SDK가 실패를 알려 주면 false, 시간 초과(아직 연결 중)면 null. */
    private suspend fun tryCall(block: suspend () -> Boolean): Boolean? = try {
        block()
    } catch (e: TimeoutCancellationException) {
        null
    } catch (e: CancellationException) {
        throw e // 화면이 닫혀 취소된 것은 실패가 아니다
    } catch (e: Exception) {
        Log.w(TAG, "vehicle call failed: ${e.message}")
        false
    }

    /** onComplete/onFailed 콜백 API를 suspend 함수로 바꾼다. */
    private suspend fun <T> await(
        call: (onComplete: (T) -> Unit, onFailed: (Exception) -> Unit) -> Unit,
    ): T = withTimeout(CALL_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            call(
                { value -> if (cont.isActive) cont.resume(value) },
                { e -> if (cont.isActive) cont.resumeWithException(e) },
            )
        }
    }

    private fun VehicleGear.toMotion(): MotionState = when (this) {
        VehicleGear.GEAR_PARK -> MotionState.PARKED
        VehicleGear.GEAR_UNKNOWN -> MotionState.UNKNOWN
        // N, R, D, 수동 단수는 모두 움직일 수 있는 상태로 보고 입력을 잠근다 (intent.md 원칙 1)
        else -> MotionState.DRIVING
    }

    private companion object {
        const val TAG = "PleosVehicle"
        const val CAPABILITY_ATTEMPTS = 5
        const val CAPABILITY_RETRY_MS = 1_000L
        const val CALL_TIMEOUT_MS = 3_000L
        const val ESTIMATE_TICK_MS = 5_000L
    }
}
