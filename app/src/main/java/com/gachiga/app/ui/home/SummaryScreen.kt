package com.gachiga.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gachiga.app.domain.Settlement
import com.gachiga.app.ui.theme.CarDimens

/**
 * 여행 종료 후 정산 결과 (F5 → F6). 각 숫자 옆에 근거를 붙인다 (intent.md 원칙 2).
 * F6 정산 화면을 본격적으로 만들 때 이 화면을 키운다.
 */
@Composable
fun SummaryScreen(summary: TripSummary, onDone: () -> Unit) {
    val trip = summary.trip
    val r = summary.result
    val type = trip.energyType
    val priceBasis = when (r.unitPriceBasis) {
        Settlement.UnitPriceBasis.REFUEL_INPUT -> "${type.refuelWord()} 금액으로 계산"
        Settlement.UnitPriceBasis.DEFAULT_ESTIMATE -> "기본 단가로 추정"
    }

    Column(
        Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("여행 정산", style = MaterialTheme.typography.headlineMedium)
        Row2("이동 거리", km(r.distanceKm), "차량 측정")
        Row2("${type.label()} 사용", "%.1f %s".format(r.energyUsed, type.unit()), "차량 측정 × 설정 용량")
        Row2("단가", "%,.0f원/%s".format(r.unitPrice, type.unit()), priceBasis)
        Row2("${type.label()} 비용", won(r.energyCostWon), null)
        Row2("통행료·주차비 등", won(r.otherCostWon), "입력값")
        HorizontalDivider()
        Row2("총비용", won(r.totalCostWon), null)
        if (trip.headcount > 1) {
            Text(
                "${trip.headcount}명 · 동승자 ${trip.headcount - 1}명이 각자 운전자에게 ${won(r.perPersonWon)}",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("운전자 부담 ${won(r.driverShareWon)} (10원 미만 나머지 포함)", style = MaterialTheme.typography.bodyMedium)
        }
        r.efficiency?.let { Row2("전비·연비", "%.1f km/%s".format(it, type.unit()), null) }
        Button(onClick = onDone, modifier = Modifier.height(CarDimens.buttonHeight)) { Text("확인") }
    }
}

@Composable
private fun Row2(label: String, value: String, basis: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(260.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(260.dp))
        if (basis != null) Text(basis, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
    }
}
