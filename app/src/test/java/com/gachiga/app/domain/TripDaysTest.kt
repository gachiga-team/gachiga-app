package com.gachiga.app.domain

import com.gachiga.app.domain.TripDays.HOUR_MS
import com.gachiga.app.domain.TripDays.SegmentInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripDaysTest {

    @Test fun `오래 세워 둔 뒤 출발하면 다음 일차다`() {
        assertTrue(TripDays.startsNewDay(parkedSince = 0, now = 9 * HOUR_MS, dayDistanceKm = 120.0))
        assertTrue(TripDays.startsNewDay(parkedSince = 0, now = 4 * HOUR_MS, dayDistanceKm = 120.0))
    }

    @Test fun `휴게소나 식사 같은 짧은 정차는 같은 날이다`() {
        assertFalse(TripDays.startsNewDay(parkedSince = 0, now = 2 * HOUR_MS, dayDistanceKm = 120.0))
    }

    @Test fun `출발 전에 오래 서 있었거나 정차 시각을 모르면 넘기지 않는다`() {
        assertFalse(TripDays.startsNewDay(parkedSince = 0, now = 9 * HOUR_MS, dayDistanceKm = 0.0))
        assertFalse(TripDays.startsNewDay(parkedSince = null, now = 9 * HOUR_MS, dayDistanceKm = 120.0))
    }

    @Test fun `일차별로 거리와 비용을 나눈다`() {
        val day2 = 20 * HOUR_MS
        val days = TripDays.summarize(
            segments = listOf(
                SegmentInput(1, 0, 100.0, 20.0),
                SegmentInput(1, 3 * HOUR_MS, 50.0, 10.0), // 일시정지 뒤 같은 날
                SegmentInput(2, day2, 80.0, 15.0),
            ),
            expenses = listOf(1 * HOUR_MS to 6_000, 10 * HOUR_MS to 10_000, day2 + HOUR_MS to 3_000),
            capacity = 50.0,
            unitPrice = 1_700.0,
        )
        assertEquals(2, days.size)
        // 1일차: 150km, 30% × 50L = 15L × 1,700 = 25,500원, 기타 16,000원 (밤에 낸 주차비 포함)
        assertEquals(150.0, days[0].distanceKm, 0.001)
        assertEquals(25_500, days[0].energyCostWon)
        assertEquals(16_000, days[0].otherCostWon)
        assertEquals(41_500, days[0].totalCostWon)
        // 2일차: 80km, 7.5L × 1,700 = 12,750원, 기타 3,000원
        assertEquals(day2, days[1].startedAt)
        assertEquals(12_750, days[1].energyCostWon)
        assertEquals(3_000, days[1].otherCostWon)
    }

    @Test fun `하루짜리 여행은 일차가 하나다`() {
        val days = TripDays.summarize(listOf(SegmentInput(1, 100, 10.0, 2.0)), listOf(50L to 1_000), 50.0, 1_700.0)
        assertEquals(1, days.size)
        assertEquals(1_000, days[0].otherCostWon) // 시작 전에 넣은 비용은 첫 일차로
    }
}
