package com.gachiga.app.domain

import com.gachiga.app.domain.VoiceCommand.Companion.parse
import org.junit.Assert.assertEquals
import org.junit.Test

/** spec.md F8 음성 명령 */
class VoiceCommandTest {

    @Test
    fun `여행 시작과 인원`() {
        assertEquals(VoiceCommand.StartTrip(4), parse("여행 시작, 4명"))
        assertEquals(VoiceCommand.StartTrip(3), parse("여행 시작 세 명"))
        assertEquals(VoiceCommand.StartTrip(2), parse("두명이서 여행 시작"))
        assertEquals(VoiceCommand.StartTrip(5), parse("여행시작 오명"))
        assertEquals(VoiceCommand.StartTrip(null), parse("여행 시작"))
        assertEquals(VoiceCommand.StartTrip(null), parse("여행 시작 열두 명"))
    }

    @Test
    fun `일시정지 다시 시작 종료`() {
        assertEquals(VoiceCommand.Pause, parse("일시정지"))
        assertEquals(VoiceCommand.Resume, parse("다시 시작"))
        assertEquals(VoiceCommand.EndTrip, parse("여행 종료"))
        assertEquals(VoiceCommand.EndTrip, parse("여행 끝"))
    }

    @Test
    fun `비용과 금액`() {
        assertEquals(VoiceCommand.AddExpense(ExpenseCategory.TOLL, 4_800), parse("통행료 4천 8백 원"))
        assertEquals(VoiceCommand.AddExpense(ExpenseCategory.PARKING, 3_000), parse("주차비 삼천 원"))
        assertEquals(VoiceCommand.Amount(60_000), parse("6만 원"))
        assertEquals(VoiceCommand.Amount(15_000), parse("만 오천 원"))
    }

    @Test
    fun `네 아니요`() {
        assertEquals(VoiceCommand.Yes, parse("네"))
        assertEquals(VoiceCommand.Yes, parse("응"))
        assertEquals(VoiceCommand.No, parse("아니요"))
    }

    @Test
    fun `알아듣지 못한 말`() {
        assertEquals(VoiceCommand.Unknown, parse("오늘 날씨 어때"))
        assertEquals(VoiceCommand.Unknown, parse(""))
    }
}
