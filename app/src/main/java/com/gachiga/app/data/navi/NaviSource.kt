package com.gachiga.app.data.navi

import android.content.Context
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** 앱이 쓰는 내비 사건 (spec.md 9절 N1~N3). */
sealed interface NaviEvent {
    /** 경로 안내 시작. 거리 m, 시간 초, [tollFare] 예상 통행료(원), [arrivalCharge] 도착 시 예상 배터리 잔량(Wh). */
    data class RouteStarted(val name: String, val distanceM: Int, val durationS: Int, val tollFare: Int, val arrivalCharge: Int) : NaviEvent
    /** 현재 목적지 정보 (여행을 경로 안내 도중에 시작했을 때 다시 묻는 데 쓴다). */
    data class Destination(val name: String, val remainDistanceM: Int, val tollFare: Int) : NaviEvent
    /** 경로 상태. 도착 시 예상 배터리 잔량(Wh)이 바뀌면 온다. */
    data class RouteState(val arrivalCharge: Int) : NaviEvent
    data class Arrived(val name: String, val distanceM: Int, val durationS: Int) : NaviEvent
    data object RouteEnded : NaviEvent
}

/**
 * Pleos NaviHelper 3.0.5 (Playground '사용 가능').
 * 읽기·구독만 쓰므로 권한은 `pleos.car.permission.NAVI_ROUTE` 하나다.
 * 내비가 없는 차량이면 초기화가 실패하고 [available]이 false로 남는다. 그때 앱은 내비 연동 없이 동작한다.
 */
class NaviSource(context: Context) {

    private val helper = NaviHelper(context)

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private val _events = MutableSharedFlow<NaviEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<NaviEvent> = _events.asSharedFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) {
            Log.i(TAG, "route started: name=${info.name} dist=${info.distance} dur=${info.duration} toll=${info.tollFare} arrivalCharge=${info.remainDestinationChargingValue}")
            _events.tryEmit(
                NaviEvent.RouteStarted(info.name.orEmpty(), info.distance, info.duration, info.tollFare, info.remainDestinationChargingValue),
            )
        }

        override fun onDestinationInfo(info: DestinationInfo) {
            Log.i(TAG, "destination: name=${info.name} remain=${info.remainDistance} toll=${info.tollFare}")
            _events.tryEmit(NaviEvent.Destination(info.name.orEmpty(), info.remainDistance, info.tollFare))
        }

        override fun onRouteStateInfo(info: RouteStateInfo) {
            Log.d(TAG, "route state: routing=${info.isRouting} arrivalCharge=${info.remainDestinationChargingInt}")
            if (info.isRouting) _events.tryEmit(NaviEvent.RouteState(info.remainDestinationChargingInt))
        }

        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            Log.i(TAG, "arrived: name=${info.name} dist=${info.distance} dur=${info.duration}")
            _events.tryEmit(NaviEvent.Arrived(info.name.orEmpty(), info.distance, info.duration))
        }

        override fun onRouteEnded() {
            Log.i(TAG, "route ended")
            _events.tryEmit(NaviEvent.RouteEnded)
        }

        override fun onRouteCancelled() {
            Log.i(TAG, "route cancelled")
            _events.tryEmit(NaviEvent.RouteEnded)
        }

        override fun onError(code: NaviErrorCode) {
            Log.w(TAG, "navi error: $code")
        }
    }

    fun initialize() {
        runCatching {
            helper.initialize(
                {
                    Log.i(TAG, "NaviHelper initialized")
                    helper.addListener(listener)
                    _available.value = true
                    refresh()
                },
                { e -> Log.w(TAG, "NaviHelper initialize failed", e) },
            )
        }.onFailure { Log.w(TAG, "NaviHelper initialize threw", it) }
    }

    /** 지금 걸린 경로가 있으면 목적지·경로 상태를 다시 보내 달라고 한다. 결과는 [events]로 온다. */
    fun refresh() {
        if (!_available.value) return
        runCatching {
            helper.getDestinationInfo()
            helper.getRouteStateInfo()
        }.onFailure { Log.w(TAG, "navi refresh failed", it) }
    }

    /**
     * debug 빌드 전용: 내비에 테스트 경로를 건다 (에뮬레이터에서 한글 검색을 할 수 없어서).
     * `pleos.car.permission.NAVI_ROUTE_SEARCH`가 필요하고, 이 권한은 debug 매니페스트에만 있다.
     */
    fun requestTestRoute(latitude: Double, longitude: Double, name: String) {
        runCatching {
            helper.requestRoute(RouteInfo(latitude, longitude, null, null, name, "", "", "", RouteOption.RECOMMENDED, "", false))
        }.onFailure { Log.w(TAG, "test route failed", it) }
    }

    fun release() {
        runCatching { helper.removeListener(listener) }
        runCatching { helper.release() }
    }

    private companion object {
        const val TAG = "PleosNavi"
    }
}
