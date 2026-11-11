package com.gachiga.app.data.vehicle

import com.gachiga.app.domain.MotionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 데모 모드 (spec.md F10). Virtual Console로 바꿀 수 없는 값을 앱 안에서 시뮬레이션한다.
 * 이 소스를 쓰는 동안 화면에 항상 "데모 모드" 배지를 보여 준다.
 */
class DemoVehicleDataSource(
    startOdometerKm: Double = 12_345.0,
    startEnergyPercent: Double = 60.0,
    /** 1 km 주행당 에너지 % 감소량. 50L 탱크, 약 14 km/L 가정 → 1 km ≈ 0.14% */
    private val percentPerKm: Double = 0.14,
) : VehicleDataSource {

    private val odometer = MutableStateFlow(startOdometerKm)
    private val energy = MutableStateFlow(startEnergyPercent)
    private val motion = MutableStateFlow(MotionState.PARKED)

    override suspend fun capabilities() =
        VehicleCapabilities(odometer = true, fuelPercent = true, evSoc = true, speedOrGear = true)

    override fun odometerKm(): Flow<Double> = odometer.asStateFlow()
    override fun energyPercent(): Flow<Double> = energy.asStateFlow()
    override fun motion(): Flow<MotionState> = motion.asStateFlow()

    /** "10 km 주행" 버튼 */
    fun drive(km: Double) {
        motion.value = MotionState.DRIVING
        odometer.update { it + km }
        energy.update { (it - km * percentPerKm).coerceAtLeast(0.0) }
    }

    /** "정차" 버튼 */
    fun park() {
        motion.value = MotionState.PARKED
    }

    /** "주유 +50%" / "충전 +40%" 버튼. 정차 중에만 의미가 있다. */
    fun addEnergy(percent: Double) {
        energy.update { (it + percent).coerceAtMost(100.0) }
    }
}
