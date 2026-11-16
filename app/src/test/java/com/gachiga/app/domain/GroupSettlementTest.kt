package com.gachiga.app.domain

import com.gachiga.app.domain.GroupSettlement.Car
import com.gachiga.app.domain.GroupSettlement.Transfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupSettlementTest {

    @Test fun `두 대의 비용을 합쳐 전체 인원으로 나눈다`() {
        // 1호차 3명 40,000원, 2호차 2명 20,000원 → 60,000 / 5 = 12,000원
        val r = GroupSettlement.calculate(listOf(Car("1호차", 40_000, 3), Car("2호차", 20_000, 2)))
        assertEquals(60_000, r.totalCostWon)
        assertEquals(5, r.headcount)
        assertEquals(12_000, r.perPersonWon)
        // 1호차 몫 36,000 → 4,000 받을 돈. 2호차 몫 24,000 → 4,000 보낼 돈.
        assertEquals(listOf(Transfer("2호차", "1호차", 4_000)), r.transfers)
    }

    @Test fun `10원 미만 나머지는 비용이 가장 큰 차량이 부담한다`() {
        val r = GroupSettlement.calculate(listOf(Car("1호차", 10_005, 2), Car("2호차", 20_000, 1)))
        // 30,005 / 3 = 10,001.67 → 10,000원. 나머지 5원은 2호차.
        assertEquals(10_000, r.perPersonWon)
        assertEquals(20_000, r.cars[0].shareWon)
        assertEquals(10_005, r.cars[1].shareWon)
        assertEquals(listOf(Transfer("1호차", "2호차", 9_995)), r.transfers)
        assertEquals(0, r.cars.sumOf { it.balanceWon })
    }

    @Test fun `세 대면 보낼 차량에서 받을 차량으로 나눠 보낸다`() {
        val r = GroupSettlement.calculate(listOf(Car("1호차", 90_000, 2), Car("2호차", 0, 2), Car("3호차", 30_000, 2)))
        assertEquals(20_000, r.perPersonWon)
        // 1호차 +50,000, 2호차 -40,000, 3호차 -10,000
        assertEquals(listOf(Transfer("2호차", "1호차", 40_000), Transfer("3호차", "1호차", 10_000)), r.transfers)
    }

    @Test fun `한 대뿐이거나 몫이 같으면 주고받을 돈이 없다`() {
        assertTrue(GroupSettlement.calculate(listOf(Car("1호차", 30_000, 3))).transfers.isEmpty())
        assertTrue(GroupSettlement.calculate(listOf(Car("1호차", 20_000, 2), Car("2호차", 20_000, 2))).transfers.isEmpty())
    }
}
