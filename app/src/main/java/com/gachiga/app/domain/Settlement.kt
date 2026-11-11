package com.gachiga.app.domain

import kotlin.math.roundToLong

/**
 * 차량 1대 정산 (spec.md F6). Android 의존성 없는 순수 Kotlin.
 *
 * 주유 금액 자체를 비용으로 더하지 않는다. 이번 여행에서 쓴 만큼만 나누고,
 * 주유 금액은 단가를 구하는 데만 쓴다.
 */
object Settlement {

    data class SegmentInput(
        val startOdometerKm: Double,
        val endOdometerKm: Double,
        val energyUsedPercent: Double,
    )

    data class RefuelInput(
        val percentBefore: Double,
        val percentAfter: Double,
        val amountWon: Int?,
    )

    data class Input(
        val headcount: Int,
        val capacity: Double,          // L 또는 kWh
        val defaultUnitPrice: Int,     // 원/L 또는 원/kWh
        val segments: List<SegmentInput>,
        val refuels: List<RefuelInput>,
        val expensesWon: List<Int>,
    )

    enum class UnitPriceBasis { REFUEL_INPUT, DEFAULT_ESTIMATE }

    data class Result(
        val distanceKm: Double,                 // 차량 측정
        val energyUsed: Double,                 // L 또는 kWh, 차량 측정 × 설정 용량
        val unitPrice: Double,                  // 원/L 또는 원/kWh
        val unitPriceBasis: UnitPriceBasis,
        val energyCostWon: Long,
        val otherCostWon: Long,                 // 입력값
        val totalCostWon: Long,
        val perPersonWon: Long,                 // 각 동승자가 운전자에게 보낼 금액
        val driverShareWon: Long,               // 10원 미만 나머지를 포함한 운전자 부담
        val efficiency: Double?,                // km/L 또는 km/kWh. 사용량이 0이면 null
    )

    fun calculate(input: Input): Result {
        require(input.headcount in 1..9) { "headcount must be 1..9" }
        require(input.capacity > 0) { "capacity must be positive" }

        val distanceKm = input.segments.sumOf { it.endOdometerKm - it.startOdometerKm }
        val energyUsed = input.segments.sumOf { it.energyUsedPercent } / 100.0 * input.capacity

        val paidRefuels = input.refuels.filter { it.amountWon != null && it.percentAfter > it.percentBefore }
        val paidAmount = paidRefuels.sumOf { it.amountWon!!.toLong() }
        val paidEnergy = paidRefuels.sumOf { (it.percentAfter - it.percentBefore) / 100.0 * input.capacity }

        val (unitPrice, basis) = if (paidRefuels.isNotEmpty() && paidEnergy > 0) {
            paidAmount / paidEnergy to UnitPriceBasis.REFUEL_INPUT
        } else {
            input.defaultUnitPrice.toDouble() to UnitPriceBasis.DEFAULT_ESTIMATE
        }

        val energyCost = (energyUsed * unitPrice).roundToLong()
        val otherCost = input.expensesWon.sumOf { it.toLong() }
        val total = energyCost + otherCost

        val perPerson = total / input.headcount / 10 * 10
        val driverShare = total - perPerson * (input.headcount - 1)

        return Result(
            distanceKm = distanceKm,
            energyUsed = energyUsed,
            unitPrice = unitPrice,
            unitPriceBasis = basis,
            energyCostWon = energyCost,
            otherCostWon = otherCost,
            totalCostWon = total,
            perPersonWon = perPerson,
            driverShareWon = driverShare,
            efficiency = if (energyUsed > 0) distanceKm / energyUsed else null,
        )
    }
}
