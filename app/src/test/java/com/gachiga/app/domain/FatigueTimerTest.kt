package com.gachiga.app.domain

import com.gachiga.app.domain.FatigueTimer.Companion.HOUR
import com.gachiga.app.domain.FatigueTimer.Companion.MINUTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** spec.md 9절 N4 */
class FatigueTimerTest {

    @Test
    fun `2시간 연속 주행하면 한 번 안내하고 1시간 뒤 다시 안내`() {
        val t = FatigueTimer()
        t.onMotion(0, MotionState.DRIVING)
        assertNull(t.tick(119 * MINUTE))
        assertEquals(120L, t.tick(120 * MINUTE))
        assertNull(t.tick(121 * MINUTE))
        assertEquals(180L, t.tick(180 * MINUTE))
    }

    @Test
    fun `짧은 정차는 쉰 것으로 보지 않는다`() {
        val t = FatigueTimer()
        t.onMotion(0, MotionState.DRIVING)
        t.onMotion(60 * MINUTE, MotionState.PARKED)
        assertNull(t.tick(65 * MINUTE))                 // 정차 중에는 안내하지 않는다
        t.onMotion(65 * MINUTE, MotionState.DRIVING)    // 5분 만에 다시 출발
        assertEquals(120L, t.tick(2 * HOUR))            // 처음부터 2시간이 지났다
    }

    @Test
    fun `10분 넘게 쉬면 처음부터 다시 센다`() {
        val t = FatigueTimer()
        t.onMotion(0, MotionState.DRIVING)
        t.onMotion(90 * MINUTE, MotionState.PARKED)
        assertNull(t.tick(101 * MINUTE))                // 11분 휴식 → 초기화
        t.onMotion(101 * MINUTE, MotionState.DRIVING)
        assertNull(t.tick(200 * MINUTE))                // 다시 출발한 지 99분
        assertEquals(120L, t.tick(221 * MINUTE))
    }

    @Test
    fun `말로 읽을 시간`() {
        assertEquals("2시간", FatigueTimer.spoken(120))
        assertEquals("1시간 30분", FatigueTimer.spoken(90))
        assertEquals("45분", FatigueTimer.spoken(45))
    }
}
