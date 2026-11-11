package com.gachiga.app

import android.content.Context
import com.gachiga.app.data.settings.VehicleSettingsRepository
import com.gachiga.app.data.trip.AppDatabase
import com.gachiga.app.data.vehicle.DemoVehicleDataSource
import com.gachiga.app.data.vehicle.PleosVehicleDataSource
import com.gachiga.app.data.vehicle.VehicleDataSource
import com.gachiga.app.data.voice.NoopVoiceIO
import com.gachiga.app.data.voice.VoiceIO

/** 수동 DI (spec.md 2절). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val settings = VehicleSettingsRepository(appContext)

    val demoVehicle = DemoVehicleDataSource()

    val pleosVehicle: PleosVehicleDataSource by lazy {
        PleosVehicleDataSource(appContext).also { it.initialize() }
    }

    fun vehicleSource(demoMode: Boolean): VehicleDataSource =
        if (demoMode) demoVehicle else pleosVehicle

    // TODO(V4): ON_DEVICE 초기화가 되면 PleosVoiceIO로 바꾼다.
    val voice: VoiceIO = NoopVoiceIO
}
