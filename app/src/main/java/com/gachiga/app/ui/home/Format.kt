package com.gachiga.app.ui.home

import com.gachiga.app.data.trip.Expense
import com.gachiga.app.domain.EnergyType
import com.gachiga.app.domain.ExpenseCategory
import com.gachiga.app.domain.InputMethod
import com.gachiga.app.domain.KoreanJosa

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

/** 비용 한 건의 이름. 내비 예상 통행료는 근거를 함께 붙인다 (intent.md 원칙 2). */
fun Expense.label(): String =
    if (input == InputMethod.NAVI) "${category.label()} (내비 예상${note?.let { ", $it" } ?: ""})" else category.label()

/** 여행 이름: 목적지가 있으면 "강릉역 여행", 없으면 "여행". */
fun tripTitle(destination: String?): String = destination?.takeIf { it.isNotBlank() }?.let { "$it 여행" } ?: "여행"

/** 주행 중 머리말: 목적지가 있으면 "강릉역으로 가는 중", 없으면 "여행 중". */
fun goingTo(destination: String?): String =
    destination?.takeIf { it.isNotBlank() }?.let { "${KoreanJosa.withEuro(it)} 가는 중" } ?: "여행 중"
