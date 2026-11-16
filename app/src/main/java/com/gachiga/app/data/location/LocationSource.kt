package com.gachiga.app.data.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.util.Log
import com.gachiga.app.domain.Geo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update

data class CarLocation(
    val lat: Double,
    val lon: Double,
    /** 진행 방향(도, 북쪽 0). 모르면 null. */
    val heading: Double?,
)

/**
 * 차량 위치 (같이 가기). 일행과 나누는 동안에만 읽고, 앱에는 저장하지 않는다.
 * 실제 차량(Pleos FusedLocation)과 데모 모드가 같은 인터페이스를 쓴다.
 */
interface LocationSource {
    /** 이 차량에서 위치를 읽을 수 있는지. 차종이 아니라 SDK의 isSupported로 판단한다. */
    fun isSupported(): Boolean

    fun locations(): Flow<CarLocation>
}

/**
 * Pleos FusedLocation SDK 1.1.3.
 * 시그니처는 SDK 클래스 파일에서 확인했다 (2026-11-16):
 * FusedLocation(context) → initialize() → isSupported() → registerFusedLocationCallback(FusedLocationListener)
 * LocationData: latitude, longitude, altitude, heading(Float), confidence(Float), timestampMillis
 */
class PleosLocationSource(context: Context) : LocationSource {

    private val fused = FusedLocation(context)
    private var initialized = false

    fun initialize() {
        if (!initialized) {
            initialized = runCatching { fused.initialize() }
                .onFailure { Log.w(TAG, "FusedLocation initialize failed", it) }
                .isSuccess
        }
    }

    override fun isSupported(): Boolean =
        initialized && runCatching { fused.isSupported() }.getOrDefault(false)

    override fun locations(): Flow<CarLocation> {
        if (!initialized) return emptyFlow()
        return callbackFlow {
            val listener = object : FusedLocationListener {
                override fun onChanged(location: LocationData?) {
                    val data = location ?: return
                    // (0, 0)은 위치를 아직 못 잡았을 때 오는 값으로 보고 버린다.
                    if (data.latitude == 0.0 && data.longitude == 0.0) return
                    val heading = data.heading.toDouble().takeIf { it in 0.0..360.0 }
                    trySend(CarLocation(data.latitude, data.longitude, heading))
                }

                override fun onError(exception: Exception) {
                    Log.w(TAG, "FusedLocation error", exception)
                }
            }
            // 권한(pleos.car.permission.FUSED_LOCATION)을 아직 못 받았으면 SecurityException이 난다.
            // 사용자가 나중에 동의할 수 있으므로 될 때까지 가끔 다시 등록해 본다.
            launch {
                while (isActive) {
                    val ok = runCatching {
                        fused.getLastKnownLocation(listener)
                        fused.registerFusedLocationCallback(listener)
                    }.onFailure { Log.w(TAG, "FusedLocation register failed: ${it.message}") }.isSuccess
                    if (ok) break
                    delay(RETRY_MS)
                }
            }
            awaitClose { runCatching { fused.unregisterFusedLocationCallback(listener) } }
        }
    }

    private companion object {
        const val TAG = "PleosLocation"
        const val RETRY_MS = 3_000L
    }
}

/**
 * 데모 모드 위치. "10 km 주행" 버튼을 누르면 동쪽으로 그만큼 움직인다.
 * 데모 모드 차량 두 대는 달린 거리의 차이만큼 떨어진 것으로 보인다.
 */
class DemoLocationSource : LocationSource {
    private val location = MutableStateFlow(CarLocation(START_LAT, START_LON, BEARING))

    override fun isSupported() = true
    override fun locations(): Flow<CarLocation> = location.asStateFlow()

    fun advance(km: Double) {
        location.update {
            val (lat, lon) = Geo.move(it.lat, it.lon, BEARING, km * 1000)
            CarLocation(lat, lon, BEARING)
        }
    }

    private companion object {
        // 영동고속도로 시작점 근처의 가상 출발 지점
        const val START_LAT = 37.40
        const val START_LON = 127.10
        const val BEARING = 90.0
    }
}
