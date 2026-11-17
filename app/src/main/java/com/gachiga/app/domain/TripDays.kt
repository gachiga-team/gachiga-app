package com.gachiga.app.domain

import kotlin.math.roundToLong

/**
 * 여러 날 여행을 일차로 나눈다. 순수 Kotlin.
 *
 * Pleos Vehicle SDK에는 시동 상태를 읽는 API가 없다. 그래서 "오래 세워 둔 뒤 다시 출발하면 다음 날"로 본다.
 * 숙소에 세워 둔 밤은 넘어가고, 휴게소나 식사처럼 짧은 정차는 같은 날로 남는다.
 */
object TripDays {
    const val HOUR_MS = 60 * 60 * 1000L

    /** 이 시간 넘게 세워 뒀다가 출발하면 다음 일차로 본다. */
    const val LONG_PARK_MS = 4 * HOUR_MS

    /** 그날 이만큼도 달리지 않았으면 일차를 넘기지 않는다 (출발 전에 오래 서 있던 경우). */
    const val MIN_DAY_KM = 0.5

    /**
     * 다시 출발하는 순간에 부른다.
     * @param parkedSince 정차를 시작한 시각. 모르면 null.
     * @param dayDistanceKm 지금 일차에 달린 거리.
     */
    fun startsNewDay(parkedSince: Long?, now: Long, dayDistanceKm: Double, longParkMs: Long = LONG_PARK_MS): Boolean =
        parkedSince != null && now - parkedSince >= longParkMs && dayDistanceKm >= MIN_DAY_KM

    data class SegmentInput(val dayIndex: Int, val startedAt: Long, val distanceKm: Double, val energyUsedPercent: Double)

    data class Day(
        val index: Int,
        val startedAt: Long,
        val distanceKm: Double,
        /** L 또는 kWh */
        val energyUsed: Double,
        val energyCostWon: Long,
        /** 그날 넣은 통행료·주차비 등 */
        val otherCostWon: Long,
    ) {
        val totalCostWon: Long get() = energyCostWon + otherCostWon
    }

    /**
     * 일차별 거리와 비용. 비용은 그날 쓴 연료·배터리 × 여행 전체 단가 + 그날 넣은 기타 비용이다.
     * 날마다 반올림하므로 합계가 여행 총비용과 몇 원 다를 수 있다.
     * @param expenses (넣은 시각, 금액)
     */
    fun summarize(segments: List<SegmentInput>, expenses: List<Pair<Long, Int>>, capacity: Double, unitPrice: Double): List<Day> {
        val starts = segments.groupBy { it.dayIndex }.toSortedMap().map { (index, list) -> index to list.minOf { it.startedAt } }
        return starts.map { (index, startedAt) ->
            val own = segments.filter { it.dayIndex == index }
            val energyUsed = own.sumOf { it.energyUsedPercent } / 100.0 * capacity
            // 비용은 넣은 시각이 속한 일차에 붙인다. 첫 일차 시작 전에 넣은 것은 첫 일차로 본다.
            val other = expenses.filter { (at, _) -> (starts.lastOrNull { it.second <= at } ?: starts.first()).first == index }
                .sumOf { it.second.toLong() }
            Day(index, startedAt, own.sumOf { it.distanceKm }, energyUsed, (energyUsed * unitPrice).roundToLong(), other)
        }
    }
}
