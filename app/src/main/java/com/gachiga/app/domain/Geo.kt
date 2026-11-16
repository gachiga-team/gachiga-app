package com.gachiga.app.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 위치 계산 (컨보이 모드). 순수 Kotlin. */
object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** 두 지점 사이의 직선 거리(m). 하버사인 공식. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
    }

    /** 첫 지점에서 둘째 지점을 바라보는 방위각(도, 북쪽 0, 시계 방향, 0..360). */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** 내 진행 방향([headingDegrees]) 기준으로 상대가 앞쪽(±90도 안)에 있는지. */
    fun isAhead(headingDegrees: Double, bearingToOther: Double): Boolean {
        val diff = ((bearingToOther - headingDegrees) % 360.0 + 540.0) % 360.0 - 180.0
        return diff > -90.0 && diff < 90.0
    }

    /** [bearingDegrees] 방향으로 [meters]만큼 간 지점 (데모 모드 위치 흉내용, 짧은 거리 근사). */
    fun move(lat: Double, lon: Double, bearingDegrees: Double, meters: Double): Pair<Double, Double> {
        val b = Math.toRadians(bearingDegrees)
        val dLat = meters * cos(b) / EARTH_RADIUS_M
        val dLon = meters * sin(b) / (EARTH_RADIUS_M * cos(Math.toRadians(lat)))
        return (lat + Math.toDegrees(dLat)) to (lon + Math.toDegrees(dLon))
    }
}
