package com.gachiga.app

import android.content.Context
import com.gachiga.app.data.convoy.ConvoyApi
import com.gachiga.app.data.convoy.ConvoyManager
import com.gachiga.app.data.location.DemoLocationSource
import com.gachiga.app.data.location.LocationSource
import com.gachiga.app.data.location.PleosLocationSource
import com.gachiga.app.data.navi.NaviSource
import com.gachiga.app.data.settings.VehicleSettingsRepository
import com.gachiga.app.data.trip.AppDatabase
import com.gachiga.app.data.trip.LongStore
import com.gachiga.app.data.trip.TripHistory
import com.gachiga.app.data.trip.TripRecorder
import com.gachiga.app.data.vehicle.DemoVehicleDataSource
import com.gachiga.app.data.vehicle.PleosVehicleDataSource
import com.gachiga.app.data.vehicle.UxRestrictionSource
import com.gachiga.app.data.vehicle.VehicleDataSource
import com.gachiga.app.data.voice.NoopVoiceIO
import com.gachiga.app.data.voice.PleosVoiceIO
import com.gachiga.app.data.voice.VoiceIO
import com.gachiga.app.domain.RestArea
import com.gachiga.app.domain.RestAreas
import com.gachiga.app.domain.TripDays
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
        TripRecorder(
            database.tripDao(), settings, ::vehicleSource, appScope,
            parkedSince = LongStore.Prefs(appContext.getSharedPreferences("trip_state", Context.MODE_PRIVATE), "parked_since"),
            // debug는 시연·확인용으로 3분만 세워도 다음 일차로 넘긴다. release는 4시간.
            longParkMs = if (BuildConfig.DEBUG) 3 * 60_000L else TripDays.LONG_PARK_MS,
        )
    }

    val demoLocation = DemoLocationSource()

    val pleosLocation: PleosLocationSource by lazy { PleosLocationSource(appContext).also { it.initialize() } }

    /** 위치 권한을 받았는지. 사용자 동의가 필요한 권한이다. */
    fun hasLocationPermission(): Boolean =
        appContext.checkSelfPermission(LOCATION_PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun locationSource(demoMode: Boolean): LocationSource = if (demoMode) demoLocation else pleosLocation

    /** 같이 가기(컨보이): 일행 차량과 최근 상태를 주고받는다. */
    val convoy: ConvoyManager by lazy {
        ConvoyManager(appContext, ConvoyApi(BuildConfig.CONVOY_URL), appScope, settings, ::vehicleSource, ::locationSource, recorder)
    }

    /** 함께 쉬기에 쓰는 휴게소 목록 (OpenStreetMap, tools/restareas/build.mjs로 만든다). */
    val restAreas: List<RestArea> by lazy {
        runCatching { appContext.assets.open("rest_areas.tsv").bufferedReader().useLines { RestAreas.parse(it) } }
            .getOrDefault(emptyList())
    }

    companion object {
        const val LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"
    }

    /** F8: Pleos STT·TTS ON_DEVICE. 초기화에 실패하면 canListen/canSpeak가 false로 남아 터치로 대체한다. */
    val voice: VoiceIO by lazy {
        runCatching { PleosVoiceIO(appContext).also { it.initialize() } }
            .getOrElse { NoopVoiceIO }
    }
}
