package com.gachiga.app.domain

import java.util.Calendar
import java.util.TimeZone

/**
 * 월간 리포트 (spec.md 9절 N6, 기능 6 '평소 모드'). 순수 Kotlin.
 * 그룹 여행이 없는 평소에도 앱을 열 이유(리텐션)를 만든다.
 */
object MonthlyReport {

    data class TripFigures(
        val startedAt: Long,
        val distanceKm: Double,
        val totalCostWon: Long,
        val energyUsed: Double,      // L 또는 kWh
        val energyType: EnergyType,
    )

    data class Summary(
        val year: Int,
        val month: Int,              // 1..12
        val trips: Int,
        val distanceKm: Double,
        val totalCostWon: Long,
        /** 이번 달 여행의 평균 전비·연비 (같은 종류끼리). 기록이 없으면 null. */
        val efficiency: Double?,
        val energyType: EnergyType?,
    )

    fun of(trips: List<TripFigures>, nowMs: Long, timeZone: TimeZone = TimeZone.getDefault()): Summary {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMs }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val inMonth = trips.filter {
            val c = Calendar.getInstance(timeZone).apply { timeInMillis = it.startedAt }
            c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month
        }
        // 가장 많이 쓴 에너지 종류 기준으로 평균을 낸다 (전기차와 내연기관을 섞지 않는다)
        val type = inMonth.groupingBy { it.energyType }.eachCount().maxByOrNull { it.value }?.key
        val sameType = inMonth.filter { it.energyType == type }
        val used = sameType.sumOf { it.energyUsed }
        return Summary(
            year = year,
            month = month + 1,
            trips = inMonth.size,
            distanceKm = inMonth.sumOf { it.distanceKm },
            totalCostWon = inMonth.sumOf { it.totalCostWon },
            efficiency = if (used > 0) sameType.sumOf { it.distanceKm } / used else null,
            energyType = type,
        )
    }
}
