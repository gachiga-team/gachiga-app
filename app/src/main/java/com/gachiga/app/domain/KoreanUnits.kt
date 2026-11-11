package com.gachiga.app.domain

import kotlin.math.roundToInt

/** 단위 변환. 순수 Kotlin. */
object KoreanUnits {

    /**
     * 내비 도착 예상 잔량(Wh)을 배터리 %로 바꾼다 (spec.md 9절 N3).
     * 용량을 모르거나 값이 이상하면 null.
     */
    fun whToPercent(wh: Int, capacityKWh: Double): Int? {
        if (capacityKWh <= 0 || wh < 0) return null
        return (wh / (capacityKWh * 1000.0) * 100.0).roundToInt().coerceIn(0, 100)
    }
}
