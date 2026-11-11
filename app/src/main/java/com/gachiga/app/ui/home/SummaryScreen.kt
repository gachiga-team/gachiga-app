package com.gachiga.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gachiga.app.BuildConfig
import com.gachiga.app.data.trip.Refuel
import com.gachiga.app.data.trip.TripDetail
import com.gachiga.app.domain.InputMethod
import com.gachiga.app.domain.MonthlyReport
import com.gachiga.app.domain.Settlement
import com.gachiga.app.domain.SettlementShare
import com.gachiga.app.ui.theme.CarDimens
import com.gachiga.app.ui.theme.SubtleText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 여행 정산 (spec.md F6). 방금 끝낸 여행과 지난 여행 모두 이 화면으로 본다.
 * - 각 숫자 옆에 근거를 붙인다 (intent.md 원칙 2).
 * - 끝난 뒤에도 주유·충전 금액과 인원은 고칠 수 있다. 차량이 측정한 거리·사용량은 고치지 않는다.
 */
@Composable
fun SummaryScreen(
    detail: TripDetail,
    onRefuelClick: (Refuel) -> Unit,
    onHeadcountClick: () -> Unit,
    onExpenseDelete: (Long) -> Unit,
    onDone: () -> Unit,
) {
    val trip = detail.trip
    val r = detail.result
    val type = trip.energyType
    val word = type.refuelWord()
    val priceBasis = when (r.unitPriceBasis) {
        Settlement.UnitPriceBasis.REFUEL_INPUT -> "$word 금액으로 계산"
        Settlement.UnitPriceBasis.DEFAULT_ESTIMATE -> "기본 단가로 추정"
    }

    Column(
        Modifier.fillMaxSize().padding(CarDimens.screenPadding).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CarDimens.gap),
    ) {
        Text("${tripTitle(trip.destinationName)} 정산 · ${dateRange(trip.startedAt, trip.endedAt)}", style = MaterialTheme.typography.headlineMedium)

        // 1. 누가 누구에게 얼마
        if (trip.headcount > 1) {
            Text(
                "동승자 ${trip.headcount - 1}명이 각자 운전자에게 ${won(r.perPersonWon)}",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("운전자 부담 ${won(r.driverShareWon)} (10원 미만 나머지 포함)", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("혼자 한 여행 · 총비용 ${won(r.totalCostWon)}", style = MaterialTheme.typography.displayMedium)
        }
        // N5: 동승자 폰으로 넘기는 QR 정산서
        var showQr by remember { mutableStateOf(false) }
        ButtonRow {
            Button(onClick = { showQr = true }, modifier = Modifier.heightIn(min = CarDimens.touch)) {
                Text("QR 정산서", style = MaterialTheme.typography.labelLarge)
            }
            SecondaryButton("인원 고치기 (${trip.headcount}명)", onHeadcountClick)
        }
        if (showQr) {
            val sheet = shareSheet(detail, priceBasis)
            val url = SettlementShare.url(BuildConfig.SETTLE_URL, sheet)
            QrDialog(content = url ?: SettlementShare.plainText(sheet), isWebLink = url != null, onDismiss = { showQr = false })
        }
        HorizontalDivider()

        // 2. 계산 근거
        Row2("이동 거리", km(r.distanceKm), "차량 측정")
        Row2("${type.label()} 사용", "%.1f %s".format(r.energyUsed, type.unit()), "차량 측정 × 설정 용량 %.1f %s".format(trip.capacity, type.unit()))
        Row2("단가", "%,.0f원/%s".format(r.unitPrice, type.unit()), priceBasis)
        Row2("${type.label()} 비용", won(r.energyCostWon), null)
        Row2("통행료·주차비 등", won(r.otherCostWon), if (detail.expenses.any { it.input == InputMethod.NAVI }) "입력값 + 내비 예상 통행료" else "입력값")
        Row2("총비용", won(r.totalCostWon), null)
        r.efficiency?.let { Row2(if (type.unit() == "kWh") "전비" else "연비", "%.1f km/%s".format(it, type.unit()), null) }
        HorizontalDivider()

        // 3. 주유·충전 내역 (금액 고치기)
        Text("$word 내역", style = MaterialTheme.typography.headlineMedium)
        if (detail.refuels.isEmpty()) Text("없음", style = MaterialTheme.typography.bodyMedium, color = SubtleText)
        detail.refuels.forEach { refuel ->
            ButtonRow {
                Text(
                    "${time(refuel.detectedAt)} · %.0f%% → %.0f%% · ".format(refuel.percentBefore, refuel.percentAfter) +
                        (refuel.amountWon?.let { won(it) } ?: "금액 미입력"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (refuel.amountWon == null) MaterialTheme.colorScheme.secondary else Color.Unspecified,
                )
                OutlinedButton(onClick = { onRefuelClick(refuel) }, modifier = Modifier.heightIn(min = CarDimens.touch)) {
                    Text(if (refuel.amountWon == null) "금액 입력" else "고치기")
                }
            }
        }

        // 4. 기타 비용 내역
        Text("기타 비용", style = MaterialTheme.typography.headlineMedium)
        if (detail.expenses.isEmpty()) Text("없음", style = MaterialTheme.typography.bodyMedium, color = SubtleText)
        detail.expenses.forEach { e ->
            ButtonRow {
                Text("${time(e.createdAt)} · ${e.label()} ${won(e.amountWon)}", style = MaterialTheme.typography.bodyLarge)
                SecondaryButton("삭제", { onExpenseDelete(e.id) })
            }
        }

        Button(onClick = onDone, modifier = Modifier.heightIn(min = CarDimens.touch)) { Text("확인") }
    }
}

/** 지난 여행 목록 (spec.md F6). 최근 여행부터. */
@Composable
fun HistoryScreen(trips: List<TripDetail>, onItemClick: (TripDetail) -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(CarDimens.screenPadding).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CarDimens.gap),
    ) {
        ButtonRow {
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = CarDimens.touch)) { Text("뒤로") }
            Text("지난 여행", style = MaterialTheme.typography.headlineMedium)
        }
        MonthlyCard(trips)
        if (trips.isEmpty()) Text("아직 끝난 여행이 없어요.", style = MaterialTheme.typography.bodyLarge)
        trips.forEach { d ->
            val r = d.result
            OutlinedButton(onClick = { onItemClick(d) }, modifier = Modifier.heightIn(min = CarDimens.touch)) {
                Text(
                    buildString {
                        append("${tripTitle(d.trip.destinationName)} · ${dateRange(d.trip.startedAt, d.trip.endedAt)} · ${km(r.distanceKm)} · ${d.trip.headcount}명 · 총 ${won(r.totalCostWon)}")
                        if (d.trip.headcount > 1) append(" · 1인 ${won(r.perPersonWon)}")
                        if (d.unpaidRefuels > 0) append(" · 금액 미입력 ${d.unpaidRefuels}건")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/** QR 정산서에 담을 내용 (N5). 화면과 같은 숫자와 근거를 쓴다. */
private fun shareSheet(detail: TripDetail, priceBasis: String): SettlementShare.Sheet {
    val trip = detail.trip
    val r = detail.result
    return SettlementShare.Sheet(
        title = tripTitle(trip.destinationName),
        period = dateRange(trip.startedAt, trip.endedAt),
        headcount = trip.headcount,
        distanceKm = r.distanceKm,
        energyLabel = trip.energyType.label(),
        energyUsed = r.energyUsed,
        unit = trip.energyType.unit(),
        unitPrice = r.unitPrice,
        priceBasis = priceBasis,
        energyCostWon = r.energyCostWon,
        otherCostWon = r.otherCostWon,
        totalCostWon = r.totalCostWon,
        perPersonWon = r.perPersonWon,
        driverShareWon = r.driverShareWon,
        items = detail.expenses.map { it.label() to it.amountWon.toLong() },
    )
}

/** 월간 리포트 (spec.md 9절 N6). 그룹 여행이 없는 평소에도 볼 거리를 준다. */
@Composable
private fun MonthlyCard(trips: List<TripDetail>) {
    val s = MonthlyReport.of(
        trips.map {
            MonthlyReport.TripFigures(it.trip.startedAt, it.result.distanceKm, it.result.totalCostWon, it.result.energyUsed, it.trip.energyType)
        },
        System.currentTimeMillis(),
    )
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(CarDimens.gap), verticalArrangement = Arrangement.spacedBy(CarDimens.gap)) {
            Text("${s.month}월 리포트", style = MaterialTheme.typography.headlineMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CarDimens.gap * 2),
                verticalArrangement = Arrangement.spacedBy(CarDimens.gap),
            ) {
                StatTile("${s.trips}번", "여행")
                StatTile(km(s.distanceKm), "달린 거리")
                StatTile(won(s.totalCostWon), "든 비용")
                val type = s.energyType
                if (s.efficiency != null && type != null) {
                    StatTile("%.1f km/%s".format(s.efficiency, type.unit()), if (type.unit() == "kWh") "평균 전비" else "평균 연비")
                }
            }
        }
    }
}

@Composable
private fun Row2(label: String, value: String, basis: String?) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(CarDimens.gap)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(min = 260.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(min = 260.dp))
        if (basis != null) Text(basis, style = MaterialTheme.typography.bodyMedium, color = SubtleText)
    }
}

private fun dateRange(start: Long, end: Long?): String {
    val day = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)
    val hm = SimpleDateFormat("HH:mm", Locale.KOREA)
    return day.format(Date(start)) + (end?.let { " ~ " + hm.format(Date(it)) } ?: "")
}

private fun time(ms: Long): String = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(ms))
