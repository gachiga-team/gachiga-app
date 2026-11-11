package com.gachiga.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** spec.md F2 사용량 누적, F3 주유·충전 감지 */
class EnergyTrackerTest {

    private fun parked() = EnergyTracker().also { it.onMotion(MotionState.PARKED) }
    private fun driving() = EnergyTracker().also { it.onMotion(MotionState.DRIVING) }

    @Test
    fun `내려간 만큼 사용량으로 센다`() {
        val t = driving()
        assertEquals(0.0, t.onEnergy(0, 50.0), 1e-9)
        assertEquals(2.0, t.onEnergy(1, 48.0), 1e-9)
        assertEquals(3.0, t.onEnergy(2, 45.0), 1e-9)
    }

    @Test
    fun `주행 중 흔들림은 사용량으로 세지 않는다`() {
        val t = driving()
        t.onEnergy(0, 50.0)
        assertEquals(0.0, t.onEnergy(1, 51.0), 1e-9)
        assertEquals(0.0, t.onEnergy(2, 50.0), 1e-9)
    }

    @Test
    fun `정차 중 30에서 80으로 오르면 30초 뒤 1건만 감지한다`() {
        val t = parked()
        t.onEnergy(0, 30.0)
        t.onEnergy(1_000, 40.0)
        t.onEnergy(2_000, 60.0)
        t.onEnergy(3_000, 80.0)
        assertNull(t.tick(20_000))                      // 아직 30초 안 지남
        assertEquals(RefuelEvent(30.0, 80.0), t.tick(33_000))
        assertNull(t.tick(70_000))                      // 두 번 감지하지 않는다
    }

    @Test
    fun `3퍼센트포인트 미만 상승은 주유로 보지 않는다`() {
        val t = parked()
        t.onEnergy(0, 50.0)
        t.onEnergy(1_000, 52.0)
        assertNull(t.tick(40_000))
    }

    @Test
    fun `오르는 도중 출발하면 그 자리에서 확정한다`() {
        val t = parked()
        t.onEnergy(0, 20.0)
        t.onEnergy(1_000, 70.0)
        assertEquals(RefuelEvent(20.0, 70.0), t.onMotion(MotionState.DRIVING))
    }

    @Test
    fun `주유 뒤에는 새 값부터 사용량을 센다`() {
        val t = parked()
        t.onEnergy(0, 30.0)
        t.onEnergy(1_000, 80.0)
        t.tick(40_000)
        t.onMotion(MotionState.DRIVING)
        assertEquals(5.0, t.onEnergy(50_000, 75.0), 1e-9)
    }

    @Test
    fun `주행 중 상승은 주유로 감지하지 않는다`() {
        val t = driving()
        t.onEnergy(0, 30.0)
        t.onEnergy(1_000, 80.0)
        assertNull(t.tick(60_000))
    }
}
