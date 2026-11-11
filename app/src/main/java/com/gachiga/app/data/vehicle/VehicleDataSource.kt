package com.gachiga.app.data.vehicle

import com.gachiga.app.domain.MotionState
import kotlinx.coroutines.flow.Flow

/** spec.md 3.1. 실제 차량(Pleos SDK)과 데모 모드가 같은 인터페이스를 쓴다. */
interface VehicleDataSource {
    /** 무엇을 읽을 수 있는지. 차종 이름이 아니라 capability 확인 결과로 판단한다 (intent.md 원칙 3). */
    suspend fun capabilities(): VehicleCapabilities

    /** 누적 주행거리 (km). */
    fun odometerKm(): Flow<Double>

    /** 연료 % 또는 배터리 SoC % (0..100). */
    fun energyPercent(): Flow<Double>

    fun motion(): Flow<MotionState>
}

data class VehicleCapabilities(
    val odometer: Boolean,
    val fuelPercent: Boolean,
    val evSoc: Boolean,
    val speedOrGear: Boolean,
) {
    val canRecordTrip: Boolean get() = odometer && (fuelPercent || evSoc)

    companion object {
        val NONE = VehicleCapabilities(odometer = false, fuelPercent = false, evSoc = false, speedOrGear = false)
    }
}
