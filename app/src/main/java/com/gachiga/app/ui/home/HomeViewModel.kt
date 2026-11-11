package com.gachiga.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gachiga.app.AppContainer
import com.gachiga.app.data.vehicle.VehicleCapabilities
import com.gachiga.app.domain.EnergyType
import com.gachiga.app.domain.MotionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
)

/**
 * 뼈대 단계의 홈 화면 상태. 차량 값과 주행/정차 상태만 보여 준다.
 * 여행 시작·기록·정산(F1~F6)은 TripEngine을 만든 뒤 여기에 연결한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<HomeUiState> = container.settings.settings
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
                    HomeUiState(settings.demoMode, caps, energyType, odo, energy, motion)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

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
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(container) as T
        }
    }
}
