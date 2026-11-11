package com.gachiga.app.data.vehicle

import com.gachiga.app.domain.EnergyType
import com.gachiga.app.domain.MotionState
import kotlinx.coroutines.flow.Flow

/** spec.md 3.1. 실제 차량(Pleos SDK)과 데모 모드가 같은 인터페이스를 쓴다. */
interface VehicleDataSource {
    /** 무엇을 읽을 수 있는지. 차종 이름이 아니라 capability 확인 결과로 판단한다 (intent.md 원칙 3). */
    suspend fun capabilities(): VehicleCapabilities

    /** 누적 주행거리 (km). */
    fun odometerKm(): Flow<Double>

    /** [type]이 FUEL이면 연료 %, EV면 배터리 SoC % (0..100). */
    fun energyPercent(type: EnergyType): Flow<Double>

    fun motion(): Flow<MotionState>
}

data class VehicleCapabilities(
    val odometer: Boolean,
    val fuelPercent: Boolean,
    val evSoc: Boolean,
    val speedOrGear: Boolean,
) {
    val canRecordTrip: Boolean get() = odometer && (fuelPercent || evSoc)

    /** spec.md 3.1: 하나만 읽히면 그쪽, 둘 다 읽히면 설정값, 둘 다 안 되면 null. */
    fun resolveEnergyType(preferred: EnergyType): EnergyType? = when {
        fuelPercent && evSoc -> preferred
        fuelPercent -> EnergyType.FUEL
        evSoc -> EnergyType.EV
        else -> null
    }

    companion object {
        val NONE = VehicleCapabilities(odometer = false, fuelPercent = false, evSoc = false, speedOrGear = false)
    }
}
