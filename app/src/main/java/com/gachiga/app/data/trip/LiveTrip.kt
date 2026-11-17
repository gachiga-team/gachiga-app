package com.gachiga.app.data.trip

import com.gachiga.app.domain.Settlement
import com.gachiga.app.domain.TripDays
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

    /** 지금 몇 일차인지. */
    val dayIndex: Int get() = segments.lastOrNull()?.dayIndex ?: 1

    fun settlement(): Settlement.Result = settlementOf(trip, segments, refuels, expenses)

    fun days(): List<TripDays.Day> = daysOf(trip, segments, expenses, settlement().unitPrice)
}

/** 일차별 거리와 비용. 단가는 여행 전체 정산에 쓴 단가를 그대로 쓴다. */
fun daysOf(trip: Trip, segments: List<Segment>, expenses: List<Expense>, unitPrice: Double): List<TripDays.Day> =
    TripDays.summarize(
        segments.map {
            TripDays.SegmentInput(it.dayIndex, it.startedAt, (it.endOdometerKm ?: it.startOdometerKm) - it.startOdometerKm, it.energyUsedPercent)
        },
        expenses.map { it.createdAt to it.amountWon },
        trip.capacity,
        unitPrice,
    )

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
