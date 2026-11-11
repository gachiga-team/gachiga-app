package com.gachiga.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** spec.md F4 완료 조건: 금액 파서 */
class KoreanAmountTest {

    @Test
    fun `spec 완료 조건 네 가지`() {
        assertEquals(3_200L, KoreanAmount.parse("삼천이백 원"))
        assertEquals(3_200L, KoreanAmount.parse("3천 2백원"))
        assertEquals(12_500L, KoreanAmount.parse("12,500원"))
        assertEquals(15_000L, KoreanAmount.parse("만 오천 원"))
    }

    @Test
    fun `만 단위와 숫자 섞어 쓰기`() {
        assertEquals(12_000L, KoreanAmount.parse("1만2천원"))
        assertEquals(60_000L, KoreanAmount.parse("6만 원"))
        assertEquals(60_000L, KoreanAmount.parse("육만원"))
        assertEquals(48_000L, KoreanAmount.parse("4만 8천"))
        assertEquals(110_000L, KoreanAmount.parse("십일만 원"))
    }

    @Test
    fun `숫자가 없으면 null`() {
        assertNull(KoreanAmount.parse("모르겠어요"))
        assertNull(KoreanAmount.parse(""))
    }

    @Test
    fun `비용 종류와 금액`() {
        assertEquals(ExpenseCategory.TOLL to 3_200L, KoreanAmount.parseExpense("통행료 3천 2백 원"))
        assertEquals(ExpenseCategory.PARKING to 5_000L, KoreanAmount.parseExpense("주차비 오천 원"))
        assertEquals(ExpenseCategory.OTHER to 9_900L, KoreanAmount.parseExpense("9,900원"))
    }
}
