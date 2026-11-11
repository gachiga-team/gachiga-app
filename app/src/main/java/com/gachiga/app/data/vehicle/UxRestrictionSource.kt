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
 */
class UxRestrictionSource(private val context: Context) {

    fun requiresDistractionOptimization(): Flow<Boolean?> = callbackFlow {
        val car = runCatching { Car.createCar(context) }
            .onFailure { Log.w(TAG, "Car.createCar failed", it) }
            .getOrNull()
        val manager = car?.let {
            runCatching { it.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager }
                .onFailure { e -> Log.w(TAG, "UX restriction service unavailable", e) }
                .getOrNull()
        }
        if (manager == null) {
            trySend(null)
            awaitClose { runCatching { car?.disconnect() } }
            return@callbackFlow
        }

        trySend(manager.currentCarUxRestrictions?.isRequiresDistractionOptimization)
        manager.registerListener { restrictions ->
            Log.d(TAG, "ux restrictions changed: requiresDO=${restrictions.isRequiresDistractionOptimization}")
            trySend(restrictions.isRequiresDistractionOptimization)
        }
        awaitClose {
            runCatching { manager.unregisterListener() }
            runCatching { car.disconnect() }
        }
    }.distinctUntilChanged()

    private companion object {
        const val TAG = "UxRestrictions"
    }
}
