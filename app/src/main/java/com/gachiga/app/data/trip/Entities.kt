package com.gachiga.app.data.trip

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gachiga.app.domain.EnergyType
import com.gachiga.app.domain.ExpenseCategory
import com.gachiga.app.domain.InputMethod
import com.gachiga.app.domain.RefuelSource
import com.gachiga.app.domain.TripStatus

// spec.md 4절 데이터 모델

@Entity
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val status: TripStatus,
    val headcount: Int,             // 운전자 포함 인원, 1..9
    val energyType: EnergyType,
    val capacity: Double,           // L 또는 kWh (시작 시점 설정값 복사)
    val defaultUnitPrice: Int,      // 원/L 또는 원/kWh (시작 시점 설정값 복사)
    val startedAt: Long,
    val endedAt: Long? = null,
    val startOdometerKm: Double,
    val endOdometerKm: Double? = null,
    val startEnergyPercent: Double,
    val endEnergyPercent: Double? = null,
)

/** ACTIVE 구간 하나. 일시정지하면 닫히고 재개하면 새로 열린다. */
@Entity(
    foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class Segment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val dayIndex: Int = 1,
    val startedAt: Long,
    val endedAt: Long? = null,
    val startOdometerKm: Double,
    val endOdometerKm: Double? = null,
    val energyUsedPercent: Double = 0.0, // 구간 중 에너지 % 하락 누적 (주유로 인한 상승은 제외)
)

@Entity(
    foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class Refuel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val detectedAt: Long,
    val percentBefore: Double,
    val percentAfter: Double,
    val amountWon: Int? = null,      // null이면 미입력
    val source: RefuelSource,
)

@Entity(
    foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val createdAt: Long,
    val category: ExpenseCategory,
    val amountWon: Int,
    val input: InputMethod,
)
