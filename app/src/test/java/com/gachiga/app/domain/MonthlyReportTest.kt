package com.gachiga.app.domain

import com.gachiga.app.domain.MonthlyReport.TripFigures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** spec.md 9절 N6 */
class MonthlyReportTest {

    private val kst = TimeZone.getTimeZone("Asia/Seoul")
    private fun at(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(kst).apply { clear(); set(year, month - 1, day, 12, 0) }.timeInMillis

    @Test
    fun `이번 달 여행만 합친다`() {
        val trips = listOf(
            TripFigures(at(2026, 11, 2), 100.0, 10_000, 15.0, EnergyType.EV),
            TripFigures(at(2026, 11, 9), 200.0, 25_000, 35.0, EnergyType.EV),
            TripFigures(at(2026, 10, 30), 500.0, 90_000, 80.0, EnergyType.EV), // 지난달
        )
        val s = MonthlyReport.of(trips, at(2026, 11, 11), kst)

        assertEquals(11, s.month)
        assertEquals(2, s.trips)
        assertEquals(300.0, s.distanceKm, 1e-9)
        assertEquals(35_000L, s.totalCostWon)
        assertEquals(6.0, s.efficiency!!, 1e-9)    // 300km / 50kWh
        assertEquals(EnergyType.EV, s.energyType)
    }

    @Test
    fun `여행이 없으면 0과 null`() {
        val s = MonthlyReport.of(emptyList(), at(2026, 11, 11), kst)
        assertEquals(0, s.trips)
        assertNull(s.efficiency)
    }
}
