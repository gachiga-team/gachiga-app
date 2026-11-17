package com.gachiga.app.data.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Android Automotive 주행 제한 상태 (UI_GUIDELINES.md 1절, 5절).
 * Pleos는 앱이 CarUxRestrictions로 주행 제한 상태를 확인해 기능을 줄이거나 막도록 요구한다.
 *
 * true면 플랫폼이 "주의 분산 최소화가 필요하다(주행 중)"고 판단한 것이다. 이때는 기어와 상관없이 입력을 막는다.
 * null이면 이 기기에서 확인할 수 없다는 뜻이고, 기어 기반 판단(spec.md 3.2)만 쓴다.
 * 에뮬레이터 확인(11/11): 정차(parked)는 제한 없음, 상태 모름(unknown)은 모두 제한.
 *
 * 차량 서비스 연결은 수명 주기 리스너로 받는다. `Car.createCar(context)`만 쓰면 차량 서비스가 죽을 때
 * 플랫폼이 이 앱 프로세스도 함께 종료시킨다 (에뮬레이터 확인 11/17: 여행 기록 중 앱이 계속 꺼졌다).
 * 리스너를 쓰면 끊긴 동안 null을 내보내고, 서비스가 돌아오면 다시 붙는다.
 */
class UxRestrictionSource(private val context: Context) {

    fun requiresDistractionOptimization(): Flow<Boolean?> = callbackFlow {
        var manager: CarUxRestrictionsManager? = null

        val car = runCatching {
            Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                runCatching { manager?.unregisterListener() }
                manager = null
                if (!ready) {
                    Log.w(TAG, "car service disconnected")
                    trySend(null)
                    return@createCar
                }
                val m = runCatching { connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager }
                    .onFailure { e -> Log.w(TAG, "UX restriction service unavailable", e) }
                    .getOrNull()
                manager = m
                if (m == null) {
                    trySend(null)
                    return@createCar
                }
                trySend(runCatching { m.currentCarUxRestrictions?.isRequiresDistractionOptimization }.getOrNull())
                runCatching {
                    m.registerListener { restrictions ->
                        Log.d(TAG, "ux restrictions changed: requiresDO=${restrictions.isRequiresDistractionOptimization}")
                        trySend(restrictions.isRequiresDistractionOptimization)
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Car.createCar failed", it) }.getOrNull()

        if (car == null) trySend(null)
        awaitClose {
            runCatching { manager?.unregisterListener() }
            runCatching { car?.disconnect() }
        }
    }.distinctUntilChanged()

    private companion object {
        const val TAG = "UxRestrictions"
    }
}
