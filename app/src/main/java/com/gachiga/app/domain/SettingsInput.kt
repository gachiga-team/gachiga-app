package com.gachiga.app.domain

/**
 * 차량 설정 입력 검사 (spec.md F9). 순수 Kotlin.
 * 범위를 벗어나면 정산이 크게 틀어지므로 저장하지 않고 이유를 보여 준다.
 */
object SettingsInput {

    data class Limits(val capacity: ClosedFloatingPointRange<Double>, val price: IntRange)

    fun limits(type: EnergyType): Limits = when (type) {
        EnergyType.EV -> Limits(capacity = 10.0..200.0, price = 50..2_000)      // kWh, 원/kWh
        EnergyType.FUEL -> Limits(capacity = 20.0..150.0, price = 500..5_000)   // L, 원/L
    }

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        data class Error(val message: String) : Result<Nothing>
    }

    fun capacity(text: String, type: EnergyType): Result<Double> {
        val unit = if (type == EnergyType.EV) "kWh" else "L"
        val v = text.trim().replace(",", "").toDoubleOrNull() ?: return Result.Error("용량을 숫자로 입력해 주세요.")
        val range = limits(type).capacity
        if (v !in range) return Result.Error("용량은 %.0f~%.0f %s 사이로 입력해 주세요.".format(range.start, range.endInclusive, unit))
        return Result.Ok(v)
    }

    fun price(text: String, type: EnergyType): Result<Int> {
        val unit = if (type == EnergyType.EV) "kWh" else "L"
        val v = text.trim().replace(",", "").replace("원", "").toIntOrNull() ?: return Result.Error("단가를 숫자로 입력해 주세요.")
        val range = limits(type).price
        if (v !in range) return Result.Error("단가는 %,d~%,d원/%s 사이로 입력해 주세요.".format(range.first, range.last, unit))
        return Result.Ok(v)
    }
}
