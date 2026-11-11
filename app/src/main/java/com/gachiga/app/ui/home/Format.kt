package com.gachiga.app.ui.home

import com.gachiga.app.domain.EnergyType
import com.gachiga.app.domain.ExpenseCategory

fun won(amount: Long): String = "%,d원".format(amount)
fun won(amount: Int): String = won(amount.toLong())
fun km(value: Double): String = "%,.1f km".format(value)

fun EnergyType.label(): String = if (this == EnergyType.EV) "배터리" else "연료"
fun EnergyType.unit(): String = if (this == EnergyType.EV) "kWh" else "L"
fun EnergyType.refuelWord(): String = if (this == EnergyType.EV) "충전" else "주유"

fun ExpenseCategory.label(): String = when (this) {
    ExpenseCategory.TOLL -> "통행료"
    ExpenseCategory.PARKING -> "주차비"
    ExpenseCategory.OTHER -> "기타"
}
