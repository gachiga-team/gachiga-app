package com.gachiga.app.domain

import com.gachiga.app.domain.SettingsInput.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** spec.md F9 설정 입력 검사 */
class SettingsInputTest {

    @Test
    fun `전기차 용량과 단가`() {
        assertEquals(Result.Ok(77.4), SettingsInput.capacity("77.4", EnergyType.EV))
        assertEquals(Result.Ok(347), SettingsInput.price("347", EnergyType.EV))
        assertEquals(Result.Ok(1_200), SettingsInput.price("1,200원", EnergyType.EV))
    }

    @Test
    fun `내연기관 용량과 단가`() {
        assertEquals(Result.Ok(55.0), SettingsInput.capacity("55", EnergyType.FUEL))
        assertEquals(Result.Ok(1_689), SettingsInput.price("1,689", EnergyType.FUEL))
    }

    @Test
    fun `범위 밖이나 숫자가 아니면 오류`() {
        assertTrue(SettingsInput.capacity("5", EnergyType.EV) is Result.Error)
        assertTrue(SettingsInput.capacity("칠십", EnergyType.EV) is Result.Error)
        assertTrue(SettingsInput.price("100", EnergyType.FUEL) is Result.Error)   // 원/L로는 너무 싸다
        assertTrue(SettingsInput.price("", EnergyType.EV) is Result.Error)
    }
}
