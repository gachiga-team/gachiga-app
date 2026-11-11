package com.gachiga.app.domain

/** 연료 종류. 내연기관은 Fuel %, 전기차는 EvBattery SoC %를 '에너지 %'로 쓴다 (intent.md 7절). */
enum class EnergyType { FUEL, EV }

enum class TripStatus { ACTIVE, PAUSED, ENDED }

/** UNKNOWN은 안전을 위해 DRIVING처럼 다룬다 (spec.md 3.2). */
enum class MotionState {
    DRIVING, PARKED, UNKNOWN;

    val locksInput: Boolean get() = this != PARKED
}

enum class RefuelSource { DETECTED, MANUAL }

enum class ExpenseCategory { TOLL, PARKING, OTHER }

/** NAVI: 내비 경로의 예상 통행료를 자동으로 넣은 것 (N1). */
enum class InputMethod { VOICE, TOUCH, NAVI }
