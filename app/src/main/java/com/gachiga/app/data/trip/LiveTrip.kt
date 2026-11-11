package com.gachiga.app.data.trip

import com.gachiga.app.domain.Settlement
import com.gachiga.app.domain.TripStatus

/** 진행 중인 여행의 현재 모습. 열린 구간에는 최신 주행거리와 사용량이 들어 있다. */
data class LiveTrip(
    val trip: Trip,
    val segments: List<Segment>,
    val refuels: List<Refuel>,
    val expenses: List<Expense>,
) {
    val isPaused: Boolean get() = trip.status == TripStatus.PAUSED
    val distanceKm: Double get() = segments.sumOf { (it.endOdometerKm ?: it.startOdometerKm) - it.startOdometerKm }
    val energyUsedPercent: Double get() = segments.sumOf { it.energyUsedPercent }

    fun settlement(): Settlement.Result = settlementOf(trip, segments, refuels, expenses)
}

fun settlementOf(trip: Trip, segments: List<Segment>, refuels: List<Refuel>, expenses: List<Expense>): Settlement.Result =
    Settlement.calculate(
        Settlement.Input(
            headcount = trip.headcount,
            capacity = trip.capacity,
            defaultUnitPrice = trip.defaultUnitPrice,
            segments = segments.map {
                Settlement.SegmentInput(it.startOdometerKm, it.endOdometerKm ?: it.startOdometerKm, it.energyUsedPercent)
            },
            refuels = refuels.map { Settlement.RefuelInput(it.percentBefore, it.percentAfter, it.amountWon) },
            expensesWon = expenses.map { it.amountWon },
        ),
    )
