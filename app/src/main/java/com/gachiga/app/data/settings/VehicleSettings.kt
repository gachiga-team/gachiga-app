package com.gachiga.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gachiga.app.domain.EnergyType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 차량 설정 (spec.md F9). 기본 단가는 가정치이므로 화면과 정산서에 그렇게 표시한다.
 */
data class VehicleSettings(
    val energyType: EnergyType = EnergyType.FUEL,
    val fuelCapacityL: Double = 50.0,
    val fuelPriceWonPerL: Int = 1_700,     // 가정치
    val evCapacityKWh: Double = 77.0,
    val evPriceWonPerKWh: Int = 350,       // 가정치
    val demoMode: Boolean = false,
    val onboarded: Boolean = false,
) {
    val capacity: Double get() = if (energyType == EnergyType.FUEL) fuelCapacityL else evCapacityKWh
    val unitPrice: Int get() = if (energyType == EnergyType.FUEL) fuelPriceWonPerL else evPriceWonPerKWh
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vehicle_settings")

class VehicleSettingsRepository(private val context: Context) {
    private object Keys {
        val energyType = stringPreferencesKey("energy_type")
        val fuelCapacityL = doublePreferencesKey("fuel_capacity_l")
        val fuelPrice = intPreferencesKey("fuel_price_won_per_l")
        val evCapacityKWh = doublePreferencesKey("ev_capacity_kwh")
        val evPrice = intPreferencesKey("ev_price_won_per_kwh")
        val demoMode = booleanPreferencesKey("demo_mode")
        val onboarded = booleanPreferencesKey("onboarded")
    }

    val settings: Flow<VehicleSettings> = context.dataStore.data.map { p ->
        val d = VehicleSettings()
        VehicleSettings(
            energyType = p[Keys.energyType]?.let { runCatching { EnergyType.valueOf(it) }.getOrNull() } ?: d.energyType,
            fuelCapacityL = p[Keys.fuelCapacityL] ?: d.fuelCapacityL,
            fuelPriceWonPerL = p[Keys.fuelPrice] ?: d.fuelPriceWonPerL,
            evCapacityKWh = p[Keys.evCapacityKWh] ?: d.evCapacityKWh,
            evPriceWonPerKWh = p[Keys.evPrice] ?: d.evPriceWonPerKWh,
            demoMode = p[Keys.demoMode] ?: d.demoMode,
            onboarded = p[Keys.onboarded] ?: d.onboarded,
        )
    }

    suspend fun update(s: VehicleSettings) {
        context.dataStore.edit { p ->
            p[Keys.energyType] = s.energyType.name
            p[Keys.fuelCapacityL] = s.fuelCapacityL
            p[Keys.fuelPrice] = s.fuelPriceWonPerL
            p[Keys.evCapacityKWh] = s.evCapacityKWh
            p[Keys.evPrice] = s.evPriceWonPerKWh
            p[Keys.demoMode] = s.demoMode
            p[Keys.onboarded] = s.onboarded
        }
    }

    suspend fun setOnboarded() {
        context.dataStore.edit { it[Keys.onboarded] = true }
    }

    suspend fun setDemoMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.demoMode] = enabled }
    }
}
