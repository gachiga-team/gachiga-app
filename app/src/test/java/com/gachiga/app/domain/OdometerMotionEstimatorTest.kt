package com.gachiga.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** spec.md 3.2-2 주행거리 기반 주행/정차 추정 */
class OdometerMotionEstimatorTest {

    @Test
    fun `처음에는 판별 불가`() {
        val e = OdometerMotionEstimator()
        assertEquals(MotionState.UNKNOWN, e.onOdometer(0, 1_000.0))
    }

    @Test
    fun `20초 안에 0_05km 넘게 늘면 주행`() {
        val e = OdometerMotionEstimator()
        e.onOdometer(0, 1_000.0)
        assertEquals(MotionState.DRIVING, e.onOdometer(10_000, 1_000.1))
    }

    @Test
    fun `20초 안에 조금만 늘면 직전 상태 유지`() {
        val e = OdometerMotionEstimator()
        e.onOdometer(0, 1_000.0)
        assertEquals(MotionState.UNKNOWN, e.onOdometer(10_000, 1_000.03))
    }

    @Test
    fun `60초 동안 변화가 없으면 정차`() {
        val e = OdometerMotionEstimator()
        e.onOdometer(0, 1_000.0)
        e.onOdometer(10_000, 1_000.2)               // 주행
        assertEquals(MotionState.DRIVING, e.evaluate(50_000))
        assertEquals(MotionState.PARKED, e.evaluate(70_000))
    }

    @Test
    fun `오래전 주행거리 증가는 지금 주행으로 보지 않는다`() {
        val e = OdometerMotionEstimator()
        e.onOdometer(0, 1_000.0)
        e.onOdometer(5_000, 1_010.0)                // 주행
        e.evaluate(70_000)                          // 정차
        // 정차 후 아주 조금 바뀌어도, 창 시작 값은 1,010km라 주행으로 바뀌지 않는다
        assertEquals(MotionState.PARKED, e.onOdometer(80_000, 1_010.01))
    }

    @Test
    fun `정차 후 다시 출발하면 주행`() {
        val e = OdometerMotionEstimator()
        e.onOdometer(0, 1_000.0)
        e.evaluate(61_000)
        assertEquals(MotionState.PARKED, e.state)
        e.onOdometer(62_000, 1_000.0)
        assertEquals(MotionState.DRIVING, e.onOdometer(70_000, 1_000.3))
    }
}
