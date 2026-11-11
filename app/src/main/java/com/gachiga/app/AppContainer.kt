package com.gachiga.app

import android.content.Context
import com.gachiga.app.data.navi.NaviSource
import com.gachiga.app.data.settings.VehicleSettingsRepository
import com.gachiga.app.data.trip.AppDatabase
import com.gachiga.app.data.trip.TripHistory
import com.gachiga.app.data.trip.TripRecorder
import com.gachiga.app.data.vehicle.DemoVehicleDataSource
import com.gachiga.app.data.vehicle.PleosVehicleDataSource
import com.gachiga.app.data.vehicle.UxRestrictionSource
import com.gachiga.app.data.vehicle.VehicleDataSource
import com.gachiga.app.data.voice.NoopVoiceIO
import com.gachiga.app.data.voice.PleosVoiceIO
import com.gachiga.app.data.voice.VoiceIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 수동 DI (spec.md 2절). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** 화면과 상관없이 앱 프로세스가 살아 있는 동안 도는 작업용 (여행 기록). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val settings = VehicleSettingsRepository(appContext)

    val demoVehicle = DemoVehicleDataSource()

    val pleosVehicle: PleosVehicleDataSource by lazy {
        PleosVehicleDataSource(appContext).also { it.initialize() }
    }

    fun vehicleSource(demoMode: Boolean): VehicleDataSource =
        if (demoMode) demoVehicle else pleosVehicle

    val history: TripHistory by lazy { TripHistory(database.tripDao()) }

    /** 내비 연동 (spec.md 9절 N1~N3). 내비가 없는 차량이면 available이 false로 남는다. */
    val navi: NaviSource by lazy { NaviSource(appContext).also { it.initialize() } }

    /** 플랫폼 주행 제한 상태 (UI_GUIDELINES.md). */
    val uxRestrictions = UxRestrictionSource(appContext)

    val recorder: TripRecorder by lazy {
        TripRecorder(database.tripDao(), settings, ::vehicleSource, appScope)
    }

    /** F8: Pleos STT·TTS ON_DEVICE. 초기화에 실패하면 canListen/canSpeak가 false로 남아 터치로 대체한다. */
    val voice: VoiceIO by lazy {
        runCatching { PleosVoiceIO(appContext).also { it.initialize() } }
            .getOrElse { NoopVoiceIO }
    }
}
