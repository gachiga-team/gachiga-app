package com.gachiga.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvoyTest {
    // 영동고속도로 근처 가상의 지점. 경도 0.01도는 이 위도에서 약 880m.
    private fun car(id: String, lon: Double?, moving: Boolean? = true, energy: Int? = 80, age: Int = 0, done: Boolean = false) =
        ConvoyMember(id, "${id}호차", lat = if (lon == null) null else 37.5, lon = lon, heading = 90.0, moving = moving, energyPercent = energy, ev = true, ageSec = age, done = done)

    @Test fun `거리 계산은 하버사인으로 한다`() {
        // 서울시청 ~ 강릉역 직선 거리 약 168km
        val d = Geo.distanceMeters(37.5663, 126.9779, 37.7639, 128.8994)
        assertTrue(d in 165_000.0..172_000.0)
    }

    @Test fun `동쪽으로 달릴 때 동쪽 차량은 앞 서쪽 차량은 뒤`() {
        val me = car("1", 127.50)
        assertEquals(true, ConvoyMonitor.gap(me, car("2", 127.55))!!.ahead)
        assertEquals(false, ConvoyMonitor.gap(me, car("2", 127.45))!!.ahead)
        assertNull(ConvoyMonitor.gap(me, car("2", null)))
    }

    @Test fun `처음 본 일행은 알리지 않고 나중에 들어온 차량만 합류를 알린다`() {
        val m = ConvoyMonitor()
        assertTrue(m.update(car("1", 127.50), listOf(car("2", 127.50))).isEmpty())
        val alerts = m.update(car("1", 127.50), listOf(car("2", 127.50), car("3", 127.50)))
        assertEquals(listOf(ConvoyAlert.Joined("3호차")), alerts)
    }

    @Test fun `3km 넘게 벌어지면 한 번만 알리고 1km 안으로 오면 다시 가까워졌다고 알린다`() {
        val m = ConvoyMonitor()
        val me = car("1", 127.50)
        m.update(me, listOf(car("2", 127.50)))
        assertTrue(m.update(me, listOf(car("2", 127.48))).isEmpty()) // 약 1.8km
        val apart = m.update(me, listOf(car("2", 127.46))) // 약 3.5km 뒤
        assertEquals(1, apart.size)
        assertEquals("2호차가 4킬로미터 뒤처졌어요.", apart[0].spoken())
        assertTrue(m.update(me, listOf(car("2", 127.45))).isEmpty()) // 계속 떨어져 있어도 다시 말하지 않는다
        assertTrue(m.update(me, listOf(car("2", 127.48))).isEmpty()) // 1km 안으로 오기 전에는 조용히
        assertEquals(listOf(ConvoyAlert.Regrouped("2호차")), m.update(me, listOf(car("2", 127.495))))
    }

    @Test fun `내가 달리는 중에 일행이 서면 정차를 알린다`() {
        val m = ConvoyMonitor()
        val me = car("1", 127.50)
        m.update(me, listOf(car("2", 127.50)))
        assertEquals(listOf(ConvoyAlert.Stopped("2호차")), m.update(me, listOf(car("2", 127.50, moving = false))))
        assertTrue(m.update(me, listOf(car("2", 127.50, moving = false))).isEmpty())
    }

    @Test fun `배터리가 20퍼센트 아래로 내려가면 한 번 알린다`() {
        val m = ConvoyMonitor()
        val me = car("1", 127.50)
        m.update(me, listOf(car("2", 127.50, energy = 25)))
        val alerts = m.update(me, listOf(car("2", 127.50, energy = 19)))
        assertEquals("2호차 배터리가 19퍼센트예요.", alerts.single().spoken())
        assertTrue(m.update(me, listOf(car("2", 127.50, energy = 18))).isEmpty())
    }

    @Test fun `소식이 90초 넘게 없으면 위치를 못 받는다고 알리고 끝낸 차량은 끝났다고 알린다`() {
        val m = ConvoyMonitor()
        val me = car("1", 127.50)
        m.update(me, listOf(car("2", 127.50), car("3", 127.50)))
        val alerts = m.update(me, listOf(car("2", 127.50, age = 95), car("3", 127.50, done = true)))
        assertEquals(listOf(ConvoyAlert.Lost("2호차"), ConvoyAlert.Finished("3호차")), alerts)
    }

    @Test fun `거리는 1km 아래면 미터로 보여 준다`() {
        assertEquals("850m 뒤", ConvoyGap(850.0, false).short())
        assertEquals("2.4km 앞", ConvoyGap(2400.0, true).short())
        assertEquals("2.4km", ConvoyGap(2400.0, null).short())
    }
}
