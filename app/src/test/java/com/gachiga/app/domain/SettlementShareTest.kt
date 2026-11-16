package com.gachiga.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/** spec.md 9절 N5 QR 정산서 */
class SettlementShareTest {

    private val sheet = SettlementShare.Sheet(
        title = "강릉역 여행", period = "11월 10일 20:27 ~ 20:31", headcount = 2,
        distanceKm = 68.0, energyLabel = "배터리", energyUsed = 10.01, unit = "kWh",
        unitPrice = 350.0, priceBasis = "기본 단가로 추정",
        energyCostWon = 3_504, otherCostWon = 6_860, totalCostWon = 10_364,
        perPersonWon = 5_180, driverShareWon = 5_184,
        items = listOf("통행료 (내비 예상, \"강릉역\")" to 6_860L),
    )

    @Test
    fun `주소의 샵 뒤를 풀면 같은 JSON이 나온다`() {
        val url = SettlementShare.url("https://example.github.io/gachiga/settle/", sheet)!!
        val fragment = url.substringAfter('#')
        val decoded = String(Base64.getUrlDecoder().decode(fragment), Charsets.UTF_8)

        assertEquals(SettlementShare.json(sheet), decoded)
        assertTrue(decoded.contains("\"pp\":5180"))
        assertTrue(decoded.contains("\"t\":\"강릉역 여행\""))
        assertTrue(decoded.contains("\\\"강릉역\\\""))            // 따옴표가 escape된다
        assertTrue(url.startsWith("https://example.github.io/gachiga/settle/#"))
    }

    @Test
    fun `그룹 합산 정산이 있으면 QR에 함께 담는다`() {
        val group = SettlementShare.Group(
            code = "1234", myLabel = "1호차", totalCostWon = 60_000, headcount = 5, perPersonWon = 12_000, pending = false,
            cars = listOf(SettlementShare.Car("1호차", 3, 40_000), SettlementShare.Car("2호차", 2, 20_000)),
            transfers = listOf(SettlementShare.Transfer("2호차", "1호차", 4_000)),
        )
        val json = SettlementShare.json(sheet.copy(group = group))
        assertTrue(json.contains("\"g\":{\"c\":\"1234\",\"me\":\"1호차\",\"tc\":60000,\"n\":5,\"pp\":12000,\"p\":0,"))
        assertTrue(json.contains("\"cars\":[[\"1호차\",3,40000],[\"2호차\",2,20000]]"))
        assertTrue(json.contains("\"tr\":[[\"2호차\",\"1호차\",4000]]"))

        val text = SettlementShare.plainText(sheet.copy(group = group))
        assertTrue(text.contains("그룹 1234 합산: 총 60,000원 / 5명 = 1인 12,000원"))
        assertTrue(text.contains("2호차 운전자 → 1호차 운전자 4,000원"))
        assertTrue(!text.contains("동승자 1인당 운전자에게 5,180원")) // 차량 1대 기준 금액은 헷갈리지 않게 뺀다
    }

    @Test
    fun `웹 주소가 없으면 url은 null이고 글자 정산서를 쓴다`() {
        assertNull(SettlementShare.url("", sheet))
        val text = SettlementShare.plainText(sheet)
        assertTrue(text.contains("동승자 1인당 운전자에게 5,180원"))
        assertTrue(text.contains("총비용 10,364원 (2명)"))
    }
}
