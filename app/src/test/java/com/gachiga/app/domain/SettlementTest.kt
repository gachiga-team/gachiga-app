package com.gachiga.app.domain

import com.gachiga.app.domain.Settlement.Input
import com.gachiga.app.domain.Settlement.RefuelInput
import com.gachiga.app.domain.Settlement.SegmentInput
import com.gachiga.app.domain.Settlement.UnitPriceBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** spec.md F6 완료 조건 */
class SettlementTest {

    private fun input(
        headcount: Int = 2,
        segments: List<SegmentInput> = listOf(SegmentInput(1_000.0, 1_150.0, 20.0)),
        refuels: List<RefuelInput> = emptyList(),
        expenses: List<Int> = emptyList(),
    ) = Input(
        headcount = headcount,
        capacity = 50.0,
        defaultUnitPrice = 1_700,
        segments = segments,
        refuels = refuels,
        expensesWon = expenses,
    )

    @Test
    fun `주유 없음 - 기본 단가 사용`() {
        val r = Settlement.calculate(input())

        assertEquals(150.0, r.distanceKm, 1e-9)
        assertEquals(10.0, r.energyUsed, 1e-9)          // 20% × 50L
        assertEquals(UnitPriceBasis.DEFAULT_ESTIMATE, r.unitPriceBasis)
        assertEquals(17_000L, r.energyCostWon)
        assertEquals(8_500L, r.perPersonWon)
        assertEquals(8_500L, r.driverShareWon)
        assertEquals(15.0, r.efficiency!!, 1e-9)
    }

    @Test
    fun `주유 1건 금액 입력 - 단가 역산`() {
        // 30% → 80% = 25L, 45,000원 → 1,800원/L
        val r = Settlement.calculate(input(refuels = listOf(RefuelInput(30.0, 80.0, 45_000))))

        assertEquals(UnitPriceBasis.REFUEL_INPUT, r.unitPriceBasis)
        assertEquals(1_800.0, r.unitPrice, 1e-9)
        assertEquals(18_000L, r.energyCostWon)          // 10L × 1,800
        assertEquals(18_000L, r.totalCostWon)           // 주유 금액 45,000원 자체는 더하지 않는다
    }

    @Test
    fun `주유 2건 중 1건만 금액 입력 - 입력된 건만으로 단가 계산`() {
        val r = Settlement.calculate(
            input(
                refuels = listOf(
                    RefuelInput(20.0, 60.0, 36_000),    // 20L, 1,800원/L
                    RefuelInput(50.0, 70.0, null),
                ),
            ),
        )

        assertEquals(UnitPriceBasis.REFUEL_INPUT, r.unitPriceBasis)
        assertEquals(1_800.0, r.unitPrice, 1e-9)
    }

    @Test
    fun `일시정지 구간 제외 - 구간 합만 거리로 센다`() {
        // 1,100 → 1,150 사이 50km는 일시정지 중이라 구간이 없다
        val r = Settlement.calculate(
            input(
                segments = listOf(
                    SegmentInput(1_000.0, 1_100.0, 10.0),
                    SegmentInput(1_150.0, 1_200.0, 5.0),
                ),
            ),
        )

        assertEquals(150.0, r.distanceKm, 1e-9)
        assertEquals(7.5, r.energyUsed, 1e-9)           // 15% × 50L
    }

    @Test
    fun `10원 단위 내림과 운전자 나머지 부담`() {
        val r = Settlement.calculate(
            input(
                headcount = 3,
                segments = listOf(SegmentInput(1_000.0, 1_000.0, 0.0)),
                expenses = listOf(100_005),
            ),
        )

        assertEquals(100_005L, r.totalCostWon)
        assertEquals(33_330L, r.perPersonWon)
        assertEquals(33_345L, r.driverShareWon)
        assertNull(r.efficiency)
    }

    @Test
    fun `혼자면 운전자가 전액 부담`() {
        val r = Settlement.calculate(input(headcount = 1))

        assertEquals(r.totalCostWon, r.driverShareWon)
    }
}
