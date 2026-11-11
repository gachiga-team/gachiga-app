package com.gachiga.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KoreanUnitsTest {

    @Test
    fun `내비 도착 잔량 Wh를 퍼센트로`() {
        assertEquals(64, KoreanUnits.whToPercent(49_454, 77.0))   // 에뮬레이터 실측값
        assertEquals(10, KoreanUnits.whToPercent(7_700, 77.0))
        assertEquals(0, KoreanUnits.whToPercent(0, 77.0))
        assertEquals(100, KoreanUnits.whToPercent(90_000, 77.0))   // 넘치면 100
    }

    @Test
    fun `용량이나 값이 이상하면 null`() {
        assertNull(KoreanUnits.whToPercent(1_000, 0.0))
        assertNull(KoreanUnits.whToPercent(-1, 77.0))
    }
}
