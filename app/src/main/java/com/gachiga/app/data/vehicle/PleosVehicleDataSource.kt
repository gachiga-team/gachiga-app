package com.gachiga.app.data.vehicle

import android.content.Context
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import com.gachiga.app.domain.MotionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Pleos Vehicle SDK 구현 (spec.md 3.1).
 *
 * 문서에서 확인된 것: Vehicle(context) → initialize() → getXxx() 로 도메인 객체 → checkXxxCapabilities
 * → AccessType이 READ/READ_WRITE일 때만 get/register → unregister → release().
 * 결과는 onComplete/onFailed 콜백으로 비동기로 온다.
 *
 * TODO(V2, 11/11): 아래 메서드의 정확한 시그니처와 단위를 에뮬레이터에서 확인한 뒤 callbackFlow로 감싼다.
 *  - Odometer: checkCurrentDistanceDrivenCapabilities / getCurrentDistanceDriven / registerDistanceDriven
 *  - Fuel: checkFuelPercentCapabilities / getFuelPercent / registerFuelPercent
 *  - EvBattery: checkEvBatteryStateOfChargeCapabilities / getEvBatteryStateOfCharge / registerEvBatteryStateOfCharge
 * TODO(V3): 속도·기어는 CarPropertyManager로 확인. 안 되면 주행거리 변화로 추정 (spec.md 3.2).
 *
 * 지금은 모든 값을 '지원 안 함'으로 돌려준다. 앱은 이 경우 데모 모드를 권한다.
 */
class PleosVehicleDataSource(context: Context) : VehicleDataSource {

    private val vehicle = Vehicle(context)
    private var initialized = false

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
        }
    }

    override suspend fun capabilities(): VehicleCapabilities = VehicleCapabilities.NONE

    override fun odometerKm(): Flow<Double> = emptyFlow()
    override fun energyPercent(): Flow<Double> = emptyFlow()
    override fun motion(): Flow<MotionState> = flowOf(MotionState.UNKNOWN)

    private companion object {
        const val TAG = "PleosVehicle"
    }
}
