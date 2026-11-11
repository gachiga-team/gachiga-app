package com.gachiga.app.data.trip

import com.gachiga.app.domain.Settlement
import kotlinx.coroutines.flow.first

/** 끝난 여행 하나와 그 정산 결과 (spec.md F6). */
data class TripDetail(
    val trip: Trip,
    val refuels: List<Refuel>,
    val expenses: List<Expense>,
    val result: Settlement.Result,
) {
    val unpaidRefuels: Int get() = refuels.count { it.amountWon == null }
}

/**
 * 끝난 여행 목록과 수정 (spec.md F6).
 * 끝난 뒤에 고칠 수 있는 것은 주유·충전 금액과 인원뿐이다. 차량이 측정한 값(거리, 사용량)은 고치지 않는다.
 */
class TripHistory(private val dao: TripDao) {

    /** 최근 여행부터. */
    suspend fun endedTrips(): List<TripDetail> =
        dao.observeEndedTrips().first().map { detailOf(it) }

    suspend fun detail(tripId: Long): TripDetail? = dao.tripById(tripId)?.let { detailOf(it) }

    suspend fun setRefuelAmount(tripId: Long, refuelId: Long, amountWon: Int): TripDetail? {
        val refuel = dao.refuels(tripId).find { it.id == refuelId } ?: return detail(tripId)
        dao.updateRefuel(refuel.copy(amountWon = amountWon))
        return detail(tripId)
    }

    /** 잘못 들어간 비용(예: 실제로는 안 낸 내비 예상 통행료)을 지운다. */
    suspend fun deleteExpense(tripId: Long, expenseId: Long): TripDetail? {
        dao.expenses(tripId).find { it.id == expenseId }?.let { dao.deleteExpense(it) }
        return detail(tripId)
    }

    suspend fun setHeadcount(tripId: Long, headcount: Int): TripDetail? {
        val trip = dao.tripById(tripId) ?: return null
        dao.updateTrip(trip.copy(headcount = headcount.coerceIn(1, 9)))
        return detail(tripId)
    }

    private suspend fun detailOf(trip: Trip): TripDetail {
        val segments = dao.segments(trip.id)
        val refuels = dao.refuels(trip.id)
        val expenses = dao.expenses(trip.id)
        return TripDetail(trip, refuels, expenses, settlementOf(trip, segments, refuels, expenses))
    }
}
